#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# MeowAlarm: native Termux build setup. Run with: bash setup-termux.sh

pkg update -y
pkg install -y openjdk-21 wget unzip aapt aapt2 d8 ecj apksigner

if [ -d "$PREFIX/lib/jvm/java-21-openjdk" ]; then
  export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
elif [ -d "$PREFIX/opt/openjdk-21" ]; then
  export JAVA_HOME="$PREFIX/opt/openjdk-21"
else
  export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
export ANDROID_HOME="$PREFIX/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/platforms/android-34" "$ANDROID_HOME/build-tools/34.0.0"

# Termux provides Android build tools compiled for the phone's ARM architecture.
# Do NOT download Google's Linux/x86_64 command-line tools on the phone.
ln -sf "$PREFIX/share/aapt/android.jar" "$ANDROID_HOME/platforms/android-34/android.jar"
ln -sf "$PREFIX/bin/aapt2" "$ANDROID_HOME/build-tools/34.0.0/aapt2"
ln -sf "$PREFIX/bin/zipalign" "$ANDROID_HOME/build-tools/34.0.0/zipalign"
ln -sf "$PREFIX/bin/apksigner" "$ANDROID_HOME/build-tools/34.0.0/apksigner"
ln -sf "$PREFIX/bin/aidl" "$ANDROID_HOME/build-tools/34.0.0/aidl" 2>/dev/null || true

# Force Android Gradle Plugin to use Termux's ARM-native aapt2.
mkdir -p "$HOME/.gradle"
cat > "$HOME/.gradle/gradle.properties" <<EOF
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
EOF

cat > "$HOME/.meowalarm-env" <<EOF
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export ANDROID_HOME="$PREFIX/android-sdk"
export ANDROID_SDK_ROOT="$PREFIX/android-sdk"
export PATH="$PREFIX/bin:\$PATH"
EOF

# Verify the native tools before the build.
echo
java -version 2>&1 | head -1
gradle_dummy=0
for tool in aapt2 zipalign apksigner d8 ecj; do
  command -v "$tool" >/dev/null || { echo "ERROR: $tool نصب نشد."; exit 1; }
done
[ -f "$ANDROID_HOME/platforms/android-34/android.jar" ] || { echo "ERROR: Android platform 34 آماده نشد."; exit 1; }

echo
echo "Setup completed successfully."
echo "Next step: bash build-termux.sh"
