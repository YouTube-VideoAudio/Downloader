set -e
PKG=android/app/src/main/java/com/videoaudio/downloader
cp native/*.java "$PKG/"

cat > "$PKG/MainActivity.java" << 'JAVA'
package com.videoaudio.downloader;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(YtDlpPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
JAVA

G=android/app/build.gradle
sed -i '0,/^dependencies {/s//dependencies {\n    implementation "io.github.junkfood02.youtubedl-android:library:0.18.1"\n    implementation "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1"/' $G
sed -i '0,/^android {/s//android {\n    packaging { jniLibs { useLegacyPackaging = true } }/' $G
sed -i '0,/defaultConfig {/s//defaultConfig {\n        ndk { abiFilters "arm64-v8a" }/' $G
sed -i 's/minSdkVersion = [0-9]*/minSdkVersion = 29/' android/variables.gradle
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
