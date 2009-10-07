package net.sf.gratia.services;

import net.sf.gratia.util.XP;

import net.sf.gratia.util.Configuration;

import net.sf.gratia.util.Logging;

import java.util.ArrayList;

import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Hashtable;

import java.text.*;

import java.io.*;

import net.sf.gratia.storage.*;

import org.apache.commons.lang.exception.ExceptionUtils;

import org.hibernate.*;
import org.hibernate.exception.ConstraintViolationException;

public class ListenerThread extends Thread {

    final static String replicationMarker = "replication";
    final static String originMarker = "Origin";
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
    RecordUpdaterManager updater = new RecordUpdaterManager();
    RecordConverter converter = new RecordConverter();
    int itotal = 0;
    Properties p;
    StatusUpdater statusUpdater = null;
    NewVOUpdate newVOUpdate = null;
    NewClusterUpdate newClusterUpdate = null;
    ErrorRecorder errorRecorder = new ErrorRecorder();
    final Object lock;
    String historypath = "";
    Hashtable<String,Integer> fProbeDetails = new Hashtable<String,Integer>();
    Hashtable<String,Integer> fSubcluster = new Hashtable<String,Integer>();
    Hashtable<String,Integer> fComputeElement = new Hashtable<String,Integer>();
    Hashtable<String,Integer> fStorageElement = new Hashtable<String,Integer>();

    //
    // various things used in the update loop
    //
    boolean stopflag = false;

    public ListenerThread(String ident,
                          String directory,
                          Object lock,
                          Hashtable global,
                          CollectorService collectorService) {
        this.ident = ident;
        this.directory = directory;
        this.lock = lock;
        this.global = global;
        this.collectorService = collectorService;

        File tmp = new File(directory);
        this.directory_part = tmp.getName();

        loadProperties();
        try {
            String url = p.getProperty("service.jms.url");
            Logging.info(ident + ": " + directory + ": Started");
        } catch (Exception e) {
            Logging.warning(ident + ": ERROR! Serious problems starting listener");
            Logging.debug(ident + "Exception detail: ", e);
        }
        historypath = System.getProperties().getProperty("catalina.home");
        if (historypath == null) {
            historypath = ".";
        }
        historypath = historypath + "/gratia/data/";

        JobUsageRecordUpdater.AddDefaults(updater);
    }

    private Boolean detectAndReportLockFailure(Exception e, Integer nTries) {
        if (e instanceof org.hibernate.exception.LockAcquisitionException) {
            if (nTries == 1) {
                Logging.info(ident + ": lock acquisition exception.  Trying a second time.");
            } else if (nTries < 5) {
                Logging.warning(ident + ": multiple contiguous lock acquisition errors: keep trying.");
            } else if (nTries == 5) {
                Logging.warning(ident + ": multiple contiguous lock acquisition errors: keep trying (warnings throttled).");
            } else if ( (nTries % 100) == 0) {
                Logging.warning(ident + ": hit " + nTries + " contiguous lock acqusition errors: check DB.");
            }
            return true;
        } else {
            return false;
        }
    }

    public void loadProperties() {
        p = Configuration.getProperties();

        try {
            long max_record =
                Long.parseLong(p.getProperty("maintain.recordsPerDirectory"));
            recordsPerDirectory = max_record;
        } catch (Exception e) {
            // Only issue a warning here
            Logging.warning(ident +
                            ": Failed to parse property " +
                            "maintain.recordsPerDirectory");
        }
    }

    public void stopRequest() {
        stopflag = true;
        Logging.log(ident + ": Stop Requested");
    }

    @Override
        public void run() {
        while (true) {
            if (stopflag) {
                Logging.info(ident + ": Exiting");
                return;
            }

            if (!HibernateWrapper.databaseUp()) {
                try {
                    HibernateWrapper.start();
                } catch (Exception e) { // Ignore
                }
                if (HibernateWrapper.databaseDown) {
                    Logging.log(ident + ": Hibernate Down: Sleeping");
                    try {
                        Thread.sleep(30 * 1000);
                    } catch (Exception ignore) {
                    }
                    continue;
                }
            }
            if (stopflag) {
                Logging.info(ident + ": Exiting");
                return;
            }
            int nfiles = loop();
            if (stopflag) {
                Logging.info(ident + ": Exiting");
                return;
            }
            if (nfiles == 0) {
                // Sleep only if there is no file waiting.
                try {
                    Thread.sleep(30 * 1000);
                } catch (Exception ignore) {
                }
            }
        }
    }

