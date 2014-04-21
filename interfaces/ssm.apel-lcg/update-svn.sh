#!/bin/bash
################################################################
# John Weigand (10/8/13)
#
# Updates the srouce code area with the latest data that needs
# to be retained.
################################################################
function logit {
  echo "$1" >>$tmpfile
}
#---------------
function logerr {
  logit "ERROR: $1"
  svn_updates_required=2
  send_email
  exit 1
}
#---------------
function validate {
  [ ! -d "$svnsrc" ] && logerr "You sure you are on the right node.
The svn src dir does not exist: $svnsrc"

  [ ! -f "$cfg" ] && logerr "You sure you are on the right node.
The $cfg does not exist."

  webapps="$(grep  '^WebappsDir' $cfg |awk '{print $2}')"
  [ ! -d "$webapps" ] && logerr "WebappsDir does not exist: $webapps"

  updates="$(grep  '^UpdatesDir' $cfg |awk '{print $2}')"
  [ ! -d "$updates" ] && logerr "UpdatesDir does not exist: $updates"

  sites="$(grep  '^SiteFilterFile' $cfg |awk '{print $2}')"
  [ ! -f "$sites" ] && logerr "SiteFilterFile does not exist: $sites"

  sitehistory="$(grep  '^SiteFilterHistory' $cfg |awk '{print $2}')"
  [ ! -d "$sitehistory" ] && logerr "SiteFilterHistory does not exist: $sitehistory"

  vos="$(grep  '^VOFilterFile' $cfg |awk '{print $2}')"
  [ ! -f "$vos" ] && logerr "VOFilterFile does not exist: $vos"

  source=/usr/share/gratia-apel
  [ ! -d "$source" ] && logerr "source directory does not exist: $source"
  [ "$(type svn &>/dev/null;echo $?)" != "0" ] && logerr "svn does not appear to be installed on this node"
 
  mailprocess=/bin/mail
  [ ! -x "$mailprocess" ] && logerr "Unable to send mail.  No $mailprocess"
  from_mail="$(grep  '^FromEmail' $cfg |awk '{print $2}')"
  [ -z "$vos" ] && logerr "No FromEmail in $cfg"
  to_mail="$(grep  '^ToEmail' $cfg |awk '{print $2}')"
  [ -z "$to_mail" ] && logerr "No ToMail in $cfg"
  cc_mail="$(grep  '^CcEmail' $cfg |awk '{print "-c",$2}')"
  [ "$cc_mail" = "NONE" ] && cc_mail=""

}
#---------------
function copy_file {
   cp $1 $2
   svn_updates_required=1
}
#---------------
function check_webapps {
  [   -z "$webapps" ] && logerr "(check_webapps function: webapps variable not set"
  [ ! -d "$webapps" ] && logerr "WebappsDir does not exist: $webapps"
  svndir=$svnsrc/webapps
  logit "Checking $svndir"
  files="$(ls $webapps)" 
  for file in $files
  do
    [ "$file" = "index.html" ] && continue
    if [ ! -f "$svndir/$file" ];then
      logit "... add $file"
      copy_file $webapps/$file $svndir/.
      svn add $svndir/$file
      continue
    fi
    rtn="$(diff $webapps/$file $svndir/$file &>/dev/null ;echo $?)"
    if [ "$rtn" != "0" ];then
      logit "... update $file"
      copy_file $webapps/$file $svndir/.
    fi
  done
}
#---------------
function check_apel_updates {
  [   -z "$updates" ] && logerr "(check_apel_updates function: updates variable not set"
  [ ! -d "$updates" ] && logerr "UpdatesDir does not exist: $updates"
  svndir=$svnsrc/apel-updates
  logit "Checking $svndir"
  files="$(ls $updates)" 
  for file in $files
  do
    [ "$file" = "index.html" ] && continue
    if [ ! -f "$svndir/$file" ];then
      logit "... add $file"
      copy_file $updates/$file $svndir/.
      svn add $svndir/$file
      continue
    fi
    rtn="$(diff $updates/$file $svndir/$file &>/dev/null ;echo $?)"
    if [ "$rtn" != "0" ];then
      logit "... update $file"
      copy_file $updates/$file $svndir/.
    fi
  done
}
#---------------
function check_reportable_sites {
  [   -z "$sites" ] && logerr "(check_reportable_sites function: sites variable not set"
  [ ! -f "$sites" ] && logerr "SiteFilterFile does not exist: $sites"
  svndir=$svnsrc
  file=$(basename $sites)
  logit "Checking $svndir/$file"
  if [ ! -f "$svndir/$file" ];then
    logerr "Something is seriously wrong. 
This file should always exist: $svndir/$file"
  fi
  rtn="$(diff $sites $svndir/$file &>/dev/null ;echo $?)"
  if [ "$rtn" != "0" ];then
    logit "... update $file"
    copy_file $sites $svndir/.
  fi
}
#---------------
function check_reportable_sites_history {
  [   -z "$sitehistory" ] && logerr "(check_reportable_sites function: sitehistory variable not set"
  [ ! -d "$sitehistory" ] && logerr "SiteFilterHistory does not exist: $sitehistory"
  svndir=$svnsrc/lcg-reportableSites.history
  logit "Checking $svndir"
  files="$(ls $sitehistory)" 
  for file in $files
  do
    if [ ! -f "$svndir/$file" ];then
      logit "... add $file"
      copy_file $sitehistory/$file $svndir/.
      svn add $svndir/$file
      continue
    fi
    rtn="$(diff $sitehistory/$file $svndir/$file &>/dev/null ;echo $?)"
    if [ "$rtn" != "0" ];then
      logit "... update $file"
      copy_file $sitehistory/$file $svndir/.
    fi
  done
}
#---------------
function check_reportable_vos {
  [   -z "$vos" ] && logerr "(check_reportable_vos function: vos variable not set"
  [ ! -f "$vos" ] && logerr "VOFilterFile does not exist: $vos"
  svndir=$svnsrc
  file=$(basename $vos)
  logit "Checking $svndir/$file"
  if [ ! -f "$svndir/$file" ];then
    logerr "Something is seriously wrong. 
This file should always exist: $svndir/$file"
  fi
  rtn="$(diff $vos $svndir/$file &>/dev/null ;echo $?)"
  if [ "$rtn" != "0" ];then
    logit "... update $file"
    copy_file $vos $svndir/.
  fi
}
#---------------
function check_source {
  [   -z "$source" ] && logerr "(check_source function: source variable not set"
  [ ! -d "$source" ] && logerr "Source file dir does not exist: $source"
  svndir=$svnsrc
  logit "Checking $svndir for source file changes"
  files="$(ls $source/*.py $source/*.sh)" 
  for file in $files
  do
    file=$(basename $file)
    [ "$file" = "lcg.sh" ] && continue
    [ "$file" = "find-late-updates.sh" ] && continue
    if [ ! -f "$svndir/$file" ];then
      logit "... add $file"
      continue
    fi
    rtn="$(diff $source/$file $svndir/$file &>/dev/null ;echo $?)"
    if [ "$rtn" != "0" ];then
      logit "... update $file"
    fi
  done
}
#---------------
function check_svn {
  [   -z "$svnsrc" ] && logerr "(check_svn function: source variable not set"
  [ ! -d "$svnsrc" ] && logerr "Source file dir does not exist: $source"
  svndir=$svnsrc
  logit "Checking svn"
  cd $svndir
#  svn status --show-updates 2>&1 |egrep -v "rpms|tarballs" 1>>$tmpfile 2>&1
  svn status 2>&1 |egrep -v "rpms|tarballs" 1>>$tmpfile 2>&1
  cd - 1>>$tmpfile 2>&1
}
#---------------
function send_email {
  case $svn_updates_required in 
    0 ) subject="No Gratia-APEL svn updates required on $(hostname -f)" ;;
    1 ) subject="Gratia-APEL svn updates required on $(hostname -f)"    ;;
    * ) subject="ERROR in Gratia-APEL svn updates process: $PGM on $(hostname -f)" ;;
  esac
  to_mail=weigand@fnal.gov
  cc_mail=""
  $mailprocess -s "$subject" $cc_mail $to_mail <<EOF;rtn=$?
$(cat $tmpfile)
EOF
}
#### MAIN #######################################################
PGM=$(basename $0)
svnsrc=/home/weigand/cdcvs/gratia/interfaces/ssm.apel-lcg
cfg=/etc/gratia-apel/lcg.conf
tmpfile=/tmp/$PGM.log
>$tmpfile
svn_updates_required=0

validate
check_webapps
check_apel_updates
check_reportable_sites
check_reportable_sites_history
check_reportable_vos
check_source
check_svn
send_email
exit 0


