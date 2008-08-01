package net.sf.gratia.services;

import net.sf.gratia.util.XP;

import net.sf.gratia.util.Configuration;

import net.sf.gratia.util.Logging;

import java.util.ArrayList;

import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Hashtable;

import java.text.*;

import java.io.*;

import net.sf.gratia.storage.*;


import org.hibernate.*;
import org.hibernate.exception.ConstraintViolationException;

public class ListenerThread extends Thread
{
    static String replication = "replication";

    String ident = null;
    String directory = null;         // Location of the incoming messages.
    Hashtable global;
    long ninput = 0;                 // Number of input messages processed
    long nrecords = 0;                // Number of records processed;
    String directory_part = null;    // stemp for history and old subdirectory
    long recordsPerDirectory = 10000; // Maximum number of records per directory.
    CollectorService collectorService;

    //
    // database parameters
    //

    org.hibernate.Session session;
    Transaction tx;
    RecordUpdaterManager updater = new RecordUpdaterManager();
    RecordConverter converter = new RecordConverter();

    int itotal = 0;
    boolean duplicateCheck = false;
    Properties p;

    XP xp = new XP();

    StatusUpdater statusUpdater = null;
    NewVOUpdate newVOUpdate = null;
    ErrorRecorder errorRecorder = new ErrorRecorder();

    Object lock;

    String historypath = "";

    //
    // various things used in the update loop
    //

    boolean stopflag = false;

    public ListenerThread(String ident,
                          String directory,
                          Object lock,
                          Hashtable global,
                          CollectorService collectorService)
    {      
        this.ident = ident;
        this.directory = directory;
        this.lock = lock;
        this.global = global;
        this.collectorService = collectorService;

        File tmp = new File(directory);
        this.directory_part = tmp.getName();

        loadProperties();
        try
            {
                String url = p.getProperty("service.jms.url");
                Logging.log("");
                Logging.log(ident + ": " + directory + ": Started");
                Logging.log("");
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }
        historypath = System.getProperties().getProperty("catalina.home") + "/gratia/data/";

        JobUsageRecordUpdater.AddDefaults(updater);
    }

    public void loadProperties()
    {
        p = Configuration.getProperties();
        String temp = p.getProperty("service.duplicate.check");
        if (temp.equals("1"))
            duplicateCheck = true;
        else
            duplicateCheck = false;
        Logging.log(ident + ": Duplicate Check: " + duplicateCheck);

        try 
            {
                long max_record = Long.parseLong(p.getProperty("maintain.recordsPerDirectory"));
                recordsPerDirectory = max_record;
            }
        catch (Exception e) 
            {
                // Only issue a warning here
                Logging.log(ident + ": Failed to parse property maintain.recordsPerDirectory");
            }            
    }

    public void stopRequest()
    {
        stopflag = true;
        Logging.log(ident + ": Stop Requested");
    }

    public void run()
    {
//         net.sf.gratia.storage.DataScrubber scrub = new net.sf.gratia.storage.DataScrubber();

        while (true)
            {
                if (stopflag)
                    {
                        Logging.log(ident + ": Exiting");
                        return;
                    }

                if (!HibernateWrapper.databaseUp())
                    {
                        HibernateWrapper.start();
                        if (HibernateWrapper.databaseDown)
                            {
                                Logging.log(ident + ": Hibernate Down: Sleeping");
                                try
                                    {
                                        Thread.sleep(30 * 1000);
                                    }
                                catch (Exception ignore)
                                    {
                                    }
                                continue;
                            }
                    }
                if (stopflag)
                    {
                        Logging.log(ident + ": Exiting");
                        return;
                    }
                int nfiles = loop();
//                 scrub.Duplicate();
//                 scrub.JobUsageRawXml();
//                 scrub.IndividualJobUsageRecords();
//                 scrub.MetricRawXml();
//                 scrub.IndividualMetricRecords();
                if (stopflag)
                    {
                        Logging.log(ident + ": Exiting");
                        return;
                    }
                if (nfiles==0) {
                    // Sleep only if there is no file waiting.
                    try
                        {
                            Thread.sleep(30 * 1000);
                        }
                    catch (Exception ignore)
                        {
                        }
                }
            }
    }