    public int loop() {
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

        String files[] = XP.getFileList(directory);

        int nfiles = files.length;

        if (nfiles == 0) {
            return 0;
        }

        statusUpdater = new StatusUpdater();
        newVOUpdate = new NewVOUpdate();
        newClusterUpdate = new NewClusterUpdate();

        for (int i = 0; i < files.length; i++) {
            global.put("listener", new java.util.Date());

            if (stopflag) {
                Logging.info(ident + ": Exiting");
                return nfiles;
            }

            file = files[i];
            //MPERF: Logging.fine(ident + ": Start Processing: " + file);
            blob = XP.get(files[i]);

            xml = "";
            rawxmllist.clear();
            extraxmllist.clear();
            md5list.clear();
            historydatelist.clear();

            gotreplication = gothistory = false;

            ninput = ninput + 1;

            try {
                saveIncoming(blob);
            } catch (Exception e) {
                Logging.warning(ident +
                                ": ERROR! Loop failed to backup incoming " +
                                "message. \nError: " +
                                e.getMessage() + "\n");
            }

            Record current = null;
            Origin origin = null;

            //
            // see if trace requested
            //
            if (p.getProperty("service.datapump.trace").equals("1")) {
                Logging.debug(ident + ": XML Trace:" + "\n\n" + blob + "\n\n");
            }

            // See if we have an origin preceding the data.
            StringTokenizer st = null;
            String nextpart = blob;

            if (blob.startsWith(originMarker)) {
                st = new StringTokenizer(blob, "|");
                if (st.hasMoreTokens()) {
                    // skip marker
                    st.nextToken();
                    if (st.hasMoreTokens()) {
                        String originStr = st.nextToken();
                        try {
                            origin = converter.convertOrigin(originStr);
                        } catch (Exception e) {
                            Logging.warning(ident + ": Parse error:  ", e);
                            Logging.warning(ident + ": XML:  " + "\n" + originStr);
                        }
                    }
                }
                if (st.hasMoreTokens()) {
                    nextpart = st.nextToken();
                } else {
                    nextpart = "";
                }
            }
            //
            // see if we got a normal update or a replicated one
            //
            try {
                int nseen = 0;
                if (nextpart.startsWith(replicationMarker)) {
                    if (st == null) {
                        // We could assert that nextpart == blob
                        st = new StringTokenizer(blob, "|");
                        if (st.hasMoreTokens()) {
                            // Skip marker
                            st.nextToken();
                        }
                    }

                    gotreplication = true;

                    while (st.hasMoreTokens()) {
                        String val = st.nextToken();
                        if (val.equals(replicationMarker) && st.hasMoreTokens()) {
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
                            if (extraxml.endsWith(replicationMarker)) {
                                extraxml =
                                    extraxml.substring(0, extraxml.length() -
                                                       replicationMarker.length());
                            }
                            extraxmllist.add(extraxml);
                        } else {
                            extraxmllist.add(null);
                        }
                        nseen = nseen + 1;
                    }
                } else if (nextpart.startsWith("history")) {
                    gothistory = true;
                    if (st == null) {
                        // We could assert that nextpart == blob
                        st = new StringTokenizer(blob, "|");
                        if (st.hasMoreTokens()) {
                            // Skip marker
                            st.nextToken();
                        }
                    }
                    while (st.hasMoreTokens()) {
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
                        if (st.hasMoreTokens()) {
                            // Skip next marker
                            st.nextToken();
                        }
                    }
                } else if (nextpart.startsWith("historymd5")) {
                    gothistory = true;
                    if (st == null) {
                        // We could assert that nextpart == blob
                        st = new StringTokenizer(blob, "|");
                        if (st.hasMoreTokens()) {
                            // Skip marker
                            st.nextToken();
                        }
                    }
                    while (st.hasMoreTokens()) {
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
                        if (st.hasMoreTokens()) {
                            // Skip next marker
                            st.nextToken();
                        }
                    }
                } else {
                    xml = nextpart;
                    nseen = nseen + 1;
                }
                if (nseen > 1) {
                    xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<RecordEnvelope>" + xml + "</RecordEnvelope>";
                }
            } catch (Exception e) {
                Logging.warning(ident + ": Error:Processing File: " + file);
                Logging.warning(ident + ": Blob: " + blob);
                try {
                    File temp = new File(file);
                    temp.delete();
                } catch (Exception ignore) {
                }
                continue;
            }

            if (xml == null) {
                Logging.warning(ident + ": No data to process: " + file);
                try {
                    File temp = new File(file);
                    temp.delete();
                } catch (Exception ignore) {
                }
                continue;
            }

            //MPERF: Logging.fine(ident + ": Processing: " + file);
            Logging.log(ident + ": Processing: " + file);

            ArrayList records = new ArrayList();

            try {
                if (gotreplication) {
                    // First attempt to fix an error in data
                    // replicated from old collectors.
                    xml = xml.replaceAll("<LocalJobId ><undefined></LocalJobId>",
                                         "<LocalJobId >&lt;undefined&gt;" +
                                         "</LocalJobId>");
                }
                records = convert(xml);
                if (records.size() > 1) {
                    Logging.log(ident + ": Received envelope of " +
                                records.size() + " records");
                }
            } catch (Exception e) {
                try {
                    if (gotreplication) {
                        Logging.fine(ident + ": Received bad replication XML");
                        errorRecorder.saveParse("Replication", "Parse", xml);
                    } else if (gothistory) {
                        Logging.fine(ident + ": Received bad history XML");
                        errorRecorder.saveParse("History", "Parse", xml);
                    } else {
                        Logging.fine(ident + ": Received bad probe XML");
                        errorRecorder.saveParse("Probe", "Parse", xml);
                    }
                } catch (Exception ignore) {
                }
            }
            int rSize = records.size();
            //MPERF: Logging.fine(ident+ ": converted " + rSize + " records");
            if (rSize > 1 && origin != null) {
                // The origin object will be reused.  Let's store it first to avoid
                // problem in case the very first record is a duplicate!
                String rId = ": ";
                if (gotreplication) {
                    rId += "Replication ";
                } else if (gothistory) {
                    rId += "History ";
                }
                Session or_session = null;
                Transaction or_tx = null;
                Integer nTries = 0;
                Boolean keepTrying = true;
                while (keepTrying) {
                    ++nTries;
                    try {
                        or_session = HibernateWrapper.getSession();
                        or_tx = or_session.beginTransaction();
                        origin = origin.attach(or_session);
                        or_session.flush();
                        or_tx.commit();
                        keepTrying = false;
                    } catch (Exception e) {
                        if (or_session != null && or_session.isOpen() && or_session.isConnected()) {
                            if ((or_tx != null) && or_tx.isActive()) {
                                or_tx.rollback();
                            }
                            or_session.close();
                        }
                        if (!this.detectAndReportLockFailure(e, nTries)) {
                            Logging.warning(ident + rId +
                                            ": received unexpected exception " +
                                            e.getMessage() + " while processing origin entry.");
                            Logging.debug(ident + rId + ": exception details:", e);
                            return 0;
                        }
                    }
                }
                //MPERF: Logging.fine(ident + rId + " saved Origin object.");
                if (or_session != null && or_session.isOpen() && or_session.isConnected()) {
                    or_session.close();
                }
            }
            for (int j = 0; j < rSize; j++) {

                current = (Record) records.get(j);

                // For information logging.
                String rId = ": ";
                if (gotreplication) {
                    rId += "Replication ";
                } else if (gothistory) {
                    rId += "History ";
                }
                String simpleName = current.getClass().getSimpleName();
                rId += simpleName;
                if (rSize > 1) {
                    rId += " " + (j + 1) + " / " + rSize;
                }
                rId += " (" + current.getProbeName();
                if (simpleName.equals("ProbeDetails")) {
                    rId += ", recordId=" +
                        ((ProbeDetails) current).getRecordIdentity();
                }
                rId += ")";

                Probe probe;
                
                //MPERF: Logging.fine(ident + rId + " starting hibernate operations.");
                Integer nTries = 0;
                Boolean keepTrying = true;
                while (keepTrying) {
                    ++nTries;
                    Session pr_session = HibernateWrapper.getSession();
                    Transaction pr_tx = null;
                    try {
                        pr_tx = pr_session.beginTransaction();

                        probe = statusUpdater.update(pr_session, current, xml);
                        pr_session.flush();
                        pr_tx.commit();
                        // Set the probe on the current object.
                        current.setProbe(probe);
                        keepTrying = false;
                        pr_session.close();
                    } catch (Exception e) {
                        if (pr_session != null && pr_session.isOpen() && pr_session.isConnected()) {
                            if ((pr_tx != null) && pr_tx.isActive()) {
                                pr_tx.rollback();
                            }
                            pr_session.close();
                        }
                        if (!this.detectAndReportLockFailure(e, nTries)) {
                            Logging.warning(ident + rId +
                                            ": received unexpected exception " +
                                            e.getMessage() + " while processing probe entry.");
                            Logging.debug(ident + rId + ": exception details:",e);
                            return 0;
                        }
                    }
                }
                //MPERF: Logging.fine(ident + rId + " saved probe object.");


                // Fix up the record; in particular set EndTime if it out of whack.
                updater.Update(current);

                Boolean acceptRecord = true;
                if (!collectorService.housekeepingServiceDisabled()) {
                    Date date;
                    try {
                        date = current.getDate();
                    } catch (NullPointerException e) {
                        date = current.getServerDate();
                    }
                    Date expirationDate = current.getExpirationDate();
                    if (date.before(expirationDate)) {
                        acceptRecord = false;
                        try {
                            if (gotreplication) {
                                Logging.fine(ident + rId +
                                             ": Rejected record because " +
                                             "its 'data' are too old (" +
                                             current.getDate() + " < " +
                                             expirationDate + ")");
                                errorRecorder.saveDuplicate("Replication",
                                                            "ExpirationDate",
                                                            0, current);
                            } else if (gothistory) {
                                Logging.fine(ident + rId +
                                             ": Ignored history record " +
                                             "because its 'data' are too " +
                                             "old (" + current.getDate() +
                                             " < " + expirationDate + ")");
                            } else {
                                Logging.fine(ident + rId +
                                             ": Rejected record because " +
                                             "its 'data' are too old (" +
                                             current.getDate() + " < " +
                                             expirationDate + ")");
                                errorRecorder.saveDuplicate("Probe",
                                                            "ExpirationDate",
                                                            0, current);
                            }

                        } catch (Exception e) {
                            if (!handleUnexpectedException(rId, e, gotreplication, current)) {
                                return 0;
                            }

                        }
                    }
                }

                if ((!gothistory) || (!(md5list.size() > j)) ||
                    md5list.get(j) == null) {
                    try {
                        if (current instanceof JobUsageRecord) {
                            String md5key = current.computemd5(DatabaseMaintenance.UseJobUsageSiteName());
                            current.setmd5(md5key);
                        } else {
                            String md5key = current.computemd5(false);
                            current.setmd5(md5key);
                        }
                    } catch (Exception e) {
                        if (!handleUnexpectedException(rId, e, gotreplication, current)) {
                            return 0;
                        }
                    }
                } else {
                    current.setmd5((String) md5list.get(j));
                }
                if (current.getTableName().equals("ProbeDetails")) {
                    Integer pd_dbid = fProbeDetails.get(current.getmd5());
                    if (pd_dbid != null) {
                        // This is a duplicate.
                        Logging.fine(ident + rId +
                                     ": " + "(fast) Ignore duplicate of record " +
                                     pd_dbid);
                        acceptRecord = false;
                    }
                }
                else if (current.getTableName().equals("Subcluster")) {
                    Integer pd_dbid = fSubcluster.get(current.getmd5());
                    if (pd_dbid != null) {
                        // This is a duplicate.
                        Logging.fine(ident + rId +
                                     ": " + "(fast) Ignore duplicate of Subcluster " +
                                     pd_dbid);
                        acceptRecord = false;
                    }
                } else if (current.getTableName().equals("ComputeElement")) {
                    Integer pd_dbid = fComputeElement.get(current.getmd5());
                    if (pd_dbid != null) {
                        // This is a duplicate.
                        Logging.fine(ident + rId +
                                     ": " + "(fast) Ignore duplicate of ComputeElement " +
                                     pd_dbid);
                        acceptRecord = false;
                    }             
                } else if (current.getTableName().equals("StorageElement")) {
                    Integer pd_dbid = fStorageElement.get(current.getmd5());
                    if (pd_dbid != null) {
                        // This is a duplicate.
                        Logging.fine(ident + rId +
                                     ": " + "(fast) Ignore duplicate of StorageElement " +
                                     pd_dbid);
                        acceptRecord = false;
                    }             
                }

                if (acceptRecord) {
                    current.setDuplicate(false);
                    if (origin != null) {
                        //MPERF: Logging.fine(ident + rId + " about to add origin objects.");
                        current.addOrigin(origin);
                    }

                    Session rec_session = null;
                    Transaction rec_tx = null;
                    nTries = 0;
                    keepTrying = true;
                    while (keepTrying) {
                        ++nTries;
                        try {
                            rec_session = HibernateWrapper.getSession();
                            rec_tx = rec_session.beginTransaction();
                            //MPERF: Logging.fine(ident + rId + " attaching VO and other content.");
                            synchronized (lock) {
                                // Synchronize on lock so we're
                                // guaranteed only one run per
                                // collector, not one per thread if we
                                // were synchronizing on the objects
                                // themselves.
                                newVOUpdate.check(current, rec_session);
                                newClusterUpdate.check(current, rec_session);
                                current.AttachContent(rec_session);
                                // Reduce contention on the attached objects (in particular Connection)
                                // to avoid DB deadlock.
                                Logging.debug(ident + rId + ": Before session flush");
                                rec_session.flush();
                            }
                            //MPERF: Logging.fine(ident + rId + " managing RawXML.");
                            String incomingxml = current.getRawXml();
                            String rawxml = null;
                            String extraxml = null;
                            if (rawxmllist.size() > j) {
                                rawxml = (String) rawxmllist.get(j);
                                if (rawxml != null) {
                                    current.setRawXml(rawxml);
                                }
                            }
                            Logging.debug(ident + rId + ": Before Hibernate Save");
                            if (gothistory) {
                                Date serverDate = new Date(Long.parseLong((String) historydatelist.get(j)));
                                current.setServerDate(serverDate);
                            }
                            //MPERF: Logging.fine(ident + rId + " saving object.");
                            rec_session.save(current);
                            //MPERF: Logging.fine(ident + rId + " executing trigger.");
                            current.executeTrigger(rec_session);
                            //MPERF: Logging.fine(ident + rId + " executing flush.");
                            rec_session.flush();
                            //MPERF: Logging.fine(ident + rId + " executing comming.");
                            rec_tx.commit();
                            keepTrying = false;
                            rec_session.close();
                            //MPERF: Logging.fine(ident + ": After Transaction Commit");
                            // Save history
                            if (!gothistory) {
                                saveHistory(current, incomingxml,
                                            rawxml, extraxml, gotreplication);
                            }
                            nrecords = nrecords + 1;
                            Logging.fine(ident + rId + " saved.");
                        } catch (ConstraintViolationException e) {
                            if (rec_session != null && rec_session.isOpen() && rec_session.isConnected()) {
                                if (rec_tx != null && rec_tx.isActive()) {
                                    rec_tx.rollback();
                                }
                                rec_session.close();
                            }
                            try {
                                handleConstraintViolationException(e, current, rId, gotreplication, gothistory);
                                keepTrying = false;
                            } catch (ConstraintViolationException e2) {
                                // Catch rethrown exception that we couldn't handle in the function
                                if (!handleUnexpectedException(rId, e, gotreplication, current,
                                                               "Received unexpected constraint violation")) {
                                    return 0;
                                }
                            } catch (Exception e2) {
                                if (!handleUnexpectedException(rId, e, gotreplication, current)) {
                                    return 0;
                                }
                            }
                        } catch (Exception e) {
                            if (rec_session != null && rec_session.isOpen() && rec_session.isConnected()) {
                                if (rec_tx != null && rec_tx.isActive()) {
                                    rec_tx.rollback();
                                }
                                rec_session.close();
                            }
                            if (detectAndReportLockFailure(e, nTries) ||
                                handleUnexpectedException(rId, e, gotreplication, current)) {
                                return 0;
                            }
                        } // End general catch
                    } // End while (keepTrying)
                } // End of handling accepted records.
            } // End of for each record loop
            // Logging.log(ident + ": Before File Delete: " + file);
            try {
                File temp = new File(file);
                temp.delete();
            } catch (Exception ignore) {
                // Logging.log(ident + ": File Delete Failed: " + file +
                // " Error: " + ignore);
            }
            // Logging.log(ident + ": After File Delete: " + file);
            itotal++;
        }
        Logging.fine(ident + ": Total Input Messages: " + itotal);
        Logging.fine(ident + ": Total Records: " + nrecords);
        return nfiles;
    }

