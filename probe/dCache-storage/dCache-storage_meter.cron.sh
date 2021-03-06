#!/bin/bash
#
# dcache-storage_meter.cron.sh - Shell script used with cron to parse dcache-storage
#   files for OSG accounting data collection.
#      By Chris Green <greenc@fnal.gov>  Began 5 Sept 2006
# $Id: dCache-storage_meter.cron.sh,v 1.4 2008/09/08 21:35:34 greenc Exp $
# Full Path: $Source: /cvs/cd/dcache/gratia/dCache-storage/dCache-storage_meter.cron.sh,v $
###################################################################
PGM=$(basename $0)
Logger="/usr/bin/logger -s -t $PGM"

Meter_BinDir=$(dirname $0)
if [ "x$1" != "x" ] ; then
   probeconfig_loc=$1
else
   probeconfig_loc=/etc/gratia/dCache-storage/ProbeConfig
fi

# Set the working directory, where we expect to find the following
#    necessary files.
if [ -d ${Meter_BinDir} ]; then
  cd ${Meter_BinDir}
else
  ${Logger} "No such directory ${Meter_BinDir}"
  exit -1
fi

# Need to be sure there is not one of these running already
NCMeter=`ps -ef | grep dCache_storage_probe | grep -v grep | wc -l`
eval `grep WorkingFolder $probeconfig_loc`
if [ ${NCMeter} -ne 0 -a -e ${WorkingFolder}/dCache-storage_meter.cron.pid ]; then
  # We might have a condor_meter.pl running, let's verify that we 
  # started it.
  
  otherpid=`cat ${WorkingFolder}/dCache-storage_meter.cron.pid`
  NCCron=`ps -ef | grep ${otherpid} | grep dCache-storage_meter.cron | wc -l`
  if [ ${NCCron} -ne 0 ]; then 
 
    ${Logger} "There is a dCache_storage_probe running already"
    exit 1
  fi
fi

# We need to locate the probe script
if [ ! -r ./dCache_storage_probe ]; then
  ${Logger} "The dCache_storage_probe file is not in this directory: $(pwd)"
  exit -2
fi

# We need to locate these files and they must be readable
for Needed_File in $probeconfig_loc
do
  if [ ! -f ${Needed_File} ]; then
    ${Logger} \
     "The ${Needed_File} file is not in this directory: $(pwd)"
    exit -3
  fi
done

pp_dir=$(cd "$Meter_BinDir/.."; pwd)

enabled=`${pp_dir}/common/GetProbeConfigAttribute -c $probeconfig_loc EnableProbe`
(( status = $? ))
if (( $status != 0 )); then
  echo "ERROR checking probe configuration!" 1>&2
  exit $status
fi
if [[ -n "$enabled" ]] && [[ "$enabled" == "0" ]]; then
  ${pp_dir}/common/DebugPrint -c $probeconfig_loc -l 0 "Probe is not enabled: check $Meter_BinDir/ProbeConfig."
  exit 1
fi

WorkingFolder=`${pp_dir}/common/GetProbeConfigAttribute -c $probeconfig_loc WorkingFolder`
if [ ! -d ${WorkingFolder} ]; then
  if [ "x${WorkingFolder}" != "x" ] ; then 
    mkdir -p ${WorkingFolder}
  else
    ${Logger} "There is no WorkingFolder directory defined in $Meter_BinDir/ProbeConfig."
    exit -4
  fi
fi

echo $$ > ${WorkingFolder}/dCache-storage_meter.cron.pid
(( status = $? ))
if (( $status != 0 )); then
   ${Logger} "dCache-storage_meter.cron.sh failed to store the pid in  ${WorkingFolder}/dCache-storage_meter.cron.pid"
   exit -2
fi 

#--- run the probes ----
./dCache_storage_probe -c storage.cfg

ExitCode=$?

