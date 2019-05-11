# D-Link DCS-8000LH

These are random notes descibing how I changed my D-Link DCS-8000LH
from a cloud camera to a locally managed IP camera, streaming H.264
MPEG-TS over HTTP and HTTPS. Some of the tools and ideas might work
for other cameras too, given some model specific adaptation.

Complete defogging requires modifying one of the file systems in the
camera.  This implies a slight risk of ending up with a brick. You
have now been warned...

This is tested and developed on firmware versions v2.01.03 and
v2.02.02 only.  The final complete procedure has only been tested with
v2.02.02. It should work fine with v2.01.03 and other versions, in
theory, but could fail like anything untested.  Please let me know if
you have an original v2.01.03 firmware update from D-Link, or any
other version for that matter, or know where firmware updates can be
downloaded.

The v2.02.02 update is available from
https://mydlinkmpfw.auto.mydlink.com/DCS-8000LH/DCS-8000LH_Ax_v2.02.02_3014.bin
at the time of writing. But I assume this link stops working as soon
as there is a newer version available.


## Problem

Got a new D-Link DCS-8000LH with firmware version 2.01.03 from factory.
This firmware is locked to the "mydlink" app/cloud service.  It does
not provide a local NIPCA compatible HTTP API or similar, and it does
not stream video over HTTP, HTTPS or RTSP.

Additionally, there is no way to downgrade the firmware.  In fact,
there is no documented way to install any firmware image at all,
except trusting the "mydlink" cloud service to do it for you.


## Solution

#### Primary goals achieved:

* configuration of network and admin password via Bluetooth LE, without
  registering with D-Link or using the "mydlink" app at all
* streaming MPEG-TS directly from camera over HTTP and HTTPS
* HTTP API based configuration of most settings, like LED, nightmode, etc

#### And some extra goodies which came for free

* Firmware upgrades and downgrades via HTTP API
* telnet server with a root account (admin/PIN Code)
* easy access to serial console, using the same root account
* running arbitrary commands on the camera using Bluetooth

Read on for all the gory details...


### Requirements

 * a Linux PC with a Bluetooth controller
 * python3 with the **bluepy** library: https://ianharvey.github.io/bluepy-doc/index.html
 * WiFi network with WPA2-PSK and a known password
 * mksquashfs from the squashfs-tools package
 * a tftp server or web server accepting file uploads (for backups)
 * guts :-)

Most recent Linux distros will probably do. The bluepy library can be
installed using pip if it is not available as a distro package.  Other
types of WiFi networks might work, but has not been tested with the
provided tools.  The squashfs-tools are only necessary if you want to
rebuild the "mydlink" alternative file system.  I assume you can even
run the tools without installing Linux, by using a Linux "Live"
CD/DVD/USB stick.

This was developed and tested on Debian Buster.
 


### Camera configuration using the Bluetooth LE GATT API

The "mydlink" app uses Bluetooth LE for camera setup, authenticated by
the camera pincode.  This repo includes an alternative python script
with a few extra goodies, but needing a better name:
**dcs8000lh-configure.py**.

(Why not an Android app?  Because it would take me much more time to
write. Should be fairly easy to do though, for anyone with enough
interest.  You can find all the necessary protocol details here and in
the python code. Please let me know if you are interested)

The script does not support scanning for the simple reason that this
would require root access for not real gain.  You have to provide the
**PIN Code** from the camera label anyway.  Reading the **MAC** as
well is simple enough.

The command line *address* paramenter should be formatted as
**01:23:45:67:89:AB**, and not like the **0123456789AB** format
printed on the label.

Current script help text at the time of writing shows what the script
can do:

```
$ ./dcs8000lh-configure.py -h
usage: dcs8000lh-configure.py [-h] [--essid ESSID] [--wifipw WIFIPW]
                              [--survey] [--netconf] [--sysinfo]
                              [--command COMMAND] [--telnetd] [--lighttpd]
                              [--unsignedfw] [--attrs] [-V]
                              address pincode

IPCam Bluetooth configuration tool.

positional arguments:
  address            IPCam Bluetooth MAC address (01:23:45:67:89:AB)
  pincode            IPCam PIN Code (6 digits)

optional arguments:
  -h, --help         show this help message and exit
  --essid ESSID      Connect to this WiFi network
  --wifipw WIFIPW    Password for ESSID
  --survey           List WiFi networks seen by the IPCam
  --netconf          Print current network configuration
  --sysinfo          Dump system configuration
  --command COMMAND  Run command on IPCam
  --telnetd          Start telnet server on IPCam
  --lighttpd         Start web server on IPCam
  --unsignedfw       Allow unsigned firmware
  --attrs            Dump IPCam GATT characteristics
  -V, --version      show program's version number and exit
```


#### Real session excample after a clean upgrade to firmware v2.02.02, followed by factory reset 

1. Start by making sure the camera can see our WiFi network.  This
   also verifies that we can connect and authenticate against the
   Bluetooth LE IPCam service, without making any changes to any
   camera settings:

```
$ ./dcs8000lh-configure.py B0:C5:54:AA:BB:CC 123456 --survey
Connecting to B0:C5:54:AA:BB:CC...
Verifying IPCam service
Connected to 'DCS-8000LH-BBCC'
DCS-8000LH-BBCC is scanning for WiFi networks...
{'I': 'AirLink126FD4', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '47'}
{'I': 'Antiboks', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '73'}
{'I': 'ASV17', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '47'}
{'I': 'ASV17-dlink', 'M': '0', 'C': '6', 'S': '4', 'E': '2', 'P': '57'}
{'I': 'DIRECT-33-HP%20ENVY%205000%20series', 'M': '0', 'C': '1', 'S': '4', 'E': '2', 'P': '46'}
{'I': 'fjorde123', 'M': '0', 'C': '1', 'S': '4', 'E': '2', 'P': '55'}
{'I': 'JOJ', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '48'}
{'I': 'Kjellerbod', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '75'}
{'I': 'Landskap_24', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '46'}
{'I': 'mgmt', 'M': '0', 'C': '1', 'S': '4', 'E': '2', 'P': '72'}
{'I': 'Rindedal', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '68'}
{'I': 'risikovirus', 'M': '0', 'C': '1', 'S': '4', 'E': '2', 'P': '45'}
{'I': 'risikovirus%20WIFI', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '45'}
{'I': 'Stavik2014', 'M': '0', 'C': '6', 'S': '4', 'E': '2', 'P': '47'}
{'I': 'TomterNett1', 'M': '0', 'C': '6', 'S': '4', 'E': '2', 'P': '44'}
{'I': 'VIF', 'M': '0', 'C': '11', 'S': '4', 'E': '2', 'P': '47'}
Done.
```

2. We're going to use the 'Kjellerbod' network, so that looks good.
   Select it and give the associated WiFi password to the camera:

