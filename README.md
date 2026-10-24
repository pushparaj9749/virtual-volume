# Virtual Volume

> **Your volume button, rebuilt for your screen.**

Virtual Volume is two separate products that share one repository:

1. **A native Android app** that replaces the physical volume rocker with a thin, always-reachable
   floating control on the edge of the screen. It draws over other apps with the standard Android
   overlay API and drives the real hardware volume through `AudioManager`.
2. **A static marketing website** (GitHub Pages) with a product landing page whose **Download APK**
   button always resolves the newest signed APK from GitHub Releases.

There is no shared runtime: the app is not a WebView, and the website is not bundled into the app.

```
/
├── android/      # Native Android application (Kotlin, Jetpack Compose, Material 3)
├── website/      # Static product site (no framework, no build step, no tracking)
├── tools/        # Reproducible asset generators (pure stdlib)
├── .github/
│   └── workflows/
│       ├── android-build.yml   # CI: compile, unit tests, lint, debug + release APKs
│       ├── release.yml         # Tag-triggered signed release + GitHub Release publishing
│       └── website-deploy.yml  # GitHub Pages deployment
└── README.md
```

---

## Project overview

The floating control is a slim bar hugging the left or right edge of the display. It is subtle
(translucent) when idle, brightens while touched, and never gets in the way — the touch window is a
narrow strip, so everything else on the screen stays fully usable.

* **Tap** the upper half to raise the volume one step, the lower half to lower it.
* **Swipe up / down** for discrete steps.
* **Drag continuously** for a smooth sweep.

The position is stored **relative to the current screen** (an edge plus a fraction of the usable
height), never as raw coordinates. On every configuration, display or screen-on change the control
re-resolves its position against the live bounds and insets, so it keeps its edge and its spot in
portrait **and both landscape rotations**.

---

## Features

* **Overlay control** via `TYPE_APPLICATION_OVERLAY` inside a foreground service; compact window, no
  invisible full-screen sheet, `FLAG_NOT_FOCUSABLE` so it never steals input.
* **Orientation-aware positioning** that survives rotation and screen-off periods.
* **Gesture engine** as pure Kotlin: dead zone (touch slop), per-event step cap, reversal
  re-anchoring, tap/split/always modes. The same engine powers the overlay, the in-app preview and
  the onboarding tutorial.
* **Real volume control** through `AudioManager` with strict range clamping; media is the default,
  ring/alarm/notification/call/system are modelled.
* **Quick Settings tile** synchronised with the persisted preference and live service state.
* **Onboarding**: welcome → overlay permission → enable → interactive tutorial → Quick Settings.
  It never repeats; a “Setup guide” replays it.
* **Customisation**: edge, position, length, thickness, idle opacity, active opacity, touch-zone
  size, swipe sensitivity, volume step, tap behaviour, animation duration, haptics, theme.
* **Persistence** of every preference in Jetpack DataStore.
* **Privacy-first**: no analytics, no accounts, no tracking, no advertising SDKs, and no internet
  permission at all.

---

## Architecture

The app follows clean separation between UI, domain and platform layers, with a hand-rolled
dependency container (one process, a handful of singletons — no graph library needed).

```
dev.virtualvolume.app
├── core/
│   ├── data/       # VolumeSettings, DataStore repository, enums (edge/stream/tap/theme)
│   ├── audio/      # VolumeController, VolumeMath, AudioManagerBackend, volume observer
│   └── platform/   # Permissions, Haptics, OverlayRuntime, OverlayServiceController
├── overlay/        # OverlayService, window host, gesture engine, layout resolver, control view
├── tile/           # Quick Settings tile + state sync
├── boot/           # Restore the control after reboot / package update
├── di/             # AppContainer (lazy singletons)
└── ui/             # Compose: theme, dashboard, onboarding, about, shared components
```

Key design decisions:

* **Pure domain logic** (`GestureEngine`, `OverlayLayoutResolver`, `VolumeMath`, `ControlSpecFactory`)
  is free of Android types and is covered by JVM unit tests. The Android layers are thin adapters.
* **One drawing implementation** (`ControlPainter`) renders the control in the overlay, the settings
  preview and the onboarding tutorial, so the look never diverges.
* **The settings preview is the real control** — an `AndroidView` wrapping `OverlayControlView` — not a
  mock-up.

---

## Android requirements

| Item | Value |
|------|-------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (dark-first, optional light) |
| `minSdk` | 26 (Android 8.0 Oreo) |
| `targetSdk` / `compileSdk` | 35 |
| JDK | 17 |
| Gradle | 8.11.1 (pinned in CI; no wrapper jar is committed) |
| AGP / Kotlin | 8.7.3 / 2.0.21 |

