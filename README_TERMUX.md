# MeowAlarm — Termux build

This project is configured to build directly on an ARM Android phone in Termux.

Important: do not install Google's Linux Android command-line tools on the phone. Those tools are distributed as Linux host binaries and are not the native Termux build path. This project uses Termux's ARM-native Android build tools (`aapt2`, `aapt`, `d8`, `ecj`, `apksigner`, `zipalign`) and a Java-only Gradle distribution.

## 1. Termux
Use the official Termux GitHub release. In Termux run:

```bash
termux-setup-storage
```

Allow storage access when Android asks.

## 2. Extract the project
If the ZIP is in Downloads:

```bash
cd ~/storage/downloads
unzip -o MeowAlarm-Android14-APK-ready-v4.zip
cd MeowAlarm
```

## 3. One-time setup

```bash
bash setup-termux.sh
```

Do not use `./setup-termux.sh` from `~/storage/downloads`; Android shared storage can be mounted `noexec`. Running it through `bash` avoids that issue.

## 4. Build the APK

```bash
bash build-termux.sh
```

The script copies the source into Termux HOME before building, so the build is not performed directly inside shared Android storage.

The APK will be copied to:

```text
~/storage/downloads/MeowAlarm-debug.apk
```

and also to:

```text
~/storage/downloads/MeowAlarm/dist/MeowAlarm-debug.apk
```

## 5. Install

```bash
termux-open ~/storage/downloads/MeowAlarm-debug.apk
```

Android's package installer will open.

## If setup was interrupted
Run the same command again:

```bash
bash setup-termux.sh
```

It is safe to rerun. Then:

```bash
bash build-termux.sh
```


Termux note: zipalign is provided by the official aapt package; do not install a separate zipalign package.


## Android 14 notes

- targetSdk/compileSdk: 34 (Android 14).
- Exact alarms use `USE_EXACT_ALARM`, appropriate for an alarm-clock app.
- Alarm playback runs as a `systemExempted` foreground service; Android 14 explicitly allows this type for apps holding `USE_EXACT_ALARM` that use a foreground service to continue alarms in the background.
- Full-screen alarm UI uses `USE_FULL_SCREEN_INTENT`; Android 14 may show the system setting if the permission is not granted.
- The debug APK is installable directly; it is not a Play Store release build.
