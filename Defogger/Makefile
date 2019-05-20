
## FIXME
KEYPASS=123456

## aapt - Android Asset Packaging Tool

## dx is renamed in Debian due to naming conflicts:
DX=dalvik-exchange

APIVER ?= 23
# This must match whatever dalvik-exchange supports
JAVAVER ?= 8

ANDROID_HOME ?= /usr/lib/android-sdk
SDKCLASSPATH ?= $(ANDROID_HOME)/platforms/android-$(APIVER)/android.jar

RESOURCES=layout/activity_main.xml layout/activity_scanner.xml layout/scanitem.xml values/strings.xml
RES=$(addprefix res/,$(RESOURCES))

## Temp disabled while we have some non-building classess....
#CLASSES=obj/no/mork/android/defogger/R.class $(patsubst src/%.java,obj/%.class,$(wildcard src/no/mork/android/defogger/util/*.java) $(wildcard src/no/mork/android/defogger/*.java))
CLASSES=obj/no/mork/android/defogger/R.class $(patsubst src/%.java,obj/%.class,src/no/mork/android/defogger/MainActivity.java src/no/mork/android/defogger/ScannerActivity.java src/no/mork/android/defogger/ScanListAdapter.java)


all: defogger.apk

src/no/mork/android/defogger/R.java: AndroidManifest.xml $(RES)
	aapt package -f -m -J src -M AndroidManifest.xml -S res -I $(SDKCLASSPATH)

obj/%.class: src/%.java
	javac -d obj -source $(JAVAVER) -target $(JAVAVER) -classpath src -bootclasspath $(SDKCLASSPATH) $<

classes.dex: $(CLASSES)
	$(DX) --dex --output=$@ obj

defogger.apk.unsigned: classes.dex AndroidManifest.xml
	aapt package -f -m -F $@ -M AndroidManifest.xml -S res -I $(SDKCLASSPATH)
	aapt add $@ classes.dex

defogger.keystore:
	keytool -storepass $(KEYPASS) -keypass $(KEYPASS) -genkeypair -alias defogger -dname "dc=no, dc=mork, dc=android, cn=defogger" -validity 365 -keystore $@ -keyalg EC -keysize 256 -sigalg SHA256withECDSA

defogger.apk: defogger.keystore defogger.apk.unsigned
	apksigner sign --v2-signing-enabled false --ks defogger.keystore --ks-pass "pass:$(KEYPASS)" --key-pass "pass:$(KEYPASS)"  --out $@.unaligned defogger.apk.unsigned
	zipalign -f 4 $@.unaligned $@

clean:
	rm -f src/no/mork/android/defogger/R.java $(CLASSES) classes.dex *.unsigned *.unaligned *.apk