```
$ ./dcs8000lh-configure.py B0:C5:54:AA:BB:CC 123456 --essid Kjellerbod --wifipw redacted
Connecting to B0:C5:54:AA:BB:CC...
Verifying IPCam service
Connected to 'DCS-8000LH-BBCC'
DCS-8000LH-BBCC is scanning for WiFi networks...
Will configure: M=0;I=Kjellerbod;S=4;E=2;K=redacted
Done.
```

3. Verify that the camera connected to the Wifi network and got an
   address.  If not, go back and try again, making sure you are using
   the correct WiFi password:

```
$ ./dcs8000lh-configure.py B0:C5:54:AA:BB:CC 123456 --netconf
Connecting to B0:C5:54:AA:BB:CC...
Verifying IPCam service
Connected to 'DCS-8000LH-BBCC'
wifi link is Up
wifi config: {'M': '0', 'I': 'Kjellerbod', 'S': '4', 'E': '2'}
ip config: {'I': '192.168.2.37', 'N': '255.255.255.0', 'G': '192.168.2.1', 'D': '148.122.16.253'}
Done.
```


WARNING: You must make a backup of your device at this point if you
haven't done so already.  See the backup section below.  I only skipped it
in this example because I already had made a complete backup.



4. We need HTTP NIPCA API for the remaining tasks, so temporarily
   start lighttpd on the camera:
   
```
$ ./dcs8000lh-configure.py B0:C5:54:AA:BB:CC 123456 --lighttpd
Connecting to B0:C5:54:AA:BB:CC...
Verifying IPCam service
Connected to 'DCS-8000LH-BBCC'
Attempting to run '[ $(tdb get HTTPServer Enable_byte) -eq 1 ] || tdb set HTTPServer Enable_byte=1' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Attempting to run '/etc/rc.d/init.d/extra_lighttpd.sh start' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Done.
```

Note that this implicitly changes a couple of settings which are
stored in the "db" NVRAM partition, and therefore will persist until
the next factory reset:
 *  extra_lighttpd.sh will exit without doing anything unless
    **HTTPServer Enable** is set
 * the admin password is set both because we're abusing that BLE
   request, and because we need it for the HTTP API access.  The
   script only supports setting the password to the **PIN Code**.
   
(This password restriction is because I'm lazy - there is nothing in
the camera or protocol preventing the password from being set to
something else. But the script would then need the new password as
an additional input parameter for most commands)


5. Disable firmware signature verification. Only firmwares signed by
   D-Link are accepted by default. This feature can be disabled by
   changing a variable in the "db" NVRAM partition:

```
$ ./dcs8000lh-configure.py B0:C5:54:AA:BB:CC 123456  --unsignedfw 
Connecting to B0:C5:54:AA:BB:CC...
Verifying IPCam service
Connected to 'DCS-8000LH-BBCC'
Attempting to run 'tdb set SecureFW _TrustLevel_byte=0' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Done.
```

6. The final step is the dangerous one.  It replaces the file system
   on the **userdata** partition with our home cooked one.  The D-Link
   firmware uses this partition exclusively for the "mydlink" cloud
   tools, which we don't need.  The rest of the system is not touched
   by our firmware update.  The camera will therefore run exactly the
   same kernel and rootfs as before the update, whatever version they
   were.  I.e., the firmware version does not change - only the
   "mydlink" version.
```
$ curl --http1.0 -u admin:123456 --form upload=@fw.tar http://192.168.2.37/config/firmwareupgrade.cgi
upgrade=ok
```

The **firmwareupgrade.cgi** script running in the camera isn't much
smarter than the rest of the system, so there are a few important
things to note here.  These are found by trial-and-error:
 * HTTP/1.1 might not work - the firmwareupgrade.cgi script does not support **100 Continue** AFAICS
 * The firmware update image should be provided as a **file** input field from a form
 * The field name must be **upload**.

Using the exact curl command as provided above, replcaing the PIN Code
and IP address with the correct vaules for your camera, should
work. Anything else might not.

The camera will reboot automatically after a sucessful upgrade.  But
from now both telnetd and lighttpd is automatically started on every
boot. And there will also be an **admin:PIN Code** account for both.


#### unexpected errors during firmware update via HTTP

The camera must be manually rebooted by removing power or pressing
reset if the firmware upgrade fails for any reason. The
**firmwareupgrade.cgi** script stops most processes, inluding the
Bluetooth handler, and fails to restart them on errors. 

There will be no permanent harm if the upload fails.  But note that
you have to repeat the **--lighttpd** step after rebooting the camera,
before you can retry. It does not start automatically until we've
installed our modified "mydlink" alternative.

The contents of the fw.tar file must obviously be a valid, encrypted,
firmware update intended for the specified hardware.  It must also be
signed.  But the signing key can be unknown to the camera provided the
previous **--unsignedfw** request above was successful.

The **Makefile** provided here shows how to build a valid firmware
update, but for the DCS-8000LH only!  It does not support any other
model. It will create a new throwaway signing key if it canæt find a
real one, and include the associated public key in the archive in case
you want to verify the signature manually.

Note that the encryption key might be model specific.  I do not know
this as I have no other model to look at.  Please let me know if you
have any information on this topic.

The encryption key is part ot the **pib** partition, and can be
read from a shell using
```
pibinfo PriKey
```
Or you can simply look at your partition backup.  The key is stored as
a plain text *RSA PRIVATE KEY* PEM blob, so it is easy to spot.


### Backup

Create a backup of everything *before* you mess up.  Restoring will be
hard anyway, so don't rely on that.  But you can forget about
restoring at all unless you have a backup, so make it anyway.

Note that the **pib** partition contains data which are specific to
**your** camera, and cannot be restored from any other source!  This
includes
 * model number
 * hardware revision
 * mac address
 * feature bits
 * private keys, pincode and passwords

Well, OK, we can restore most of the **pib** using information from
the camera label, but it's better to avoid having to do that...

A backup is also useful for analyzing the file systems offline.

Making a backup without networking is inconvenient, so setup
networking first. In theory, you could dump the flash to the serial
console. But this would be very time consuming and tiresome.

The D-Link firmware provides a selection of network file transfer
tools. Pick anyone you like:
 * tftp
 * wget
 * curl
 * ...and probably more


I've been using tftp for my backups because it is simple. You'll
obviously need a tftp server for this. Google for instructions on
setting that up.  You could alternatively set up a web server and use
wget or curl to post the files there, but this is more complx to set
up IMHO.

Here is one example of how to enable temporary telnet access and
copying all camera flash partitions to a tftp server:

