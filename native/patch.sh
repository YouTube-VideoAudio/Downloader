set -e
PKG=android/app/src/main/java/com/videoaudio/downloader
cp native/YtDlpPlugin.java "$PKG/YtDlpPlugin.java"

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
