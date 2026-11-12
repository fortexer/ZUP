# ZUP (زوپ)

Offline, single-player, vertical neon arcade game for Android — Kotlin + Android Canvas, zero third-party dependencies.

**Gameplay:** the ball slides left/right; tap to reverse direction, slip through the gaps of falling neon barriers
(single gaps, double gaps, moving gates), collect crystals (+5 score, +3 coins) and survive as the speed ramps up.

**Features:** main menu, shop (8 ball skins, 6 trail effects, buy/equip/unequip with in-game coins), settings
(sound, music, vibration, effects quality, confirmed data reset), pause with countdown, redesigned Game Over,
synthesized sound effects and music (no asset files), Lucide-style icons drawn in code. All data is stored locally.

## Build (cloud only)
Every push to `main` (or a manual run in the *Actions* tab) builds a Debug APK with GitHub Actions
(JDK 17, Android SDK 34, Gradle 8.7, AGP 8.5.2). Download it from the run page under **Artifacts → ZUP-debug-apk**.
No signing keys or secrets are stored in this repository.