```
$ ./dcs8000lh-configure.py B0:C5:54:AA:BB:CC 123456 --telnetd
Connecting to B0:C5:54:AA:BB:CC...
Verifying IPCam service
Connected to 'DCS-8000LH-BBCC'
Adding the 'admin' user as an alias for 'root'
Attempting to run 'grep -Eq ^admin: /etc/passwd||echo admin:x:0:0::/:/bin/sh >>/etc/passwd' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Setting the 'admin' user password to '123456'
Attempting to run 'grep -Eq ^admin:x: /etc/passwd&&echo admin:123456|chpasswd' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Starting telnetd
Attempting to run 'pidof telnetd||telnetd' on DCS-8000LH-BBCC by abusing the 'set admin password' request
 

Attempting to run '[ $(tdb get HTTPServer Enable_byte) -eq 1 ] || tdb set HTTPServer Enable_byte=1' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Attempting to run '/etc/rc.d/init.d/extra_lighttpd.sh start' on DCS-8000LH-BBCC by abusing the 'set admin password' request
Done.


$ telnet 192.168.2.37
Trying 192.168.2.37...
Connected to 192.168.2.37.
Escape character is '^]'.
localhost login: admin
Password: 


BusyBox v1.22.1 (2019-02-14 17:06:35 CST) built-in shell (ash)
Enter 'help' for a list of built-in commands.


# for i in 0 1 2 3 4 5 6 7 8; do tftp -l /dev/mtd${i}ro -r mtd$i -p 192.168.2.1; done`
```

Change 192.168.2.37 to the address of your camera and 192.168.2.1 to
the address of your tftp server. Note that most tftp servers require
existing and writable destination files. Refer to your tftp server docs
for details.



## All the gory details


### Restoring original D-Link firmware

The D-Link firmware, including the mydlink tools in the **userdata**
partition, can be restored by doing a manual firmware upgrade
providing a firmware update from D-Link.  Real example, going back to
v2.02.02:

```
$ curl --http1.0 -u admin:123456 --form upload=@DCS-8000LH_Ax_v2.02.02_3014.bin http://192.168.2.37/config/firmwareupgrade.cgi
curl: (52) Empty reply from server
```

I don't know why I got that *Empty reply* warning instead of the
expected *upgrade=ok*, but update went fine so I guess it can safely
be ignored. Might be a side effect of rewriting the root file system,
which the firmwareupgrade.cgi script is running from.


### Serial console

Entirely optional.  The defogging procedure does not require console
access, but it can be very useful when debugging problems related to
network configuration etc.

There is a 4 hole female header with 2 mm spacing in the bottom of the
camera. This header is easily accessible without opening the case at
all. But you will need to remove the bottom label to find it. Take a
picure, or save the information somewhere else, first, in case you
make the label unreadable.

Mate with a 3 (or 4) pin male 2 mm connector, or use sufficiently
solid wires.  The pins need to be 6-10 mm long.

The pinout seen from center to edge of camera is:

 | GND | RX | TX |  3.3V |

You obviously need a 3.3V TTL adapter for this, Look at for example
at the generic OpenWrt console instructions if you need guidance.

The serial port parameters are 57600 8N1


### U-Boot

My DCS-8000LH came with this boot loader:

`U-Boot 2014.01-rc2-V1.1 (Jun 06 2018 - 03:44:37)`

But it is patched/configured to require a password for access to the
U-Boot prompt. Fortunately, D-Link makes the password readily
available in their GPL package :-) It is found in the file
`DCS-8000LH-GPL/configs/gpl_defconfig`:

`ALPHA_FEATURES_UBOOT_LOGIN_PASSWORD="alpha168"`

Enter **alpha168** password when you see

`Press ESC to abort autoboot in 3 seconds`

and you'll get a `rlxboot#` prompt, with access to these U-Boot commands :

```
rlxboot# ?
?       - alias for 'help'
base    - print or set address offset
bootm   - boot application image from memory
bootp   - boot image via network using BOOTP/TFTP protocol
cmp     - memory compare
coninfo - print console devices and information
cp      - memory copy
crc32   - checksum calculation
echo    - echo args to console
editenv - edit environment variable
efuse   - efuse readall | read addr
env     - environment handling commands
fephy   - fephy read/write
go      - start application at address 'addr'
help    - print command description/usage
imxtract- extract a part of a multi-image
loadb   - load binary file over serial line (kermit mode)
loadx   - load binary file over serial line (xmodem mode)
loady   - load binary file over serial line (ymodem mode)
loop    - infinite loop on address range
md      - memory display
mm      - memory modify (auto-incrementing address)
mw      - memory write (fill)
nm      - memory modify (constant address)
ping    - send ICMP ECHO_REQUEST to network host
printenv- print environment variables
reset   - Perform RESET of the CPU
setenv  - set environment variables
setethaddr- set eth address
setipaddr- set ip address
sf      - SPI flash sub-system
source  - run script from memory
tftpboot- boot image via network using TFTP protocol
tftpput - TFTP put command, for uploading files to a server
tftpsrv - act as a TFTP server and boot the first received file
update  - update image
version - print monitor, compiler and linker version
```

Using the boot loader for image manipulation will be hard though,
since the camera has no ethernet, USB or removable flash and the boot
loader has no WiFi driver.  It is probably possible to load an image
over serial, but I don't have the patience for that...

The environment is fixed and pretty clean:
```
rlxboot# printenv
=3
addmisc=setenv bootargs ${bootargs}console=ttyS0,${baudrate}panic=1
baudrate=57600
bootaddr=(0xBC000000 + 0x1e0000)
bootargs=console=ttyS1,57600 root=/dev/mtdblock8 rts_hconf.hconf_mtd_idx=0 mtdparts=m25p80:256k(boot),128k(pib),1024k(userdata),128k(db),128k(log),128k(dbbackup),128k(logbackup),3072k(kernel),11264k(rootfs)
bootcmd=bootm 0xbc1e0000
bootfile=/vmlinux.img
ethact=r8168#0
ethaddr=00:00:00:00:00:00
load=tftp 80500000 ${u-boot}
loadaddr=0x82000000
stderr=serial
stdin=serial
stdout=serial

Environment size: 533/131068 bytes
```

So we can get ourselves a root shell:


```
rlxboot# setenv bootargs ${bootargs} init=/bin/sh
rlxboot# ${bootcmd}
```

Nothing is mounted or started since /sbin/init is skipped altogether
in this case.  Not even /sys and /proc.  We can emulate a semi-normal
system by running

`/etc/rc.d/rcS` 

as the first command.  And then run for example

`telnetd -l /bin/sh`

to enable temporary passwordless telnet into the camera instead of/in
addition to the serial console. This is futile unless you have
networking of course. I will not go into details on how to do that
from the shell. Use the much simpler Bluetooth procedure described
above. Or the "mydlink" app if you prefer.




### Partitions

