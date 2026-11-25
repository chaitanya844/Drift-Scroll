# Drift Scroll

A small native Android app for hands-free PDF reading. Android 8.0 or newer.

[Download the latest APK](https://github.com/chaitanya844/Drift-Scroll/releases/latest) · [Source code](https://github.com/chaitanya844/Drift-Scroll)

- Short upward swipes (24–48 dp, capped at 8% of screen height) scroll the page down. Slow 650–950 ms gestures reduce flicking. Actual page movement depends on the reader, zoom, and touch slop.
- Curved paths with slightly varied horizontal positions; recent rounded endpoints are not reused (the last 8,192 swipes are remembered, keeping memory bounded for unlimited sessions). This is variation for reading comfort, not a guarantee of resembling a person or bypassing detection.
- Random intervals between swipe starts, with no 60-second cap (default 8–18). Positive whole seconds up to the integer input range are accepted.
- Session timer with no 120-minute cap; **0 minutes = unlimited**. Floating Stop shows remaining time or ∞.
- Floating **Pause / Resume** beside Stop freezes both the timer and the wait until the next swipe. Resume continues the same session. Stop works while paused. An already dispatched swipe may finish (under one second) when Pause is tapped.
- Every setting is saved as you edit, even before pressing Start, including unfinished drafts. Existing settings are retained on upgrade. New installations default to unlimited; set an existing installation's duration to 0 to turn off its timer.
- Stops on screen lock, service interruption, failed/cancelled gestures, or opening Android system controls. Sessions do not restart automatically.
- No internet permission, content retrieval, screenshots, analytics, or account required.

## Use

1. Install the debug APK on an Android phone.
2. Open **Drift Scroll**, tap **Enable accessibility**, read the explanation, then enable **Drift Scroll** in Android's settings and return.
3. Set the random delay range and session duration. Tap **Start reading session** and switch to your PDF reader. The timer starts immediately; swipes wait while Drift Scroll is visible.
4. Use a PDF viewer's continuous vertical scrolling mode. The floating **Stop** button ends the session; locking the screen also stops it.

Some Android versions require allowing restricted settings for a sideloaded accessibility app in its App info menu. Enable this only for a build you trust. The accessibility capability is required for gesture injection; the app does not request permission to read window content. A swipe already in progress may finish (under one second) after Stop is pressed. Android scheduling may delay gestures, so configured intervals are not a real-time guarantee.

## Build

Open this folder in Android Studio with Android SDK 36, Build Tools 36.0.0, and JDK 17 or newer compatible with Gradle 9.1.0. Set `sdk.dir` in `local.properties` to your SDK directory. Run `gradlew.bat assembleDebug lintDebug` on Windows, or `./gradlew assembleDebug lintDebug` elsewhere. APK: `app/build/outputs/apk/debug/app-debug.apk`.

The delivered `DriftScroll-debug.apk` was built with the official SDK command-line tools using `tools/build-apk.ps1`, which compiles resources and Java, generates DEX, aligns the APK, and signs it with a local debug key. This provides a reproducible build without waiting for a full Gradle download. Run `./tools/build-apk.ps1 -SdkRoot /path/to/android-sdk` in PowerShell to rebuild it. This is a locally signed testing build, not a Play Store release.

Validation completed: Java compilation against Android API 36, resource compilation/linking, DEX generation, APK signature verification, and 51,600 generated-gesture checks. Device testing covered the landscape interface, short page movements, timer completion, Pause/Resume, Stop, and settings persistence. Gradle lint, other Android versions, portrait layout, and individual PDF viewers remain unverified.

Pure Java gesture checks can run without the Android SDK:

```powershell
javac -d .tools/test-classes app/src/main/java/com/gladbox/driftscroll/ScrollPlan.java tests/ScrollPlanTest.java
java -cp .tools/test-classes ScrollPlanTest
```

## Device checks

Version 1.2 verification: the floating Pause button changed to Resume; screenshots six seconds apart showed identical reading position and 0:38 remaining. Resume advanced the page and countdown again. Stop while paused removed both controls and ended the session. The user's saved configuration was restored and the temporary reading test app removed.

Version 1.1 verification: 51,600 generated swipes and large-delay overflow checks passed. On the connected device, edited delay and unlimited-duration values persisted through force-stop/relaunch without pressing Start. The original 8–18 second range was restored and duration set to 0. Android disabled accessibility during force-stop; its previous enabled state was restored through Settings.

- Confirm setup, accessibility enable/disable, and settings persistence.
- Start a 1-minute session in a continuous-scroll PDF; check short swipes and timer stop.
- Test floating Stop during a wait and during a swipe; check no later swipe occurs.
- Test screen lock, rotation, service disable, and manual swipe interruption.
- Check portrait/landscape, large font settings, and your PDF viewer at typical zoom.
- Verify minimum/maximum delays and input errors; reopen the app during a session.

API references: [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [AGP 9.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes).