    public File getDirectory(String what) {
        Date now = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddkk");
        String path = historypath + what + "-" + format.format(now);
        File dirondisk = new File(path);
        if (!dirondisk.exists()) {
            dirondisk.mkdir();
        }

        long part = nrecords / recordsPerDirectory;

        File subdir = new File(dirondisk, directory_part + "-" + part);

        if (!subdir.exists()) {
            subdir.mkdir();
        }
        return subdir;
    }

    public void saveIncoming(String data) throws java.io.IOException {
        File where = getDirectory("old");
        File errorfile = File.createTempFile("old-", ".xml", where);
        String filename = errorfile.getPath();
        XP.save(filename, data);
    }

    public void saveHistory(Record current, String xml, String rawxml,
                            String extraxml, boolean gotreplication)
        throws java.io.IOException {
        Date serverDate = current.getServerDate();
        File where = getDirectory("history");

        File historyfile = File.createTempFile("history-", ".xml", where);
        String filename = historyfile.getPath();

        StringBuffer data;
        if (gotreplication) {
            data = new StringBuffer("history" + "|" + serverDate.getTime() +
                                    "|" + xml + "|" + rawxml);
            if (extraxml != null) {
                data.append("|" + extraxml);
            }
        } else {
            data = new StringBuffer("historymd5" + "|" + serverDate.getTime() +
                                    "|" + xml + "|" + current.getmd5());
        }
        XP.save(filename, data.toString());
    }

