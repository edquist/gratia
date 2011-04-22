#!/bin/sh

package=$1
version=$2
file=$3

cat > /var/tmp/versions.$$.java <<EOF 
package net.sf.gratia.$package;
public class Versions { 
public static final String PackageVersionString = "$version"; 
}; 
EOF

if [ ! -e $file ] ;  then 
   mv /var/tmp/versions.$$.java $file
else 
   diff $file /var/tmp/versions.$$.java > /dev/null
   result=$?
   if [ $result -eq 0 ] ; then 
      rm /var/tmp/versions.$$.java
   else 
      mv /var/tmp/versions.$$.java $file
   fi  
fi