    public int loop()
    {
        String file = "";
        String blob = "";

        String xml = "";
        ArrayList rawxmllist = new ArrayList();
        ArrayList extraxmllist = new ArrayList();
        ArrayList md5list = new ArrayList();
        ArrayList historydatelist = new ArrayList();

        boolean gotreplication = false;
        boolean gothistory = false;

        // Return the number of files seen.
        // or 0 in the case of error.

        if (!HibernateWrapper.databaseUp())
            return 0;

        String files[] = xp.getFileList(directory);

        int nfiles = files.length;

        if (nfiles == 0)
            return 0;

        statusUpdater = new StatusUpdater();
        newVOUpdate = new NewVOUpdate();

        for (int i = 0; i < files.length; i++)
            {
                global.put("listener", new java.util.Date());

                if (stopflag)
                    {
                        Logging.log(ident + ": Exiting");
                        return nfiles;
                    }

                file = files[i];
                blob = xp.get(files[i]);

                xml = "";
                rawxmllist.clear();
                extraxmllist.clear();
                md5list.clear();
                historydatelist.clear();

                gotreplication = gothistory = false;

                ninput = ninput + 1;

                try
                    {
                        saveIncoming(blob);
                    }
                catch (Exception e) 
                    {
                        Logging.log(ident + ": loop failed to backup incoming message. \nError: "+e.getMessage()+"\n");
                    }

                Record current = null;

                //
                // see if trace requested
                //

                if (p.getProperty("service.datapump.trace").equals("1"))
                    {
                        Logging.log(ident + ": XML Trace:" + "\n\n" + blob + "\n\n");
                    }

                //
                // see if we got a normal update or a replicated one
                //

                try
                    {
                        int nseen = 0;
                        if (blob.startsWith(replication))
                            {
                                StringTokenizer st = new StringTokenizer(blob, "|");

                                gotreplication = true;

                                while(st.hasMoreTokens()) {
                                    String val = st.nextToken();
                                    if (val.equals(replication) && st.hasMoreTokens()) {
                                        val = st.nextToken();
                                    }                 
                                    xml = xml.concat(val);
                                    if (st.hasMoreTokens()) {
                                        rawxmllist.add(st.nextToken());
                                    } else {
                                        rawxmllist.add(null);
                                    }
                                    if (st.hasMoreTokens()) {
                                        // The extraxml can be directly followed by the word 'replication'
                                        String extraxml = st.nextToken();
                                        if (extraxml.endsWith(replication)) {
                                            extraxml = extraxml.substring(0,extraxml.length()-replication.length());
                                        }
                                        extraxmllist.add(extraxml);
                                    } else {
                                        extraxmllist.add(null);
                                    }
                                    nseen = nseen + 1;
                                }
                            }
                                
                        else if (blob.startsWith("history"))
                            {
                                gothistory = true;
                                StringTokenizer st = new StringTokenizer(blob, "|");
                                while(st.hasMoreTokens()) {
                                    st.nextToken();
                                    if (st.hasMoreTokens()) {
                                        historydatelist.add(st.nextToken());
                                    } else {
                                        historydatelist.add(null);
                                    }
                                    if (st.hasMoreTokens()) {
                                        xml = xml.concat(st.nextToken());
                                    }
                                    if (st.hasMoreTokens()) {
                                        rawxmllist.add(st.nextToken());
                                    } else {
                                        rawxmllist.add(null);
                                    }
                                    if (st.hasMoreTokens()) {
                                        extraxmllist.add(st.nextToken());
                                    } else {
                                        extraxmllist.add(null);
                                    }
                                    nseen = nseen + 1;
                                }

                            }
                        else if (blob.startsWith("historymd5"))
                            {
                                gothistory = true;
                                StringTokenizer st = new StringTokenizer(blob, "|");
                                while (st.hasMoreTokens()) {
                                    st.nextToken();
                                    if (st.hasMoreTokens()) {
                                        historydatelist.add(st.nextToken());
                                    } else {
                                        historydatelist.add(null);
                                    }
                                    if (st.hasMoreTokens()) {
                                        xml = xml.concat(st.nextToken());
                                    }
                                    if (st.hasMoreTokens()) {
                                        md5list.add(st.nextToken());
                                    } else {
                                        md5list.add(null);
                                    }
                                    nseen = nseen + 1;
                                }
                            }
                        else {
                            xml = blob;
                            nseen = nseen + 1;
                        }
                        if (nseen>1) {
                            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><RecordEnvelope>" + xml + "</RecordEnvelope>";
                        }
                    }
                catch (Exception e)
                    {
                        Logging.log(ident + ": Error:Processing File: " + file);
                        Logging.log(ident + ": Blob: " + blob);
                        try
                            {
                                File temp = new File(file);
                                temp.delete();
                            }
                        catch (Exception ignore)
                            {
                            }
                        continue;
                    }

                if (xml == null)
                    {
                        Logging.log(ident + ": Error:No Data To Process: " + file);
                        try
                            {
                                File temp = new File(file);
                                temp.delete();
                            }
                        catch (Exception ignore)
                            {
                            }
                        continue;
                    }

                Logging.log(ident + ": Processing: " + file);

                ArrayList records = new ArrayList();

                try {
                    records = convert(xml);
                }
                catch (Exception e) {
                    try {
                        if (gotreplication)
                            errorRecorder.saveParse("Replication", "Parse", xml);
                        else if (gothistory)
                            errorRecorder.saveParse("History", "Parse", xml);
                        else
                            errorRecorder.saveParse("Probe", "Parse", xml);
                    }
                    catch (Exception ignore) { }
                }

                for (int j = 0; j < records.size(); j++) {
                    // Logging.log(ident + ": Before Begin Transaction");
                    session = HibernateWrapper.getSession();
                    tx = session.beginTransaction();
                    try {
                        // Logging.log(ident + ": After Begin Transaction");

                        current = (Record)records.get(j);

                        Probe probe = statusUpdater.update(session, current, xml);
                        current.setProbe(probe);

                        Boolean acceptRecord = true;
                        if (!collectorService.housekeepingServiceDisabled()) {
                            Date date = current.getDate();
                            Date expirationDate = current.getExpirationDate();
                            if ( date.before(expirationDate) ) {
                                acceptRecord = false;
                                if (gotreplication) {
                                    Logging.info(ident + ": Rejected record because its 'data' is too old ("+current.getDate()+" < "+expirationDate+")");
                                    errorRecorder.saveDuplicate("Replication","ExpirationDate",0,current);
                                } else if (gothistory) {
                                    Logging.info(ident + ": Ignored history record because its 'data' is too old ("+current.getDate()+" < "+expirationDate+")");                                
                                } else {
                                    Logging.info(ident + ": Rejected record because its 'data' is too old ("+current.getDate()+" < "+expirationDate+")");
                                    errorRecorder.saveDuplicate("Probe","ExpirationDate",0,current);
                                }

                                session.flush();
                                tx.commit();
                                session.close();

                            }
                        }
                        if (acceptRecord) {
                            // This is a recent record, let's process it

                            updater.Update(current);

                            if ((!gothistory) || (!(md5list.size()>j)) || md5list.get(j) == null) {
                                String md5key = current.computemd5();
                                current.setmd5(md5key);
                                if (current.getTableName()
                                    .equals("JobUsageRecord")) {
                                    // Need to do this to keep number of
                                    // duplicates making it into the DB
                                    // under control during the upgrade
                                    // procedure. This will be removed for a
                                    // future upgrade as it is only really
                                    // necessary for very large DBs.
                                    Logging.debug("Calculating and saving " +
                                                  "old-style checksum for " +
                                                  "JobUsageRecord");
                                    JobUsageRecord jRecord = (JobUsageRecord) current;
                                    String oldMd5 = jRecord.computeOldMd5();
                                    jRecord.setoldMd5(oldMd5);
                                }
                            } else {
                                current.setmd5((String)md5list.get(j));
                            }
                            current.setDuplicate(false);

                            synchronized (lock)
                                {
                                    newVOUpdate.check(current, session);
                                }
                            synchronized (lock)
                                {
                                    current.AttachContent(session);
                                }

                            String incomingxml = current.getRawXml();
                            String rawxml = null;
                            String extraxml = null;
                            if (rawxmllist.size()>j) {
                                rawxml = (String)rawxmllist.get(j);
                                if (rawxml != null)
                                    current.setRawXml(rawxml);
                            }
                            Logging.log(ident + ": Before Hibernate Save");
                            if (gothistory) {
                                Date serverDate = new Date(Long.parseLong((String)historydatelist.get(j)));
                                current.setServerDate(serverDate);
                            }
                            session.save(current);
                            //
                            // now - save history
                            //
                            if (!gothistory) {
                                saveHistory(current,incomingxml,rawxml,extraxml,gotreplication);
                            }
                            // Logging.log(ident + ": After Hibernate Save");
                            // Logging.log(ident + ": Before Transaction Commit");
                            session.flush();
                            tx.commit();
                            session.close();
                            // Logging.log(ident + ": After Transaction Commit");
                            nrecords = nrecords + 1;
                        }
                    }
                    catch (ConstraintViolationException e) {
                        tx.rollback();
                        session.close();
                        int dupdbid = 0;
                        Boolean needCurrentSaveDup = false;
                        if (e.getSQLException().getMessage().matches(".*\\b[Dd]uplicate\\b.*")) {
                            if (current.getTableName().equals("JobUsageRecord")) {
                                UserIdentity newUserIdentity = ((JobUsageRecord) current).getUserIdentity();
                                session = HibernateWrapper.getSession();
                                Query q =
                                    session.createQuery("select record from " +
                                                        "JobUsageRecord " +
                                                        "record where " +
                                                        "record.md5 = " +
                                                        "'" +
                                                        current.getmd5() +
                                                        "'")
                                    .setCacheMode(CacheMode.IGNORE);
                                ScrollableResults dups = q.scroll(ScrollMode.FORWARD_ONLY);
                                Boolean savedCurrent = false;
                                try {
                                    if (dups.next()) {
                                        tx = session.beginTransaction();
                                        JobUsageRecord original_record = (JobUsageRecord) dups.get(0);
                                        dupdbid = original_record.getRecordId();
                                        UserIdentity originalUserIdentity = original_record.getUserIdentity();
                                        if (newUserIdentity == null) continue; // No replacement
                                        Boolean newerIsBetter = false;
                                        String replaceReason = null;
                                        if (originalUserIdentity == null) {
                                            newerIsBetter = true;
                                            replaceReason = "original UserIdentity block is null";
                                        } else if ((newUserIdentity.getVOName() != null) &&
                                                   (newUserIdentity.getVOName().length() != 0) &&
                                                   (!newUserIdentity.getVOName().equalsIgnoreCase("Unknown"))) {
                                            // Have something with which to replace it.
                                            replaceReason = "New VOName is better";
                                            if (originalUserIdentity.getVOName() == null) {
                                                newerIsBetter = true;
                                                replaceReason += " -- original VOName is null";
                                            } else if (originalUserIdentity.getVOName().length() == 0) {
                                                newerIsBetter = true;
                                                replaceReason += " -- original VOName is empty";
                                            } else if (originalUserIdentity.getVOName().equalsIgnoreCase("Unknown")) {
                                                newerIsBetter = true;
                                                replaceReason += " -- original VOName is \"Unknown\")";
                                            } else if ((newUserIdentity.getVOName().startsWith("/")) &&
                                                       (!originalUserIdentity.getVOName().startsWith("/"))) {
                                                newerIsBetter = true;
                                                replaceReason += " -- original VOName is not fully qualified";
                                            }
                                        }
                                        if (!newerIsBetter) { // Still haven't decided
                                            if ((newUserIdentity.getCommonName() != null) &&
                                                (!newUserIdentity.getCommonName().startsWith("Generic"))) {
                                                replaceReason = "New CommonName is better";
                                                if (originalUserIdentity.getCommonName() == null) {
                                                    newerIsBetter = true;
                                                    replaceReason += " -- original CommonName is null";
                                                } else if (originalUserIdentity.getCommonName().length() == 0) {
                                                    newerIsBetter = true;
                                                    replaceReason += " -- original CommonName is empty";
                                                } else if (originalUserIdentity.getCommonName().startsWith("Generic")) {
                                                    newerIsBetter = true;
                                                    replaceReason += " -- original CommonName is generic";
                                                }
                                            } else if ((newUserIdentity.getKeyInfo() != null) &&
                                                       (originalUserIdentity.getKeyInfo() == null)) { 
                                                newerIsBetter = true;
                                                replaceReason = "Original KeyInfo is null";
                                            }
                                        }
                                        if (newerIsBetter) {
                                            // Keep the new one and ditch the old
                                            Logging.info(ident + ": Replacing record " +
                                                         dupdbid + " with \"better\" record (" +
                                                         replaceReason + ").");
                                            SummaryUpdater.removeFromSummary(original_record.getRecordId(),
                                                                             session);
                                            // Delete the record.
                                            String originalXml = original_record.asXML();
                                            String originalTableName = original_record.getTableName();
                                            session.delete(original_record);
                                            if (!savedCurrent) {
                                                // If we haven't saved
                                                // the current record
                                                // yet, flush and commit
                                                // the delete
                                                // (important) and then
                                                // save the current record. 
                                                session.flush();
                                                tx.commit();
                                                tx = session.beginTransaction();
                                                session.save(current);
                                                savedCurrent = true;
                                            }
                                            if (original_record.setDuplicate(true)) {
                                                if (gotreplication) {
                                                    errorRecorder.saveDuplicate("Replication", "Duplicate",
                                                                                current.getRecordId(),
                                                                                originalXml,
                                                                                originalTableName);
                                                } else if (gothistory) {
                                                    ;
                                                } else {
                                                    errorRecorder.saveDuplicate("Probe", "Duplicate",
                                                                                current.getRecordId(),
                                                                                originalXml,
                                                                                originalTableName);
                                                }
                                            }
                                        }
                                        session.flush();
                                        tx.commit();
                                    }
                                    if (!savedCurrent) {
                                        needCurrentSaveDup = current.setDuplicate(true);
                                    }
                                    session.close();
                                }
                                catch (Exception e2) {
                                    tx.rollback();
                                    session.close();
                                    Logging.warning(ident +
                                                    ": caught exception resolving duplicates for record with md5 checksum" +
                                                    current.getmd5() +
                                                    " -- all duplicates of same will remain in DB", e2);
                                }
                            } else {
                                needCurrentSaveDup = current.setDuplicate(true);
                            }
                            if (needCurrentSaveDup) {
                                //Logging.log(ident + ": Before Save Duplicate");
                                try {
                                    if (gotreplication) {
                                        Logging.debug(ident +
                                                      ": save duplicate of record " +
                                                      dupdbid);
                                        errorRecorder.saveDuplicate("Replication", "Duplicate", dupdbid, current);
                                    } else if (gothistory) {
                                        // If we are reprocessing the history date, we should not
                                        // be recording the possible duplicates.
                                        Logging.debug(ident +
                                                      ": ignore duplicate of record " +
                                                      dupdbid + " (history replay)");
                                        ;
                                    } else {
                                        errorRecorder.saveDuplicate("Probe", "Duplicate", dupdbid, current);
                                        Logging.debug(ident +
                                                      ": save duplicate of record " +
                                                      dupdbid);
                                    }
                                    //Logging.log(ident + ": After Save Duplicate");
                                }
                                catch (Exception ignore) { }
                            }
                        } else { // Constraint exception, but not a duplicate: oops!
                            Logging.debug(ident + ": received constraint violation " +
                                          e.getSQLException().getMessage());
                            if (HibernateWrapper.databaseUp()) {
                                try {
                                    if (gotreplication) {
                                        errorRecorder.saveSQL("Replication", "SQLError", current);
                                    } else {
                                        errorRecorder.saveSQL("Probe", "SQLError", current);
                                    }
                                }
                                catch (Exception ignore) { }
                            } else {
                                Logging.log(ident + ": Communications Error:Shutting Down");
                                return 0; 
                            }
                            Logging.warning(ident + ": Error In Process: ",e);
                            Logging.warning(ident + ": Current: " + current);
                        }
                    }
                    catch (Exception e) {
                        // Must close session!
                        tx.rollback();
                        session.close();
                        if (HibernateWrapper.databaseUp()) {
                            try {
                                if (gotreplication) {
                                    errorRecorder.saveSQL("Replication", "SQLError", current);
                                } else {
                                    errorRecorder.saveSQL("Probe", "SQLError", current);
                                }
                            }
                            catch (Exception ignore) { }
                        } else {
                            Logging.log(ident + ": Communications Error:Shutting Down");
                            return 0; 
                        }
                        Logging.warning(ident + ": Error In Process: ",e);
                        Logging.warning(ident + ": Current: " + current);
                    } // End general catch
                } // End of for each record loop
                // Logging.log(ident + ": Before File Delete: " + file);
                try {
                    File temp = new File(file);
                    temp.delete();
                }
                catch (Exception ignore) {

                    // Logging.log(ident + ": File Delete Failed: " + file + " Error: " + ignore);
                }
                // Logging.log(ident + ": After File Delete: " + file);
                itotal++;
                Logging.log(ident + ": Total Input Messages: " + itotal);
                Logging.log(ident + ": Total Records: " + nrecords);
                
            }
        return nfiles; 
    }