    public ArrayList convert(String xml) throws Exception {
        ArrayList records = null;

        try {
            records = converter.convert(xml);
        } catch (Exception e) {
            Logging.info(ident + ": Parse error:  " + e.getMessage());
            Logging.info(ident + ": XML:  " + "\n" + xml);
            throw e;
        }

        // The usage records array list is now populated with all the
        // job usage records found in the given XML file return it to
        // the caller.
        return records;
    }

    private boolean handleUnexpectedException(String rId, Exception e,
                                              boolean gotreplication, Record current) {
        return handleUnexpectedException(rId, e, gotreplication, current,
                                         "Received unexpected exception");
    }

    private boolean handleUnexpectedException(String rId, Exception e,
                                              boolean gotreplication, Record current,
                                              String message) {
        Logging.warning(ident + rId + ": " + message + " " + e.getMessage());
        Logging.debug(ident + rId + ": exception details:", e);
        if (HibernateWrapper.databaseUp()) {
            try {
                if (gotreplication) {
                    errorRecorder.saveSQL("Replication", "SQLError", current);
                } else {
                    errorRecorder.saveSQL("Probe", "SQLError", current);
                }
            } catch (Exception ignore) {
            }
        } else {
            Logging.warning(ident + ": Communications error: " + "shutting down");
            return false;
        }
        Logging.warning(ident + ": Error in Process: ", e);
        Logging.warning(ident + ": Current: " + current);
        return true;
    }

