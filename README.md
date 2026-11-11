# ZUP (زوپ)

Offline, single-player, vertical neon arcade game for Android.
Kotlin + Android Canvas, zero third-party dependencies.

**Gameplay:** the ball slides left/right automatically. Tap to reverse direction,
slip through the gaps in the falling neon barriers, collect crystals (+5), and survive as
the speed ramps up. Tap to restart after Game Over. Best score is stored locally.

## Build (cloud only)
Every push to `main` (or a manual run from the *Actions* tab) builds a Debug APK with GitHub Actions
(JDK 17, Android SDK 34, Gradle 8.7, AGP 8.5.2). Download it from the run page under **Artifacts → ZUP-debug-apk**.
No signing keys or secrets are stored in this repository.
