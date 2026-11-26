set -e
PKG=android/app/src/main/java/com/videoaudio/downloader
cp native/*.java "$PKG/"

cat > "$PKG/MainActivity.java" << 'JAVA'
package com.videoaudio.downloader;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(YtDlpPlugin.class);
        super.onCreate(savedInstanceState);
        handleShare(getIntent(), false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent, true);
    }

    private void handleShare(Intent intent, boolean live) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        intent.setAction(Intent.ACTION_MAIN);
        if (text == null || text.trim().isEmpty()) return;
        if (text.length() > 4000) text = text.substring(0, 4000);
        ShareStore.pending = text;
        if (live && getBridge() != null) {
            getBridge().triggerWindowJSEvent("sharedtext", "{}");
        }
    }
}
JAVA

G=android/app/build.gradle
sed -i '0,/^dependencies {/s//dependencies {\n    implementation "io.github.junkfood02.youtubedl-android:library:0.18.1"\n    implementation "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1"/' $G
sed -i '0,/^android {/s//android {\n    packaging { jniLibs { useLegacyPackaging = true } }/' $G
sed -i '0,/defaultConfig {/s//defaultConfig {\n        ndk { abiFilters "arm64-v8a"; if (project.hasProperty("legacy")) { abiFilters "armeabi-v7a" } }/' $G
sed -i 's/minSdkVersion = [0-9]*/minSdkVersion = (project.hasProperty("legacy") ? 25 : 29)/' android/variables.gradle
sed -i 's/<application/<application android:extractNativeLibs="true"/' android/app/src/main/AndroidManifest.xml

echo "--- patched ---"
grep -n "youtubedl\|useLegacyPackaging\|abiFilters" $G
grep -n "minSdkVersion" android/variables.gradle
grep -n "extractNativeLibs" android/app/src/main/AndroidManifest.xml

G=android/app/build.gradle
if [ -n "$KS_PASS" ] && [ -f "$KS_FILE" ]; then
  sed -i '0,/^android {/s//android {\n    signingConfigs { release { storeFile file(System.getenv("KS_FILE")); storePassword System.getenv("KS_PASS"); keyAlias System.getenv("KS_ALIAS"); keyPassword System.getenv("KS_PASS") } }/' $G
  sed -i '0,/buildTypes {/s//buildTypes {\n        debug { signingConfig signingConfigs.release }/' $G
  echo "signing: custom key"
else
  echo "signing: default debug key"
fi

M=android/app/src/main/AndroidManifest.xml
sed -i 's#</application>#        <service android:name=".DownloadService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>#' $M
sed -i 's#</manifest>#    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.WAKE_LOCK" />\n</manifest>#' $M
grep -n "DownloadService\|FOREGROUND_SERVICE\|POST_NOTIFICATIONS" $M

sed -i 's#</manifest>#    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />\n</manifest>#' android/app/src/main/AndroidManifest.xml
grep -n "REQUEST_IGNORE" android/app/src/main/AndroidManifest.xml

sed -i 's#</application>#        <activity android:name=".PlayerActivity" android:exported="false" android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize" android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen" />\n    </application>#' android/app/src/main/AndroidManifest.xml
grep -n "PlayerActivity" android/app/src/main/AndroidManifest.xml

python3 - << 'PY'
p = 'android/app/src/main/AndroidManifest.xml'
s = open(p).read()
filt = """            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>"""
if 'android.intent.action.SEND' not in s:
    s = s.replace('</activity>', filt, 1)
open(p, 'w').write(s)
PY
grep -n "action.SEND" android/app/src/main/AndroidManifest.xml

G=android/app/build.gradle
M=android/app/src/main/AndroidManifest.xml
sed -i "s/versionCode [0-9]*/versionCode ${GITHUB_RUN_NUMBER:-1}/; s/versionName \"[^\"]*\"/versionName \"1.${GITHUB_RUN_NUMBER:-0}\"/" $G
mkdir -p android/app/src/main/res/xml
cat > android/app/src/main/res/xml/update_paths.xml << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="updates" path="updates/" />
</paths>
XML
sed -i 's#</application>#        <provider android:name=".UpdateFileProvider" android:authorities="${applicationId}.updates" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/update_paths" /></provider>\n    </application>#' $M
sed -i 's#</manifest>#    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n</manifest>#' $M
grep -n "versionCode\|versionName" $G
grep -n "UpdateFileProvider\|REQUEST_INSTALL_PACKAGES" $M

G=android/app/build.gradle
M=android/app/src/main/AndroidManifest.xml
sed -i 's/versionName "1\.\([0-9]*\)"/versionName ("1.\1" + (project.hasProperty("legacy") ? "-legacy" : ""))/' $G
sed -i 's#</manifest>#    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />\n</manifest>#' $M
grep -n "legacy" $G android/variables.gradle
grep -n "WRITE_EXTERNAL_STORAGE" $M
