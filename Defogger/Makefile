
## FIXME
KEYPASS=123456

## aapt - Android Asset Packaging Tool

## dx is renamed in Debian due to naming conflicts:
DX=dalvik-exchange

API=23
SDKCLASSPATH=/usr/lib/android-sdk/platforms/android-$(API)/android.jar

# This must match whatever dalvik-exchange supports
JAVAVER=8

RESOURCES=layout/activity_main.xml values/strings.xml
RES=$(addprefix res/,$(RESOURCES))

CLASSES=R.class MainActivity.class

all: bin/defogger.apk

src/no/mork/defogger/R.java: AndroidManifest.xml $(RES)
	aapt package -f -m -J src -M AndroidManifest.xml -S res -I $(SDKCLASSPATH)

obj/%.class: src/no/mork/defogger/%.java
	javac -d obj -source $(JAVAVER) -target $(JAVAVER) -classpath src -bootclasspath $(SDKCLASSPATH) $<

classes.dex: $(addprefix obj/,$(CLASSES))
	$(DX) --dex --output=$@ obj

bin/defogger.unaligned.apk: classes.dex AndroidManifest.xml
	aapt package -f -m -F $@ -M AndroidManifest.xml -S res -I $(SDKCLASSPATH)
	aapt add $@ classes.dex


defogger.keystore:
	keytool -storepass $(KEYPASS) -keypass $(KEYPASS) -genkeypair -alias defogger -dname "dc=no, dc=mork, cn=defogger" -validity 365 -keystore $@ -keyalg EC -keysize 256 -sigalg SHA256withECDSA

bin/defogger.apk: bin/defogger.unaligned.apk defogger.keystore
	apksigner sign -ks defogger.keystore -ks-pass "pass:$(KEYPASS)" -key-pass "pass:$(KEYPASS)" -out $@ bin/defogger.unaligned.apk

clean:
	rm -f src/no/mork/defogger/R.java $(addprefix obj/,$(CLASSES)) classes.dex