    private void handleConstraintViolationException(ConstraintViolationException e,
                                                    Record current,
                                                    String rId,
                                                    Boolean gotreplication,
                                                    Boolean gothistory) throws Exception {
        Logging.debug(ident + rId + ": handling ConstraintViolationException caused by SQL: " +
                      e.getSQL());
        int dupdbid = 0;
        Boolean needCurrentSaveDup = false;
        if (e.getSQLException().getMessage().
            matches(".*\\b[Dd]uplicate\\b.*")) { // Duplicate
            if (e.getSQL().matches(".*_Meta .*")) {
                if (current.getTableName().equals("JobUsageRecord")) {
                    UserIdentity newUserIdentity =
                        ((JobUsageRecord) current).getUserIdentity();
                    Session dup_session = HibernateWrapper.getSession();
                    Query dup_query = dup_session.createQuery("select record from " +
                                                              "JobUsageRecord " +
                                                              "record where " +
                                                              "record.md5 = " +
                                                              "'" +
                                                              current.getmd5() +
                                                              "'").setCacheMode(CacheMode.IGNORE);
                    Boolean savedCurrent = false;
                    Transaction dup_tx = null;
                    JobUsageRecord original_record =
                        (JobUsageRecord) dup_query.uniqueResult();
                    if (original_record == null) {
                        return;
                    }
                    dupdbid = original_record.getRecordId();
                    UserIdentity originalUserIdentity =
                        original_record.getUserIdentity();
                    if (newUserIdentity == null) {
                        return;
                    }
                    Boolean newerIsBetter = false;
                    String replaceReason = null;
                    if (originalUserIdentity == null) {
                        newerIsBetter = true;
                        replaceReason = "original UserIdentity block is null";
                    } else if ((newUserIdentity.getVOName() !=
                                null) &&
                               (newUserIdentity.getVOName().
                                length() != 0) &&
                               (!newUserIdentity.getVOName().
                                equalsIgnoreCase("Unknown"))) {
                        // Have something with which to replace it.
                        replaceReason = "New VOName is better";
                        if (originalUserIdentity.getVOName() ==
                            null) {
                            newerIsBetter = true;
                            replaceReason +=
                                " -- original VOName is null";
                        } else if (originalUserIdentity.getVOName().length() == 0) {
                            newerIsBetter = true;
                            replaceReason +=
                                " -- original VOName is empty";
                        } else if (originalUserIdentity.getVOName().
                                   equalsIgnoreCase("Unknown")) {
                            newerIsBetter = true;
                            replaceReason +=
                                " -- original VOName is " +
                                "\"Unknown\")";
                        } else if ((newUserIdentity.getVOName().startsWith("/")) &&
                                   (!originalUserIdentity.getVOName().startsWith("/"))) {
                            newerIsBetter = true;
                            replaceReason +=
                                " -- original VOName is not fully qualified";
                        }
                    } // End if (originalUserIdentity == null)
                    if (!newerIsBetter) { // Still haven't decided
                        if ((newUserIdentity.getCommonName() != null) &&
                            (!newUserIdentity.getCommonName().
                             startsWith("Generic"))) {
                            replaceReason =
                                "New CommonName is better";
                            if (originalUserIdentity.getCommonName() == null) {
                                newerIsBetter = true;
                                replaceReason +=
                                    " -- original CommonName " +
                                    "is null";
                            } else if (originalUserIdentity.getCommonName().length() == 0) {
                                newerIsBetter = true;
                                replaceReason +=
                                    " -- original CommonName " +
                                    "is empty";
                            } else if (originalUserIdentity.getCommonName().
                                       startsWith("Generic")) {
                                newerIsBetter = true;
                                replaceReason +=
                                    " -- original CommonName " +
                                    "is generic";
                            }
                        } else if ((newUserIdentity.getKeyInfo() != null) &&
                                   (originalUserIdentity.getKeyInfo() == null)) {
                            newerIsBetter = true;
                            replaceReason =
                                "Original KeyInfo is null";
                        }
                    } // End if (!newerIsBetter)
                    if (newerIsBetter) {
                        Integer nTries = 0;
                        Boolean keepTrying = true;
                        while (keepTrying) {
                            ++nTries;
                            try {
                                if (dup_session == null || (!dup_session.isOpen()) || (!dup_session.isConnected())) {
                                    dup_session = HibernateWrapper.getSession();
                                }
                                dup_tx = dup_session.beginTransaction();
                                // Keep the new one and ditch the old
                                Logging.fine(ident + rId +
                                             ": Replacing record " +
                                             dupdbid +
                                             " with \"better\" " +
                                             "record (" +
                                             replaceReason + ").");
                                SummaryUpdater.removeFromSummary(original_record.getRecordId(),
                                                                 dup_session);
                                // Delete the record.
                                String originalXml =
                                    original_record.asXML();
                                String originalTableName =
                                    original_record.getTableName();
                                dup_session.delete(original_record);
                                if (!savedCurrent) {
                                    // If we haven't saved
                                    // the current record
                                    // yet, flush and commit
                                    // the delete
                                    // (important) and then
                                    // save the current record.
                                    dup_session.flush();
                                    dup_tx.commit();
                                    dup_tx = dup_session.beginTransaction();
                                    dup_session.save(current);
                                    current.executeTrigger(dup_session);
                                    savedCurrent = true;
                                }
                                dup_tx.commit();
                                keepTrying = false;
                                if (original_record.setDuplicate(true)) {
                                    if (gotreplication) {
                                        errorRecorder.saveDuplicate("Replication",
                                                                    "Duplicate",
                                                                    current.getRecordId(),
                                                                    originalXml,
                                                                    originalTableName);
                                    } else if (gothistory) { // NOP
                                    } else {
                                        errorRecorder.saveDuplicate("Probe",
                                                                    "Duplicate",
                                                                    current.getRecordId(),
                                                                    originalXml,
                                                                    originalTableName);
                                    }
                                }
                            } catch (Exception e2) {
                                if (dup_session != null && dup_session.isOpen() && dup_session.isConnected()) {
                                    if (dup_tx != null && dup_tx.isActive()) {
                                        dup_tx.rollback();
                                    }
                                    dup_session.close();
                                }
                                if (!detectAndReportLockFailure(e, nTries)) {
                                    throw e2; // Re-throw;
                                }
                            } // End try (resolve duplicate JobUsageRecord)
                        } // End while (keepTrying)
                    } // End if (newerIsBetter)
                    if (!savedCurrent) {
                        needCurrentSaveDup =
                            current.setDuplicate(true);
                        if (!needCurrentSaveDup) { // Save probe object anyway
                            Probe localprobe = current.getProbe();
                            if (localprobe != null) {
                                Integer nTries = 0;
                                Boolean keepTrying = true;
                                while (keepTrying) {
                                    ++nTries;
                                    try {
                                        if (dup_session == null || (!dup_session.isOpen()) || (!dup_session.isConnected())) {
                                            dup_session = HibernateWrapper.getSession();
                                        }
                                        dup_tx = dup_session.beginTransaction();
                                        dup_session.saveOrUpdate(localprobe);
                                        dup_session.flush();
                                        dup_tx.commit();
                                        dup_session.close();
                                        keepTrying = false;
                                    } catch (Exception e2) {
                                        if (!detectAndReportLockFailure(e, nTries)) {
                                            throw e2; // Re-throw
                                        }
                                    } // End try (save probe)
                                } // End while (keepTrying)
                            } // End if (localprobe != null)
                        } // End if (!needCurrentSaveDup)
                    } // End if (!savedCurrent)
                    if (dup_session != null && dup_session.isOpen() && dup_session.isConnected()) {
                        dup_session.close();
                    }
                } else { // Not JobUsageRecord
                    needCurrentSaveDup = current.setDuplicate(true);
                    Session dup2_session = HibernateWrapper.getSession();
                    try {
                        String cmd = "select dbid from " +
                            current.getTableName() +
                            "_Meta record where " +
                            "record.md5 = " +
                            "'" +
                            current.getmd5() +
                            "'";
                        Integer dup_dbid = (Integer) (dup2_session.createSQLQuery(cmd).uniqueResult());
                        // Avoid infinite growth
                        if (current instanceof ProbeDetails) {
                            if (fProbeDetails.size() > 500) {
                                fProbeDetails.clear();
                            }
                            fProbeDetails.put(current.getmd5(), dup_dbid);
                        } else if (current instanceof Subcluster) {
                            if (fSubcluster.size() > 500) {
                                fSubcluster.clear();
                            }
                            fSubcluster.put(current.getmd5(), dup_dbid);
                        } else if (current instanceof ComputeElement) {
                            if (fComputeElement.size() > 5000) {
                                fComputeElement.clear();
                            }
                            fComputeElement.put(current.getmd5(), dup_dbid);
                        } else if (current instanceof StorageElement) {
                            if (fStorageElement.size() > 1000) {
                                fStorageElement.clear();
                            }
                            fStorageElement.put(current.getmd5(), dup_dbid);
                        }
                        dupdbid = dup_dbid;
                    } finally {
                        dup2_session.close();
                    }
                    if (!needCurrentSaveDup) {
                        Logging.fine(ident + rId +
                                     ": " + "Ignore duplicate of record " +
                                     dupdbid);
                    }
                } // End if (JobUsageRecord)
                if (needCurrentSaveDup) {
                    try {
                        Logging.fine(ident + rId +
                                     ": " + (gothistory ? "Ignore" : "Save") +
                                     " duplicate of record " +
                                     dupdbid);
                        if (gotreplication) {
                            errorRecorder.saveDuplicate("Replication",
                                                        "Duplicate",
                                                        dupdbid,
                                                        current);
                        } else if (gothistory) { // NOP
                        } else {
                            errorRecorder.saveDuplicate("Probe",
                                                        "Duplicate",
                                                        dupdbid,
                                                        current);
                        }
                    } catch (Exception ignore) {
                    }
                } // End if (needCurrentSaveDup)
            } else if (e.getSQL().matches(".*into Origin.*")) {
                Logging.warning(ident + rId + ": failure updating Origin table.");
                Logging.warning(ident + rId + ": exception details:\n" +
                                ExceptionUtils.getFullStackTrace(e));
                throw e; // Re-throw.
            } // End SQL check
        } else {
            // Constraint exception, but not a duplicate. Re-throw.
            throw e;
        } // End if (constraint is duplicate)
    
    } // End handleConstrainViolationException()

}