The D-Link DCS-8000LH partitions are:
```
# cat /proc/mtd 
dev:    size   erasesize  name
mtd0: 00040000 00010000 "boot"
mtd1: 00020000 00010000 "pib"
mtd2: 00100000 00010000 "userdata"
mtd3: 00020000 00010000 "db"
mtd4: 00020000 00010000 "log"
mtd5: 00020000 00010000 "dbbackup"
mtd6: 00020000 00010000 "logbackup"
mtd7: 00300000 00010000 "kernel"
mtd8: 00b00000 00010000 "rootfs"
```
Or as seen by the driver with start and end addresses:

```
9 cmdlinepart partitions found on MTD device m25p80
Creating 9 MTD partitions on "m25p80":
0x000000000000-0x000000040000 : "boot"
0x000000040000-0x000000060000 : "pib"
0x000000060000-0x000000160000 : "userdata"
0x000000160000-0x000000180000 : "db"
0x000000180000-0x0000001a0000 : "log"
0x0000001a0000-0x0000001c0000 : "dbbackup"
0x0000001c0000-0x0000001e0000 : "logbackup"
0x0000001e0000-0x0000004e0000 : "kernel"
0x0000004e0000-0x000000fe0000 : "rootfs"
```

Partition usage:

 | number | name        | start    | end      | size     | fstype   | contents          |
 | 0      | "boot"      | 0x000000 | 0x040000 | 0x40000  | boot     | U-Boot            |
 | 1      | "pib"       | 0x040000 | 0x060000 | 0x20000  | raw      | device info       |
 | 2      | "userdata"  | 0x060000 | 0x160000 | 0x100000 | squashfs | mydlink (/opt)    |
 | 3      | "db"        | 0x160000 | 0x180000 | 0x20000  | tar.gz   | non-volatile data |
 | 4      | "log"       | 0x180000 | 0x1a0000 | 0x20000  | raw?     | empty             |
 | 5      | "dbbackup"  | 0x1a0000 | 0x1c0000 | 0x20000  | tar.gz   | copy of "db"      |
 | 6      | "logbackup" | 0x1c0000 | 0x1e0000 | 0x20000  | raw?     | empty             |
 | 7      | "kernel"    | 0x1e0000 | 0x4e0000 | 0x300000 | uImage   | Linux 3.10        |
 | 8      | "rootfs"    | 0x4e0000 | 0xfe0000 | 0xb00000 | squashfs | rootfs (/)        |


The D-Link firmware updates I have looked at will replace the
"userdata", "kernel" and "rootfs" partitions, but leave other
partitions unchanged. I imagine that the "boot" partition might be
upgraded too if deemed necessary by D-Link. But it was not touched
when going from 2.01.03 to 2.02.02.

The "log" and "logbackup" appear to be currently unused.  But I am
reluctant trusting this, given their names.  I guess they could be
cleaned and overwritten anytime.  They are too small to be very useful
anyway.  You can't put any writable file system om them with only two
erase blocks.


### Backing up dynamic data

This is not necessary for system operation as any non-volatile data is
saved in the **db** partition anyway.  But it can still be useful to
have a copy of the system state for offline studying, so I also like
to save a working copy of /tmp:
```
tar zcvf /tmp/tmp.tgz /tmp/
tftp -l /tmp/tmp.tgz -r tmp.tgz -p 192.168.2.1
```


### Why can we run the NIPCA webserver before we modify the firmware?

D-Link left all the webserver parts in the firmware, including all the
NIPCA CGI tools. The only change they made was disabling the startup
script.

The webserver can be enabled and started manually from the shell by
running:

```
tdb set HTTPServer Enable_byte=1
/etc/rc.d/init.d/extra_lighttpd.sh start
```

This is precisely what our Bluetooth tool does when it is called with
the **--lighttpd** option.

The `HTTPServer Enable_byte` is persistent, so setting is only
necessary once. Unless you do a factory reset.


### The "userdata" file system

The **userdata** you backed up as **mtd2** contains a xz compressed
squasfs file system, with most of the mydlink cloud tools. The file
system can be unpacked on a Linux system using unsquashfs:
```
$ unsquashfs mtd2
Parallel unsquashfs: Using 4 processors
15 inodes (22 blocks) to write

[=============================================================================================================================================================================================================|] 22/22 100%

created 12 files
created 1 directories
created 3 symlinks
created 0 devices
created 0 fifos
$ ls -la squashfs-root/
total 1156
drwxr-xr-x  2 bjorn bjorn    340 Feb 14 10:58 .
drwxrwxrwt 41 root  root    2280 May 13 15:13 ..
-rwxr-xr-x  1 bjorn bjorn  13184 Feb 14 10:58 ca-refresh
-rwxr-xr-x  1 bjorn bjorn 273692 Feb 14 10:58 cda
lrwxrwxrwx  1 bjorn bjorn      9 May 13 15:13 cert -> /tmp/cert
-rwxr-xr-x  1 bjorn bjorn   5991 Feb 14 10:58 client-ca.crt.pem
lrwxrwxrwx  1 bjorn bjorn      7 May 13 15:13 config -> /tmp/db
-rwxr-xr-x  1 bjorn bjorn 436428 Feb 14 10:58 da_adaptor
-rwxr-xr-x  1 bjorn bjorn      4 Feb 14 10:58 dcp_version
-rwxr-xr-x  1 bjorn bjorn    814 Feb 14 10:58 device.cfg
lrwxrwxrwx  1 bjorn bjorn     17 May 13 15:13 lib -> /var/libevent/lib
-rwxr-xr-x  1 bjorn bjorn      5 Feb 14 10:58 m2m
-rwxr-xr-x  1 bjorn bjorn   6220 Feb 14 10:58 mydlink_watchdog.sh
-rwxr-xr-x  1 bjorn bjorn   1034 Feb 14 10:58 opt.local
-rwxr-xr-x  1 bjorn bjorn 171828 Feb 14 10:58 sa
-rwxr-xr-x  1 bjorn bjorn 242028 Feb 14 10:58 strmsvr
-rwxr-xr-x  1 bjorn bjorn     10 Feb 14 10:58 version
```

The primary entry point here is the **opt.local** init-script.  This
is also the only required file.  The **version** file is read by the
Bluetooth API, and reported as the mydlink version, which makes it
useful for verifying a modified camera.  Our alternate **userdata**
file system contains only these two files. But one could imagine
including a number of other useful tools, like tcpdump, a ssh server etc.

It is also possible to keep all the D-Link files, if that's
wanted. The original **opt.local** script can be modified to leave
mydlink support running while still starting other features.  We could
even add our own non-volatile setting to choose one or the other, or
both, and making it a configuration thing. Fantasy is the only
limiting factor.

Repacking the files into a camera compatible squashfs file system:
```
mksquashfs squashfs-root mtd2.new -all-root -comp xz
```

Note that **xz** compression is required.  No other compression is
supported AFAIK.

There are simpler ways to write the new file system to the camera than
creating a firmware update package, if you just want to test it. One
example:

```
tftp -r mtd2.new -l /tmp/mtd2.new -g 192.168.2.1
cat /tmp/mtd2.new >/dev/mtdblock2 
```

But DON'T do that unless you both have a backup and know what you are
doing...

You should reboot the camera after doing this, unless you make sure
you stop any process running from the previous /opt system and remount
it properly.


### Using NIPCA to manage the camera

The local web server provides a direct camera management API, but not
a web GUI application. All API requests require authentication. We
have added a single admin user, using the pincode from the camera
label as passord.  More users can be adding if necessary, even by
using the API itself.

Google for the NIPCA reference spec, or look at the script names under
/var/www and try them out.  Some examples:

```
$ curl -u admin:123456 'http://192.168.2.37/config/datetime.cgi' 
method=1
timeserver=ntp1.dlink.com
timezone=1
utcdate=2019-05-09
utctime=13:25:14
date=2019-05-09
time=15:25:14
dstenable=yes
dstauto=yes
offset=01:00
starttime=3.2.0/02:00:00
stoptime=11.1.0/02:00:00

$ curl -u admin:123456 http://192.168.2.37/config/led.cgi?led=off
led=off
```
There are lots of settings which can be controlled using this API.


### Streaming video locally

The whole point of all this... We can now stream directly from the
camera using for example:

```
vlc https://192.168.2.37/video/mpegts.cgi
vlc https://192.168.2.37/video/flv.cgi
```

Again using the same admin/PIN Code user for authentication.



### Bluetooth LE GATT API

The Bluetooth service is in a "locked" mode by default. This is
controlled by the "Ble Mode" persistent setting stored in the **db**
partition. If true ("1"), then most of the Bluetooth commands are
rejected.  But changing the setting manually will not help much, since
the system automatically enter lock mode 180 seconds after the last
Bluetooth client disconnected.

The challenge -> response unlock method described below is much more
useful.


#### Converting the PIN Code to a Bluetooth unlock key

Most Bluetooth commands are rejected when locked.  Access to the full
Bluetooth API can be unlocked by using the PIN Code printed on the
camera label.  This code is not sent directly over the air
though. Instead it is combined with a random challenge.

Both the random challenge and the matching key are generated by the
application `sbin/gen_bt_config` on the camera side.  The key is
calculated by taking the first 16 bytes of the base64 encoded md5
digest of

 * model string + '-'  four last mac digits (or Bluetooth device name?)
 * PIN Code
 * challenge.

Note that this application depends on bluetooth libraries, which are
not in /lib. So we have to set LD\_LIBRARY\_PATH to run it manually:

```
# LD_LIBRARY_PATH=/var/bluetooth/lib sbin/gen_bt_config update_key_only
In main:182: modelStr = 'DCS-8000LH'
In main:183: mac = 'b0:c5:54:ab:cd:ef'
In update_ble_key:87: key data = 'DCS-8000LH-CDEF012345b2gaescrbldchnik'
```

I've slightly obfuscated my data here - the pincode in the above case
is `012345`, and the dynamically generated challenge is
`b2gaescrbldchnik`. The generated challenge and key are stored in
`/tmp/db/db.xml` and can be read directly from there:
```
# grep Key /tmp/db/db.xml |tail -2
<ChallengeKey type="3" content="b2gaescrbldchnik" />
<Key type="5" content="jrtY6nONQ5rV+2Ph" />
```

Or you can read them using the same tools the Bluetooth system uses:
```
# tdb get Ble ChallengeKey_ss
b2gaescrbldchnik
# mdb get ble_key
jrtY6nONQ5rV+2Ph
```

Yes, the D-Link code does actually use tdb for the first one and mdb
for the second.  I have absolutely no idea why,... It is possible to
read the key using tdb too:

```
# tdb get Ble Key_ss
jrtY6nONQ5rV+2Ph
```

Generating the same key by hand on a Linux system is simple:

```
$ echo -n 'DCS-8000LH-CDEF012345b2gaescrbldchnik' | md5sum | xxd -r -p | base64 | cut -c-16
jrtY6nONQ5rV+2Ph
```

#### Characteristic UUIDs

D-Link is using the GATT BlueZ example plugin, patching it to add
their camera specific endpoints.  This means that we can find all the
API "documentation" in the
`DCS-8000LH-GPL/package/bluez_utils/feature-patch/5.28/customized-mydlink.patch`
file in the GPL archive.

This defines a number of 16bit UUIDs with mostly nonsense names:
```
+#define IPCAM_UUID		0xD001
+#define A000_UUID		0xA000
+#define A001_UUID		0xA001
+#define A100_UUID		0xA100
+#define A101_UUID		0xA101
+#define A102_UUID		0xA102
+#define A103_UUID		0xA103
+#define A104_UUID		0xA104
+#define A200_UUID		0xA200
+#define A201_UUID		0xA201
+#define A300_UUID		0xA300
+#define A301_UUID		0xA301
+#define A302_UUID		0xA302
+#define A303_UUID		0xA303
+#define A304_UUID		0xA304
```


`IPCAM_UUID` is registered as the `GATT_PRIM_SVC_UUID`, which means
that it shows up as a primary GATT service we can look for when
looking for a supported camera.

The rest of the UUIDs are characteristics of this primary service. The
API is based on reading or writing these characteristics.


#### Data formatting

Both input and output parameters are sent as ascii strings using
key=value pairs joined by `;`, with an exception for the nested KV
pairs in the WiFi survey results.  All keys are single upper case
characters. Key names are somewhat reused, so the exact meaning depend
on the characteristic.

Values are either integers, including boolean 0/1, or some set of
ascii text.

Three real examples, read from 0xA001, 0xA200 and 0xA104:
```
M=1;C=b2gaescrbldchnik
N=DCS-8000LH;P=1;T=1557349762;Z=CET-1CEST,M3.5.0,M10.5.0/3;F=2.01.03;H=A1;M=B0C554ABCDEF;V=3.0.0-b71
I=192.168.2.37;N=255.255.255.0;G=192.168.2.1;D=148.122.16.253
```

#### Listing characteristics


