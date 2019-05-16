
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

all: defogger.apk

src/no/mork/defogger/R.java: AndroidManifest.xml $(RES)
	aapt package -f -m -J src -M AndroidManifest.xml -S res -I $(SDKCLASSPATH)

obj/%.class: src/no/mork/defogger/%.java
	javac -d obj -source $(JAVAVER) -target $(JAVAVER) -classpath src -bootclasspath $(SDKCLASSPATH) $<

classes.dex: $(addprefix obj/,$(CLASSES))
	$(DX) --dex --output=$@ obj

defogger.apk.unsigned: classes.dex AndroidManifest.xml
	aapt package -f -m -F $@ -M AndroidManifest.xml -S res -I $(SDKCLASSPATH)
	aapt add $@ classes.dex

defogger.keystore:
	keytool -storepass $(KEYPASS) -keypass $(KEYPASS) -genkeypair -alias defogger -dname "dc=no, dc=mork, cn=defogger" -validity 365 -keystore $@ -keyalg EC -keysize 256 -sigalg SHA256withECDSA

defogger.apk: defogger.keystore defogger.apk.unsigned
	apksigner sign --v2-signing-enabled false --ks defogger.keystore --ks-pass "pass:$(KEYPASS)" --key-pass "pass:$(KEYPASS)"  --out $@.unaligned defogger.apk.unsigned
	zipalign -f 4 $@.unaligned $@

clean:
	rm -f src/no/mork/defogger/R.java $(addprefix obj/,$(CLASSES)) classes.dex *.unsigned *.unaligned *.apk
