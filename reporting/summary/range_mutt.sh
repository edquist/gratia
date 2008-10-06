#!/usr/bin/env bash

# space separated list of mail recipients
PROD_MAILTO="osg-accounting-info@fnal.gov"
PROD_USER_MAILTO="osg-user-accounting-info@opensciencegrid.org"
MAILTO="pcanal@fnal.gov"
USER_MAILTO=$MAILTO
WEBLOC="http://gratia-osg.fnal.gov:8880/gratia-reporting"
SUM_WEBLOC="http://gratia-osg.fnal.gov:8884/gratia-reporting"

ExtraHeader=
ExtraArgs=--daily
(( mailOverride = 0 ))
(( production = 0 ))

while test "x$1" != "x"; do
   if [ "$1" == "--help" ]; then 
	echo "usage: $0 [--dry-run] [--debug] [--mail email] [--draft] [--production] [quoted_string_representing_starting_date (as accepted by date -d)]"
	exit 1
   elif [ "$1" == "--debug" ]; then
	debug=yes
	shift
   elif [ "$1" == "--dry-run" ]; then
	dryrun=yes
	shift
   elif [ "$1" == "--draft" ]; then
        ExtraHeader="[Draft] ${ExtraHeader}"
	MAILTO=$PROD_MAILTO
        USER_MAILTO=$PROD_USER_MAILTO
        (( production = 1 ))
	shift
   elif [ "$1" == "--production" ]; then
	MAILTO=$PROD_MAILTO
        USER_MAILTO=$PROD_USER_MAILTO
        (( production = 1 ))
	shift
   elif [ "$1" == "--mail" ]; then
	MAILTO=$2
        USER_MAILTO=$2
        (( mailOverride = 1 ))
	shift
	shift
   elif [ "$1" == "--weekly" ]; then
    ExtraArgs=$1
    ExtraHeader="${ExtraHeader}Weekly "
    shift
   elif [ "$1" == "--monthly" ]; then
    ExtraArgs=$1
    ExtraHeader="${ExtraHeader}Monthly "
    shift
   else 
    date_arg=$1
	shift
   fi
done

when=$(date -d "${date_arg:-yesterday}" +"%d %B %Y")
whenarg=$(date -d "${date_arg:-yesterday}" +"%Y/%m/%d")

MAIL_MSG="${ExtraHeader}Report from the job level Gratia db for $when"
SUM_MAIL_MSG="${ExtraHeader}Report from the daily summary Gratia db for $when"
STATUS_MAIL_MSG="${ExtraHeader}Job Success Rate for $when (from the job level Gratia db)"
REPORTING_MAIL_MSG="${ExtraHeader}Summary on how sites are reporting to Gratia for $when"
LONGJOBS_MAIL_MSG="${ExtraHeader}Report of jobs longer than 7 days for $when"
USER_MAIL_MSG="${ExtraHeader}Report by user for $when"

# Transfer the file now
WORK_DIR=workdir.${RANDOM}

mkdir $WORK_DIR

function sendto {
    cmd=$1
    rep_args=$2
    txtfile=$3.txt
    csvfile=$3.csv
    subject="$4"
    to="$5"

    local whenarg="${ExtraArgs#--}"
    whenarg=${whenarg:-all}

    if (( $mailOverride == 0 )) && (( $production > 0 )); then
      newto=$(sed -ne 's/^[ 	]*'"`basename $cmd`"'[ 	]\{1,\}'"${ExtraArgs#--}"'[ 	]\{1,\}\(.*\)$/\1/p' \
              reportMail.config | sed -e 's/\b\default\b/'"$to"'/' | head -1) 
      to=${newto:-$to}
    fi
    echo "See $WEBLOC for more information" > $txtfile
    echo >> $txtfile
    eval $1 --output=text $rep_args >> $txtfile

    echo "For more information see:,$WEBLOC" > $csvfile
    echo >> $csvfile
    eval $1 --output=csv $rep_args >>  $csvfile
    
    if [ "$dryrun" != "yes" ]; then 
       mutt -F ./muttrc -a $csvfile -s "$subject" $to < $txtfile
    else 
       echo mutt -F ./muttrc -a $csvfile -s "$subject" $to < $txtfile
    fi
   
}


sendto ./range "$ExtraArgs $whenarg" ${WORK_DIR}/report "$MAIL_MSG" $MAILTO
sendto ./reporting "$ExtraArgs $whenarg" ${WORK_DIR}/report "$REPORTING_MAIL_MSG" $MAILTO
sendto ./longjobs "$ExtraArgs $whenarg" ${WORK_DIR}/report "$LONGJOBS_MAIL_MSG" $MAILTO
sendto ./usersreport "$ExtraArgs $whenarg" ${WORK_DIR}/report "$USER_MAIL_MSG" $MAILTO
sendto ./efficiency "$ExtraArgs $whenarg" ${WORK_DIR}/report "${ExtraHeader}OSG Efficiency by Site and VO for $when" $MAILTO
sendto ./voefficiency "$ExtraArgs $whenarg" ${WORK_DIR}/report "${ExtraHeader}OSG Efficiency by VO for $when" $MAILTO
sendto ./gradedefficiency "$ExtraArgs $whenarg" ${WORK_DIR}/report "${ExtraHeader}OSG Efficiency by VO by time period for $when" $MAILTO


sendto ./usersitereport "$ExtraArgs $whenarg" ${WORK_DIR}/report "${ExtraHeader}Report by user by site for $when" $USER_MAILTO

if [ "$debug" != "yes" ]; then 
   rm -rf $WORK_DIR
fi

exit 1
