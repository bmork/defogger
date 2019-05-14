# SPDX-License-Identifier: GPL-2.0
# Copyright(c) 2019 Bjørn Mork <bjorn@mork.no>

MODEL=DCS-8000LH
BUILD=9999
PRIKEY=keys/$(MODEL)-PriKey.pem
SIGNKEY=keys/$(MODEL)-sign.pem

OPTFILES=version opt.local
FW_XTRA_FILES=

all: fw

version: dcs8000lh-configure.py
	sed -ne 's/"//g' -e 's/^VERSION *= *//p' dcs8000lh-configure.py >$@

opt.squashfs: $(OPTFILES)
	mksquashfs $(OPTFILES) $@ -all-root -comp xz

aes.key:
	openssl rand 16 > $@

aes.key.rsa: aes.key $(PRIKEY)
	openssl rsautl -encrypt -in aes.key -inkey $(PRIKEY) -out $@

### FIXME:  This is verified using the pubkey in /etc/db/verify.key, which will fail
sign.sha1.rsa: sign.sha1 $(SIGNKEY)
	openssl rsautl -sign -inkey $(SIGNKEY) -out sign.sha1.rsa -in sign.sha1

## FIXME:  It would be nice to have the D-Link signing key instead :-)
$(SIGNKEY):
	echo "WARNING: $(SIGNKEY) is missing - using a new abitrary key instead"
	$(eval SIGNKEY := random-signkey.pem)
	$(eval FW_XTRA_FILES := $(FW_XTRA_FILES) verify.key)
	[ -f $(SIGNKEY) ] || openssl genrsa -out $(SIGNKEY)

verify.key: $(SIGNKEY)
	 openssl rsa -pubout -in $(SIGNKEY) -out $@

sign.sha1: update.bin.aes aes.key.rsa certificate.info update.sha1
	cat $^ | openssl dgst -sha1 | cut -d' ' -f2 > $@

fw: verify.key fw.tar
fw.tar: certificate.info aes.key.rsa sign.sha1.rsa update.aes update.bin.aes $(FW_XTRA_FILES)
	tar cvf $@ $^ $(FW_XTRA_FILES)

update.sha1: update.aes
	 openssl dgst -sha1 $^ | cut -d' ' -f2 > $@

update.aes: aes.key opt.squashfs
	openssl aes-128-cbc -md md5 -kfile aes.key -nosalt -e -out $@ -in opt.squashfs

update.bin: aes.key update.aes update.sh
	$(eval MD5SUM := $(shell openssl aes-128-cbc -md md5 -kfile aes.key -nosalt -d -in update.aes | md5sum - | cut -d' ' -f1 | md5sum - | cut -d' ' -f1))
	sed  -e "s/@@MODEL@@/\"$(MODEL)\"/" -e "s/@@MD5SUM@@/\"$(MD5SUM)\"/" -e "s/@@VERSION@@/\"1.0.0-$(BUILD)\"/" update.sh >update.bin

certificate.info:
	echo "Publisher:DMdssdFW1" >$@
	echo "Supported Models:$(MODEL),$(MODEL)" >>$@
	echo "Firmware Version:1.0.0" >>$@
	echo "Target:update.bin" >>$@
	echo "Build No:$(BUILD)" >>$@
	echo "Contents:update" >>$@

update.bin.aes: aes.key update.bin
	openssl aes-128-cbc -md md5 -kfile aes.key -nosalt -e -out $@ -in update.bin

clean:
	rm -f *.rsa *.aes opt.squashfs sign.sha1 certificate.info update.bin update.sha1 fw.tar verify.key

distclean: clean
	rm -f aes.key random-signkey.pem

