
\! echo
\! echo =========================================================
\! echo   Review MasterTransferSummary - summarized
\! echo =========================================================

SELECT 
   Site.SiteName AS Site
  ,VO.VOName as GratiaVO
  ,Main.VOCorrid as VOCorrid
  ,VC.ReportableVOName
  ,VC.VOName as ProbeVO
  ,Probe.ProbeName
  ,min(date_format(StartTime,"%Y-%m")) as Earliest
  ,max(date_format(StartTime,"%Y-%m")) as Latest
  ,Sum(NJobs) as Jobs
  ,Round(Sum(TransferSize/1000)) as TransferSize
  ,Round(Sum(TransferDuration/3600)) as TransferDuration
  ,count(*) as rec_cnt
FROM
     VO
    ,VONameCorrection VC
    ,MasterTransferSummary Main
    ,Site
    ,Probe
where
      VO.VOName like @voname
  and VO.VOid         = VC.VOid
  and VC.corrid       = Main.VOCorrid
  and Main.ProbeName  = Probe.ProbeName
  and Probe.siteid    = Site.siteid
group by 
   Site
  ,GratiaVO
  ,VOCorrid
  ,VC.ReportableVOName
  ,ProbeVO
  ,Probe.ProbeName
-- limit 10
;

\! echo
\! echo =========================================================
\! echo   VONameCorrection entries with JUR recs
\! echo =========================================================

SELECT 
   Site.SiteName AS Site
  ,VO.VOName as GratiaVO
  ,Main.VOCorrid as VOCorrid
  ,VC.ReportableVOName
  ,VC.VOName as ProbeVO
  ,Probe.ProbeName
  ,min(date_format(StartTime,"%Y-%m")) as Earliest
  ,max(date_format(StartTime,"%Y-%m")) as Latest
  ,Sum(NJobs) as Jobs
  ,Round(Sum(TransferSize/1000)) as TransferSize
  ,Round(Sum(TransferDuration/3600)) as TransferDuration
  ,count(*) as rec_cnt
FROM
     VO
    ,VONameCorrection VC
    ,MasterTransferSummary Main
    ,Site
    ,Probe
where
      VO.VOName like @voname
  and VO.VOid         = VC.VOid
  and VC.corrid       = Main.VOCorrid
-- 3 months back
  and StartTime > "2011-09-01"
  and Main.ProbeName  = Probe.ProbeName
  and Probe.siteid    = Site.siteid
group by 
   Site
  ,GratiaVO
  ,VOCorrid
  ,VC.ReportableVOName
  ,ProbeVO
  ,Probe.ProbeName
-- limit 10
;

\! echo
\! echo ============================================================
\! echo   MasterTransferSummary table count
\! echo   - also show number of VONameCorrection entries to delete
\! echo ============================================================

SELECT
   Main.VOcorrid
  ,VO.VOName as GratiaVO
  ,sum(NJobs) as Jobs
  ,count(*) as nbr_of_recs
FROM
     VO
    ,VONameCorrection VC
    ,MasterTransferSummary Main
where
      VO.VOName like @voname
  and VO.VOid   = VC.VOid
  and VC.corrid = Main.VOCorrid
group by
   Main.VOcorrid
  ,GratiaVO
;

\! echo
\! echo =========================================================
\! echo   MasterTransferSummary table - number of records to delete
\! echo =========================================================

SELECT
   count(*) as nbr_of_deletions
FROM
     VO
    ,VONameCorrection VC
    ,MasterTransferSummary Main
where
      VO.VOName like @voname
  and VO.VOid   = VC.VOid
  and VC.corrid = Main.VOCorrid
;

\! echo
\! echo ============================================================
\! echo   MasterTransferSummary DELETE statements
\! echo ============================================================

SELECT
   CONCAT("DELETE FROM MasterTransferSummary WHERE VOcorrid = ", a.VOCorrid ,";")
as delete_sql
FROM
 (select distinct(Main.VOCorrid) as VOCorrid
  from
     VO
    ,VONameCorrection VC
    ,MasterTransferSummary Main
where
      VO.VOName like @voname
  and VO.VOid   = VC.VOid
  and VC.corrid = Main.VOCorrid
) a
order by
   delete_sql
;

\! echo
\! echo ============================================================
\! echo   MasterTransferSummary DELETE statements
\! echo ============================================================

SELECT *
FROM MasterTransferSummary
WHERE VOCorrid in
 (select distinct(Main.VOCorrid) as VOCorrid
  from
     VO
    ,VONameCorrection VC
    ,MasterTransferSummary Main
where
      VO.VOName like @voname
  and VO.VOid   = VC.VOid
  and VC.corrid = Main.VOCorrid
)
-- limit 10
;
