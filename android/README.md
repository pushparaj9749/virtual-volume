# Virtual Volume — Android app

Native Android application (Kotlin, Jetpack Compose, Material 3, dark-first).

See the repository root `README.md` for architecture, permissions, requirements and limitations.

## Build

```bash
cd android
gradle :app:assembleDebug        # or ./gradlew after generating the wrapper
gradle :app:testDebugUnitTest
gradle :app:lintDebug
```

`minSdk` 26 · `targetSdk`/`compileSdk` 35 · JDK 17 · Gradle 8.11.1 · Kotlin 2.0.21 · AGP 8.7.3.

## Package map

- `core/data` — `VolumeSettings`, DataStore repository, enums.
- `core/audio` — `VolumeController`, `VolumeMath`, backend, volume observer.
- `core/platform` — permissions, haptics, runtime state, service controller.
- `overlay` — service, window host, gesture engine, layout resolver, control view, painter.
- `tile` — Quick Settings tile + sync.
- `boot` — restore after reboot / update.
- `ui` — Compose theme, dashboard, onboarding, about, shared components.

## Testing

Pure JVM unit tests cover the gesture engine, layout resolver, volume maths, settings codec and
control spec factory. A Robolectric test dispatches real motion events into the real
`OverlayControlView`.