---

## Permissions

| Permission | Why |
|------------|-----|
| `SYSTEM_ALERT_WINDOW` | Draw the control over other apps. Required, revocable. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the control alive with the notification Android requires. |
| `POST_NOTIFICATIONS` (API 33+) | Show the foreground-service notification. |
| `VIBRATE` | Optional haptic tick. |
| `RECEIVE_BOOT_COMPLETED` | Restore the control after reboot when the user enables it. |

The manifest requests **no internet permission** — the app cannot talk to the network.

---

## Local development

You need a JDK 17 and the Android SDK. The Gradle wrapper jar is not committed (it is a binary blob);
CI supplies Gradle with `gradle/actions/setup-gradle`. Locally, generate the wrapper once:

```bash
cd android
gradle wrapper --gradle-version 8.11.1   # one-time, needs network
```

Then build and test:

```bash
cd android
./gradlew :app:assembleDebug        # or: gradle :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Run unit tests from an IDE, or install the debug APK on a device/emulator (API 26+).

### Release signing locally

Create `android/keystore.properties` (git-ignored) or export environment variables — both are read by
`android/app/build.gradle.kts` and **never echoed**:

```properties
# android/keystore.properties
storeFile=/path/to/release.keystore
storePassword=...
keyAlias=virtual-volume
keyPassword=...
```

or

```bash
export ANDROID_KEYSTORE_FILE=/path/to/release.keystore
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=virtual-volume
export ANDROID_KEY_PASSWORD=...
```

With a signing config present, `:app:assembleRelease` produces a signed APK; without it, a release
build still assembles unsigned and `release.yml` falls back to a debug-signed APK.

---

## Release process

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`.
2. Commit, merge to `main`, then tag: `git tag v1.2.0 && git push origin v1.2.0`.
3. `release.yml` runs: decodes the keystore from `ANDROID_KEYSTORE_BASE64`, builds the signed release
   APK, verifies it with `apksigner`, and creates/updates a GitHub Release with the APK + SHA-256 and
   generated release notes.
4. The website's **Download APK** button reads the latest release via the GitHub API and points at the
   real asset (falling back to the `releases/latest` redirect).

Semantic versioning is used (`vMAJOR.MINOR.PATCH`).

---

## GitHub Actions secrets

`release.yml` consumes exactly these secret names:

| Secret | Purpose |
|--------|---------|
| `ANDROID_KEYSTORE_BASE64` | base64 of the release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias (`virtual-volume`) |
| `ANDROID_KEY_PASSWORD` | key password |

Secrets are passed to Gradle **only through environment variables**, are never written into the
checkout, never echoed, and the temporary keystore is deleted in a step that always runs.
`android/keystore.properties` and `*.keystore` / `*.jks` are in `.gitignore`.

If `ANDROID_KEYSTORE_BASE64` is absent the workflow still publishes a working download by falling back
to a debug-signed APK and clearly labels it as such.

---

## GitHub Pages deployment

`website-deploy.yml` uploads `website/` as a Pages artifact with `actions/deploy-pages`. The Pages
source for this repository is configured to **GitHub Actions**. No build step is required; the site is
plain HTML/CSS/JS. Deployment runs on pushes to `main` that touch `website/**`.

---

## Known Android limitations

These are platform rules, not bugs, and the app documents them honestly in-app:

* **Screen off.** Android does not deliver touch events to overlays while the display is powered down.
  No app can change this. The service and your settings stay alive and the control reappears — correctly
  positioned, even after a rotation — when the screen turns back on.
* **Protected surfaces.** The lock screen, secure payment sheets and some system dialogs hide every
  overlay by design.
* **Background starts (Android 12+).** The system can refuse a foreground-service start from a broadcast
  (e.g. after boot). Holding the user-granted `SYSTEM_ALERT_WINDOW` permission is a documented exemption,
  so it normally works; otherwise opening the app or tapping the tile restores the control.
* **OEM battery rules.** Some skins need an exclusion from battery optimisation for long-lived services.

The app never attempts unsafe workarounds or claims capabilities Android forbids.

---

## Privacy

* No analytics, crash reporting, accounts, tracking or ad SDKs.
* No internet permission in the app manifest.
* Settings live in the app's private DataStore.
* The website is static and loads no trackers; the download button only calls the GitHub API.

---

## License & attribution

Original brand assets (icon, mark, favicon, website imagery) are generated in this repository; see
`tools/generate_web_icons.py` and `website/assets/img/`. No third-party logos or copyrighted artwork are
used.
