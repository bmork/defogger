#!/bin/sh
VENDOR="Alphanetworks"
HWBOARD="r8000lh"
HWVERSION="1.0.0"
OEM="D-Link"
MODEL=@@MODEL@@
PRODUCT="Internet Camera"
VERSION=@@VERSION@@
PACKAGE=""
WIRELESS_MODULE="RTL8723BU"
DESCRIPT="An alternative opt."

showBrief() {
	echo -n "\

This updates the userdata partition only.
It will update following, ...
	1. /opt file system
"
}

MD5SUM=@@MD5SUM@@

MECH_SIGN="QPAT"
MECH_VERSION="1.0"
MECH_APP="doUpdate"

showUsage() {
	echo "Usage: $0 [ info | brief | exam | update ]" 1>&2
}

exam() {
	rm -f /tmp/update/md5
	for i in $(cat /tmp/update/certificate.info | grep Contents | cut -d":" -f2); do
		echo $(tar xOf "$1" $i.aes | openssl aes-128-cbc -kfile /tmp/update/aes.key -nosalt -d | md5sum - | cut -d' ' -f1 ) >> /tmp/update/md5
	done
	md5sum=$(md5sum /tmp/update/md5 | cut -d' ' -f1)
	[ "$MD5SUM" = "$md5sum" ] && return 0
	
	echo "md5sum failed" 1>&2
	return 1
}

self="$0"
action=$1
enc_bin=$2
end=$3

if [ "$action" = "" ] || [ "$end" != "" ]; then
	showUsage
	exit 1
fi

if [ "$action" = "info" ]; then
	echo -n "\
MECH_SIGN=\"$MECH_SIGN\"
MECH_VERSION=\"$MECH_VERSION\"
MECH_APP=\"$MECH_APP\"
HWBOARD=\"$HWBOARD\"
HWVERSION=\"$HWVERSION\"
MODEL=\"$MODEL\"
PRODUCT=\"$PRODUCT\"
OEM=\"$OEM\"
VENDOR=\"$VENDOR\"
VERSION=\"$VERSION\"
DESCRIPT=\"$DESCRIPT\"
PACKAGE=\"$PACKAGE\"
"
	exit 0
fi

if [ "$action" = "brief" ]; then
	showBrief
	exit 0
fi

if [ "$action" = "exam" ]; then
	exam "$enc_bin" || exit 1
	exit 0
fi

if [ "$action" = "update" ]; then
	/etc/rc.d/init.d/services.sh stop > /dev/null 2> /dev/null
	exam "$enc_bin" || { /etc/rc.d/init.d/services.sh start > /dev/null 2> /dev/null && exit 1; }

	userdata="/dev/$(grep '"userdata"' /proc/mtd |cut -f1 -d:)"
	size=$(grep '"userdata"' /proc/mtd |cut -f2 -d\ )
	blocksize=$(grep '"userdata"' /proc/mtd |cut -f3 -d\ )
	blocks=$(( 0x${size} / 0x${blocksize} ))
	[ -z "$userdata" ] ||  [ -z "$size" ] ||  [ -z "$blocksize" ] || [ -z "$blocks" ] && exit 1
	/usr/bin/flash_erase "$userdata" 0 ${blocks}
	tar xOf "$enc_bin" update.aes | openssl aes-128-cbc -k `cat /tmp/update/aes.key` -nosalt -d | dd bs=$((0x${blocksize})) of="$userdata" > /dev/null 2> /dev/null || exit 1

	{(sleep 5 && reboot) > /dev/null 2> /dev/null &}
	exit 0
fi

# unknown action
showUsage && exit 1