The **gattool** Linux command line tool is useful for exploring
Bluetooth LE devices.  You can look for primary services and list
associated characteristics of a service:
```
[B0:C5:54:AA:BB:CC][LE]> primary 
attr handle: 0x0001, end grp handle: 0x0008 uuid: 00001800-0000-1000-8000-00805f9b34fb
attr handle: 0x0010, end grp handle: 0x0010 uuid: 00001801-0000-1000-8000-00805f9b34fb
attr handle: 0x0011, end grp handle: 0x002e uuid: 0000d001-0000-1000-8000-00805f9b34fb
[B0:C5:54:AA:BB:CC][LE]> characteristics 0x0011
handle: 0x0012, char properties: 0x12, char value handle: 0x0013, uuid: 0000a000-0000-1000-8000-00805f9b34fb
handle: 0x0015, char properties: 0x0a, char value handle: 0x0016, uuid: 0000a001-0000-1000-8000-00805f9b34fb
handle: 0x0017, char properties: 0x02, char value handle: 0x0018, uuid: 0000a100-0000-1000-8000-00805f9b34fb
handle: 0x0019, char properties: 0x0a, char value handle: 0x001a, uuid: 0000a101-0000-1000-8000-00805f9b34fb
handle: 0x001b, char properties: 0x08, char value handle: 0x001c, uuid: 0000a102-0000-1000-8000-00805f9b34fb
handle: 0x001d, char properties: 0x02, char value handle: 0x001e, uuid: 0000a103-0000-1000-8000-00805f9b34fb
handle: 0x001f, char properties: 0x02, char value handle: 0x0020, uuid: 0000a104-0000-1000-8000-00805f9b34fb
handle: 0x0021, char properties: 0x0a, char value handle: 0x0022, uuid: 0000a200-0000-1000-8000-00805f9b34fb
handle: 0x0023, char properties: 0x08, char value handle: 0x0024, uuid: 0000a201-0000-1000-8000-00805f9b34fb
handle: 0x0025, char properties: 0x0a, char value handle: 0x0026, uuid: 0000a300-0000-1000-8000-00805f9b34fb
handle: 0x0027, char properties: 0x02, char value handle: 0x0028, uuid: 0000a301-0000-1000-8000-00805f9b34fb
handle: 0x0029, char properties: 0x08, char value handle: 0x002a, uuid: 0000a302-0000-1000-8000-00805f9b34fb
handle: 0x002b, char properties: 0x08, char value handle: 0x002c, uuid: 0000a303-0000-1000-8000-00805f9b34fb
handle: 0x002d, char properties: 0x02, char value handle: 0x002e, uuid: 0000a304-0000-1000-8000-00805f9b34fb
```

It is also possible to read and write characteristics using this tool,
but this can be a bit cumbersome unless you are fluent in ASCII coding
;-)



#### Description of the IPCam characteristics


Guessing the meaning of each characteristic, based on the source code
and some trial and error:


 | UUID | op     | description     | format                                  | keys                                                                                                            |
 | A000 | read   | last status     | C=%d;A=%d;R=%d                          | C: uuid, A: mode, R: state                                                                                      |
 | A000 | notify | last status     | C=%d;A=%d;R=%d                          | C: uuid, A: mode, R: state                                                                                      |
 | A001 | read   | challenge       | M=%d;C=%s                               | M: opmode, C: challenge                                                                                         |
 | A001 | write  | auth            | M=%d;K=%s                               | M: opmode, K: key                                                                                               |
 | A100 | read   | wifi survey     | N=%d;P=%d;...                           |                                                                                                                 |
 | A101 | read   | wifi config     | M=%s;I=%s;S=%s;E=%s                     | M: opmode, I: essid, S: 4 , E: 2                                                                                |
 | A101 | write  | wifi config     | M=%s;I=%s;S=%s;E=%s;K=%s                | M: opmode, I: essid, S: 4 , E: 2, K: password                                                                   |
 | A102 | write  | wifi connect    | C=%d                                    | C: connect (0/1)                                                                                                |
 | A103 | read   | wifi status     | S=%d                                    | S: wifi link status (0,1,?)                                                                                     |
 | A104 | read   | ip config       | I=%s;N=%s;G=%s;D=%s                     | I: address, N: netmask, G: gateway, D: DNS-server                                                               |
 | A200 | read   | system info     | N=%s;P=%d;T=%d;Z=%s;F=%s;H=%s;M=%s;V=%s | N: devicename, P: haspin (0/1), T: time (unix epoch), Z: timezone, F: fwver, H: hwver, M: macaddr, V:mydlinkver |
 | A200 | write  | name and time   | N=%s;T=%d;Z=%s                          | N: devicename, T: time (unix epoch), Z: timezone                                                                |
 | A201 | write  | admin password  | P=%s;N=%s                               | P: current password, N: new password                                                                  |
 | A300 | read   | reg state       | G=%d                                    | G: registration state (0/1)                                                                                     |
 | A300 | write  | reg state       | G=%d                                    | G: registration state (0/1)                                                                                     |
 | A301 | read   | provisioning    | N=%s;T=%s;U=%s                          | N: username, T: footprint, U: portal                                                                            |
 | A302 | write  | restart mydlink | C=%d                                    | C: restart (0/1)                                                                                                |
 | A303 | write  | register        | S=%s;M=%s                               | S: , M:  (written to /tmp/mydlink/reg_info, and then kill -USR1 `pidof da_adaptor`)                             |
 | A304 | read   | register        | S=%d;E=%d                               | S: , E:  (cat /tmp/mydlink/reg_st)                                                                              |


The UUIDs from 0xA300 to 0xA304 are all related to the mydlink cloud
service, and therefore not of much use to us.  I haven't bothered
trying to figure out exactly how they are used.

We could in theory use the 0xA303 request which simply calls
**/opt/opt.local restart**.  But with the gaping 0xA201 hole,
allowing **any** command, there isn't much need for this one...

A few more details on the more complex characteristics:


##### A000

The only characteristic sent as notifications.  But it can also be
read directly for syncronous operations.

The value is the state to the last Bluetooth action:

	"C=%d;A=%d;R=%d", last_action_status.uuid, last_action_status.mode, last_action_status.state


##### A100

The wifi survey scan results are split in 128 byte "pages", where each
page starts with the total number of pages and the current page
number.  The characteristic value must be read as many times as the
given total.

For example, reading 3 pages:
```
[B0:C5:54:AA:BB:CC][LE]> char-read-hnd 0x0018
Characteristic value/descriptor: 4e 3d 33 3b 50 3d 31 3b 4c 3d 49 3d 41 6e 74 69 62 6f 6b 73 2c 4d 3d 30 2c 43 3d 36 2c 53 3d 34 2c 45 3d 32 2c 50 3d 36 32 26 4c 3d 49 3d 41 53 56 31 37 2c 4d 3d 30 2c 43 3d 31 31 2c 53 3d 34 2c 45 3d 32 2c 50 3d 34 36 26 4c 3d 49 3d 41 53 56 31 37 2d 64 6c 69 6e 6b 2c 4d 3d 30 2c 43 3d 36 2c 53 3d 34 2c 45 3d 32 2c 50 3d 36 38 26 4c 3d 49 3d 66 6a 6f 72 64 65 31 32 33 2c 4d 3d 30 
[B0:C5:54:AA:BB:CC][LE]> char-read-hnd 0x0018
Characteristic value/descriptor: 4e 3d 33 3b 50 3d 32 3b 2c 43 3d 31 2c 53 3d 34 2c 45 3d 32 2c 50 3d 35 38 26 4c 3d 49 3d 4a 4f 4a 2c 4d 3d 30 2c 43 3d 31 31 2c 53 3d 34 2c 45 3d 32 2c 50 3d 34 37 26 4c 3d 49 3d 4b 6a 65 6c 6c 65 72 62 6f 64 2c 4d 3d 30 2c 43 3d 36 2c 53 3d 34 2c 45 3d 32 2c 50 3d 36 32 26 4c 3d 49 3d 6d 67 6d 74 2c 4d 3d 30 2c 43 3d 31 2c 53 3d 34 2c 45 3d 32 2c 50 3d 37 34 26 4c 3d 49 3d 52 69 
[B0:C5:54:AA:BB:CC][LE]> char-read-hnd 0x0018
Characteristic value/descriptor: 4e 3d 33 3b 50 3d 33 3b 6e 64 65 64 61 6c 2c 4d 3d 30 2c 43 3d 31 31 2c 53 3d 34 2c 45 3d 32 2c 50 3d 36 32 
```