# If the probe ended in error, report this in Syslog and exit
if [ $ExitCode != 0 ]; then
  ${pp_dir}/common/DebugPrint -c $probeconfig_loc -l -1 "ALERT: $0 exited abnormally with [$ExitCode]"
  exit $ExitCode
fi
  
exit 0

#==================================================================
# CVS Log
# $Log: dCache-storage_meter.cron.sh,v $
# Revision 1.4  2008/09/08 21:35:34  greenc
# Check status after attempting to get config attribute.
#
# Revision 1.3  2008/03/20 19:31:19  greenc
# I feel like I've fixed this before ...
#
# Revision 1.2  2008/03/18 15:15:36  greenc
# Correct all occurrences of old python script name.
#
# Revision 1.1  2008/03/17 17:17:31  greenc
# Standardize naming.
#
# This repository is the primary repository for this file now.
#
# Revision 1.5  2008/02/28 22:12:18  greenc
# Fix fix.
#
# Revision 1.4  2008/02/28 21:25:06  greenc
# Mirror glob fix from transfer init script.
#
# Revision 1.3  2008/01/17 17:29:37  greenc
# Fix for reported PYTHONPATH problem.
#
# Revision 1.2  2007/12/14 21:44:42  greenc
# Supress build output with -q option.
#
# Revision 1.1  2007/12/10 22:35:14  greenc
# Package dCache probes.
#
# Improve code reuse in scriptlets.
#
# Revision 1.5  2007/09/10 20:17:14  greenc
# Update SPEC file.
#
# Improve redirection of non-managed output.
#
# Revision 1.4  2007/05/25 23:34:56  greenc
# New utilities GetProbeConfigAttribute.py and DebugPrint.py.
#
# Cron scripts now check for EnableProbe attribute in config -- if present
# and 0, probe will not be invoked and log entry will be made.
#
# Fix fragility in spec file using "global" macro.
#
# Revision 1.3  2007/05/24 23:33:52  greenc
# Fix minor problems with glexec probe.
#
# Revision 1.2  2007/05/09 22:24:04  greenc
# gLExec probe moved into Gratia repository.
#
# Revision 1.3  2007/03/08 18:25:00  greenc
# John W's changes to forestall lockfile problems.
#
# Revision 1.2  2006/10/09 16:40:26  greenc
# Invoke the perl Gratia wrapper immediately after the urCollector run.
#
# Revision 1.1  2006/09/07 22:20:41  greenc
# Gratia-specific files for glexec probe.
#
# Revision 1.1  2006/08/21 21:10:03  greenc
# Probe areas reorganized to facilitate RPM building and new
# probes.
#
# README files in probe/condor and probe/common still need to be
# updated.
#
# Probe tarball creation removed from build script per discussion with Greg. Please see probe/build/README.
#
# RPM building commissioned and will be tested shortly.
#
# Revision 1.4  2006/07/20 14:41:48  pcanal
# permissions
#
# Revision 1.3  2006/07/20 14:38:53  pcanal
# change permisssion
#
# Revision 1.2  2006/06/16 15:57:37  glr01
# glr: reset condor-probe to contents from gratia-proto
#
# Revision 1.8  2006/06/06 21:45:16  pcanal
# update following the new directory layout
#
# Revision 1.7  2006/04/21 15:49:58  kschu
# There is now no output, unless there is a problem, updated note re: crontab
#
# Revision 1.6  2006/04/19 22:04:04  kschu
# Updated comment about setting up crontab entry
#
# Revision 1.5  2006/04/19 16:51:16  kschu
# Improved exception handling and comments within the script
#
# Revision 1.4  2006/04/17 22:25:14  kschu
# Gets the log file directory from ProbeConfig file.
#
# Revision 1.3  2006/04/13 15:56:29  kschu
# Uses a directory as probe command-line argument
#
# Revision 1.2  2006/04/10 19:52:30  kschu
# Refined data submission after code review
#
# Revision 1.1  2006/04/05 18:10:25  kschu
# First test version of script to be called by Cron
#