    public File getDirectory(String what) 
    {
        Date now = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddkk");
        String path = historypath + what + "-" + format.format(now);
        File directory = new File(path);
        if (!directory.exists())
            {
                directory.mkdir();
            }
        
        long part = nrecords / recordsPerDirectory;

        File subdir = new File(directory,directory_part + "-" + part);
        
        if (!subdir.exists())
            {
                subdir.mkdir();
            }
        return subdir;
    }


    public void saveIncoming(String data) throws java.io.IOException
    {
        File where = getDirectory("old");
        File errorfile = File.createTempFile("old-", ".xml", where);
        String filename = errorfile.getPath();
        xp.save(filename, data);
    }


    public void saveHistory(Record current, String xml, String rawxml, String extraxml, boolean gotreplication) throws java.io.IOException
    {
        Date serverDate = current.getServerDate();
        File where = getDirectory("history");

        File historyfile = File.createTempFile("history-", ".xml", where);
        String filename = historyfile.getPath();

        StringBuffer data;
        if (gotreplication) {
            data = new StringBuffer("history" + "|" + serverDate.getTime() + "|" + xml + "|" + rawxml);
            if (extraxml != null) {
                data.append("|" + extraxml);
            }
        } else {
            data = new StringBuffer("historymd5" + "|" + serverDate.getTime() + "|" + xml + "|" + current.getmd5());
        }
        xp.save(filename,data.toString());
    }
    
    public ArrayList convert(String xml) throws Exception
    {
        ArrayList records = null;

        try {
            records = converter.convert(xml);
        }
        catch (Exception e)
            {
                Logging.log(ident + ": Parse error:  " + e.getMessage());
                Logging.log(ident + ": XML:  " + "\n" + xml);
                throw e;
            }

        // The usage records array list is now populated with all the job usage records found in the given XML file
        //  return it to the caller.
        return records;
    }

}