These strings are decoded as:
```
N=3;P=1;L=I=Antiboks,M=0,C=6,S=4,E=2,P=62&L=I=ASV17,M=0,C=11,S=4,E=2,P=46&L=I=ASV17-dlink,M=0,C=6,S=4,E=2,P=68&L=I=fjorde123,M=0
N=3;P=2;,C=1,S=4,E=2,P=58&L=I=JOJ,M=0,C=11,S=4,E=2,P=47&L=I=Kjellerbod,M=0,C=6,S=4,E=2,P=62&L=I=mgmt,M=0,C=1,S=4,E=2,P=74&L=I=Ri
N=3;P=3;ndedal,M=0,C=11,S=4,E=2,P=62
```

Which, when joined after removing the N/P paging info, becomes::
```
L=I=Antiboks,M=0,C=6,S=4,E=2,P=62&L=I=ASV17,M=0,C=11,S=4,E=2,P=46&L=I=ASV17-dlink,M=0,C=6,S=4,E=2,P=68&L=I=fjorde123,M=0,C=1,S=4,E=2,P=58&L=I=JOJ,M=0,C=11,S=4,E=2,P=47&L=I=Kjellerbod,M=0,C=6,S=4,E=2,P=62&L=I=mgmt,M=0,C=1,S=4,E=2,P=74&L=I=Rindedal,M=0,C=11,S=4,E=2,P=62
```

And after splitting this on & we get the final result:
```
L=I=Antiboks,M=0,C=6,S=4,E=2,P=62
L=I=ASV17,M=0,C=11,S=4,E=2,P=46
L=I=ASV17-dlink,M=0,C=6,S=4,E=2,P=68
L=I=fjorde123,M=0,C=1,S=4,E=2,P=58
L=I=JOJ,M=0,C=11,S=4,E=2,P=47
L=I=Kjellerbod,M=0,C=6,S=4,E=2,P=62
L=I=mgmt,M=0,C=1,S=4,E=2,P=74
L=I=Rindedal,M=0,C=11,S=4,E=2,P=62
```

So each L entry is made up of the same set of keys:

 * I: essid
 * M: opmode? or authalg? (always 0 in the sample)
 * C: channel (2.4 GHz only)
 * S: key_mgmt/auth_alg/proto?
 * E: key_mgmt/auth_alg/proto?
 * P: relative signal. Higher is better. dBm + 100?

Still need to figure out the mapping of the M,S,E keys to
wpa_supplicant config settings. I assume they represent enums.  But we
can simply treat them as opaque values since we only use the survey
data to help setup WiFi anyway. We copy these to the setup request,
and do not need to know what they mean.


FWIW, my example setting `M=0;I=Kjellerbod;S=4;E=2`
is mapped to this wpa_supplicant configuration:
```
# cat /tmp/wpa_supplicant.conf 
ctrl_interface=/var/run/wpa_supplicant
device_type=4-0050F204-3
model_name=DCS-8000LH
manufacturer=D-Link
os_version=01020300
config_methods=push_button virtual_push_button
eapol_version=1
network={
        scan_ssid=1
        ssid="Kjellerbod"
        key_mgmt=WPA-PSK
        auth_alg=OPEN
        proto=RSN
        psk="redeacted"
}
```

##### A201

This write request allows setting an admin password, used for example
by the webserver. It takes the old and new passwords as unencoded
input, verifies that the old password matches, and then change the
admin password to the provided new one.

The initial password is empty, which prevents webserver
authentication. Simply provide an empty string for the old password in
the first request: **P=;N=newpassword**

But this request is much more useful in other ways.... The new passord
(N_str) is processed like this (after slight compression of the
interesting code lines):

```
	snprintf(cmd, sizeof(cmd), "mdb set admin_passwd %s", N_str);
	snprintf(cmdbuf, sizeof(cmdbuf), "%s > %s 2>&1", cmd, p_name);
	fp = popen(cmdbuf, "r");
```

You don't have to be a security expert to see the problem here. But
one mans bug is another mans feature :-)


##### A303

The two strings S and M are url decoded and checked for special
characters.  Then the **orginal** url encoded strings are written to
**/tmp/mydlink/reg_info** and SIGUSR1 is sent to the **da_adaptor**
process.  Presumably triggering it to reread the reg_info file.

It is pretty safe to assume that this provides some registration info
to the mydlink system, allowing it to connect to the cloud service.

The set of allowed characters is rather interesting:
```
 "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
```

Which initially made me think that this was an obvious security hole,
since I missed the point that it's the url encoded strings that are
used on the command line.

But given the quality of the rest of the code here, I would be very
surprised if there isn't an issue or ten in the da_adaptor code
allowing this to be abused. It's just a bit harder to figure out
without the source code.



### Firmware updates

There are at least two shell scripts providing a firmware update
service in the D-Link firmware:

 * /var/www/config/firmwareupgrade.cgi
 * /sbin/fwupdate

They are both pretty similar and obviously come from the same source.
The main difference is that firmwareupgrade.cgi provides the NIPCA
firmwareupgrade service, while fwupdate is a command line tool.

The web service is most interesting for us, providing both the upload
and upgrade in one simple tool.  The fwupdate tool is used by the
mydlink cloud tool **da_adaptor** , via an fw_upgrade symlink.


#### Signed and encrypted

Looking at the contents of a firmware update from D-Link can be
demotivating at the beginning:

```
$ tar xvf DCS-8000LH_Ax_v2.02.02_3014.bin 
update.bin.aes
update.aes
aes.key.rsa
certificate.info
sign.sha1.rsa

$ file *
aes.key.rsa:      data
certificate.info: ASCII text
sign.sha1.rsa:    data
update.aes:       data
update.bin.aes:   data

$ ls -l
total 10956
-rw-r--r-- 1 bjorn bjorn      128 Feb 14 10:58 aes.key.rsa
-rw-r--r-- 1 bjorn bjorn      130 Feb 14 10:58 certificate.info
-rw-r--r-- 1 bjorn bjorn      128 Feb 14 10:58 sign.sha1.rsa
-rw-r--r-- 1 bjorn bjorn 10268368 Feb 14 10:58 update.aes
-rw-r--r-- 1 bjorn bjorn   936464 Feb 14 10:58 update.bin.aes
```

So all the interesting stuff is AES encrypted, and the AES key is RSA
encrypted.  The only directly readable file is this one, and it
doesn't tell us much:

```
$ cat certificate.info 
Publisher:DMdssdFW1
Supported Models:DCS-8000LH,DCS-8000LH
Firmware Version:1.0.0
Target:update.bin
Build No:3014
Contents:update
```

Not much we can do about this then.  Or so it seems...  Until we look
at **firmwareupgrade.cgi**, or **fwupdate** which has almost the same
code:

```
verifyFirmware() {
	result=uploadSign
	#tar tf "$UPLOADBIN" > /dev/null 2> /dev/null || return 1
	fw_sign_verify.sh "$UPLOADBIN" /etc/db/verify.key > /dev/null 2> /dev/null || return 1
	return 0
}

decryptFirmware() {
	result=uploadDecrypt
	pibinfo PriKey > $dir/decrypt.key 2> /dev/null
	fw_decrypt.sh $dir/decrypt.key $out > /dev/null 2> /dev/null || return 1
	return 0
}
```

Can it be that simple?  Yes, it is.

Looking further at the **fw_sign_verify.sh** and **fw_decrypt.sh**,
used by both update tools, confirms it.  The firmware is verified by
using the RSA public key in **/etc/db/verify.key** to decrypt the hash
in **sign.sha1.rsa**.  Then it is decrypted using a key from the
factory data **pib** partition.



#### Further unpacking the firmware update

So we have the keys and the hashing algorithms we need to both verify
and decrypt this firmware.  We can run the commands found in
**fw_decrypt.sh** to get the real contents (slightly adapted to modern
openssl versions):

```
$ openssl rsautl -decrypt -in aes.key.rsa -inkey decrypt.key -out aes.key

$ openssl aes-128-cbc -v -md md5 -kfile aes.key -nosalt -d -in update.bin.aes -out update.bin
bufsize=8192
*** WARNING : deprecated key derivation used.
Using -iter or -pbkdf2 would be better.
bytes read   :   936464
bytes written:   936454

$ openssl aes-128-cbc -v -md md5 -kfile aes.key -nosalt -d -in update.aes -out update
bufsize=8192
*** WARNING : deprecated key derivation used.
Using -iter or -pbkdf2 would be better.
bytes read   : 10268368
bytes written: 10268355

$ file update.bin update
update.bin: POSIX shell script, ASCII text executable
update:     data
```

OK, the **update** file is still in an unknown format, but at least
we have the tool used to write it to the system. And it is a shell
script, so we have the source to look at too!  But 936454 bytes is a
hell of a shell script, and this is of course because most of it is an
uuencoded binary.  So we don't know exactly what that does.  But it is
named ddPack so a fair guess is that it is a tool for dd'ing multiple
file systems or other images packed as a single file.  That's really
enough info.

binwalk shows that the **update** file is just two squashfs systems
and a kernel, with a 1024 header of some sort.  The header presumably
tells ddPack how it should apply these three images:

```
$ binwalk update

DECIMAL       HEXADECIMAL     DESCRIPTION
--------------------------------------------------------------------------------
1024          0x400           Squashfs filesystem, little endian, version 4.0, compression:xz, size: 338755 bytes, 16 inodes, blocksize: 131072 bytes, created: 2019-02-14 09:58:28
340992        0x53400         uImage header, header size: 64 bytes, header CRC: 0x675F081D, created: 2019-02-14 09:31:53, image size: 1661571 bytes, Data Address: 0x804D4960, Entry Point: 0x804D4960, data CRC: 0x73083021, OS: Linux, CPU: MIPS, image type: OS Kernel Image, compression type: none, image name: "linux_3.10"
2002627       0x1E8EC3        Squashfs filesystem, little endian, version 4.0, compression:xz, size: 8265620 bytes, 2145 inodes, blocksize: 131072 bytes, created: 2019-02-14 09:58:45
```

But we can easily guess that without knowing anything about the
header.  There is only one alternative:
 * The kernel goes into the **kernel** partition
 * The 8265620 bytes squasfs system goes into the **rootfs** partition
 * The remaining squasfs system goes into the **userdata** partition

So there is no need to analyze ddPack.  We have the necessary entry
points for **fwupdate** or **firmwareupgrade.cgi** in the
**update.bin** script, and that's what we needed to know for the next
step:


#### Creating our own firmware updates

We do have shell access, so we can simply write the file systems we
want to flash as shown earlier.  We don't need to use the D-Link
scripts.  But where's the fun in that?

There is one challenge here: The D-Link tools are expecting signed and
encrypted firmware updates.  They will run their verifyFirmware() and
decryptFirmware() functions, and fail the update if any of the returns
an error.

But bailing out on verification errors is only the default setting, as
illustrated by this code from **fwupdate** (there is code with similar
functionality in **firmwareupgrade.cgi**):


```
        TrustLevel=`tdb get SecureFW _TrustLevel_byte`
        verifyFirmware
        ret=$?
        case $ret in
                2)
                        sign="not_signed"
                ;;
                0)
                        sign="trust"
                ;;
                *)
                        sign="untrust"
                ;;
        esac    
        if [ "$do_up" = "1" -a "$ret" != "0" -a "$TrustLevel" = "1" ]; then
                echo "3"
                return 1
        fi
```

So we don't need to sign the firmware if we change the **SecureFW
_TrustLevel** setting.  Or we can even sign it with a key unknown to
the camera if we like.  Which can be useful if we ever replace the
**rootfs**, since it will allow us to install our own verification
key and use it with D-Links tools.

But what about the encryption?  This cannot be disabled.  This gets
even better: The decrypting key so graciously provided to us in the
**pib** partition is an RSA private key. So not only can we decrypt
the firmware with it, but we can also encrypt! Nice.


The **Makefile** in this repo has examples of how to use this to
create firmware update images which are accepted by the **fwupdate**
and **firmwareupgrade.cgi** tools.  It uses an alternatative
**update.bin** made to modify only the **userdata** partition.  This
way we can install our own code in the camera, but still leave the
D-Link camera OS unmodified.


## Contact


Please contact me on bjorn@mork.no if you have questions, comments or
just want to say hi.

But please note that I won't be able to provide any support for this.
I am making this information available for educational purposes.  If
you find it useful, then great!  If you brick a camera, then I am
truly sorry about that.  But there isn't much I can do about it....
