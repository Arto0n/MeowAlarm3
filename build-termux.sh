#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
source "$HOME/.meowalarm-env" 2>/dev/null || true
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "$JAVA_HOME" ]; then
  if [ -d "$PREFIX/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
  elif [ -d "$PREFIX/opt/openjdk-21" ]; then
    export JAVA_HOME="$PREFIX/opt/openjdk-21"
  else
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  fi
fi
export ANDROID_HOME="${ANDROID_HOME:-$PREFIX/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -f "$HOME/.gradle/gradle.properties" ]; then
  echo "Setup has not been run yet."
  echo "Run: bash setup-termux.sh"
  exit 1
fi

# Android shared storage can be mounted noexec. Build from Termux HOME instead.
BUILD_ROOT="$HOME/.meowalarm-build"
rm -rf "$BUILD_ROOT"
mkdir -p "$BUILD_ROOT"
cp -a "$SCRIPT_DIR/." "$BUILD_ROOT/"
cd "$BUILD_ROOT"

GRADLE_VERSION="8.9"
# Android Gradle Plugin 8.5.2 is used by this project and is compatible with Gradle 8.7+.
GRADLE_HOME="$HOME/.meowalarm-gradle/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$HOME/.meowalarm-gradle"
  TMP="$PREFIX/tmp/gradle-$GRADLE_VERSION.zip"
  mkdir -p "$PREFIX/tmp"
  echo "Downloading Gradle $GRADLE_VERSION..."
  wget -q --show-progress -O "$TMP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  rm -rf "$GRADLE_HOME"
  unzip -q "$TMP" -d "$HOME/.meowalarm-gradle"
  rm -f "$TMP"
fi

"$GRADLE_HOME/bin/gradle" --version | grep -E 'Gradle|JVM' | head -2
"$GRADLE_HOME/bin/gradle" --no-daemon --stacktrace clean assembleDebug

OUT="$BUILD_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$OUT" ] || { echo "ERROR: APK ساخته نشد."; exit 1; }

mkdir -p "$SCRIPT_DIR/dist"
cp -f "$OUT" "$SCRIPT_DIR/dist/MeowAlarm-debug-v1.2.apk"
cp -f "$OUT" "$SCRIPT_DIR/dist/MeowAlarm-debug.apk"

# Also put a copy in Android Downloads when Termux storage permission exists.
if [ -d "$HOME/storage/downloads" ]; then
  cp -f "$OUT" "$HOME/storage/downloads/MeowAlarm-debug.apk"
fi

echo
echo "========================================"
echo "BUILD SUCCESSFUL"
echo "APK: $SCRIPT_DIR/dist/MeowAlarm-debug.apk"
if [ -d "$HOME/storage/downloads" ]; then
  echo "Downloads: $HOME/storage/downloads/MeowAlarm-debug.apk"
fi
echo "========================================"
