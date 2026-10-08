# MeowAlarm – GitHub Actions APK build

This ZIP is **repository-root ready**: extract it, then upload all extracted files/folders into the **root** of a GitHub repository. Make sure that `.github/workflows/build-apk.yml` is preserved.

Expected layout:

```text
.github/workflows/build-apk.yml
app/build.gradle.kts
app/src/main/...
build.gradle.kts
settings.gradle.kts
gradle.properties
```

**Do not nest these files inside an additional `MeowAlarm/` directory.** The original GitHub workflow used `working-directory: MeowAlarm` even though the upload instructions used repository root. This has been corrected.

1. Create a repository on GitHub (private is fine).
2. Commit/upload the *contents* of this ZIP to your repository root, including `.github`.
3. Go to **Actions → Build MeowAlarm APK → Run workflow**, or just push to `main`/`master`.
4. Once the build succeeds, open its run and download the **MeowAlarm-debug** artifact.
5. Extract the artifact ZIP to obtain `app-debug.apk` for Android installation/testing.

The workflow uses Java 17, Android Gradle Plugin 8.5.2, Gradle 8.9, compileSdk 34 and Build Tools 34.0.0. It explicitly verifies that the Android SDK platform exists and prints the installed SDK packages. If a build fails, open the failure step and copy its complete error text; SDK, Gradle, and source compilation errors have different causes.

**This is a debug APK, not a signed release or a Play Store bundle.** This project was statically checked, but could not be compiled here because the Android SDK/Gradle downloads are inaccessible in this environment. Test alarm delivery with a locked phone, after reboot, while battery optimization is enabled, and on your target Android version.

## Changes in this revision

- Build from GitHub repository root and verify SDK 34 before Gradle.
- Do not overwrite the stored Android alarm settings when the WebView reloads.
- Wait for the actual native snooze alarm instead of firing one from the WebView timer.
- Apply Android 14's `systemExempted` foreground-service type only on API 34+.
- Do not keep the screen awake while browsing alarm settings.
- Avoid creating an empty service instance just to stop an alarm.
- Preserve vibration if alarm audio initialization fails.
