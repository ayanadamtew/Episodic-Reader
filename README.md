# Episodic Reader

Offline-first Android reader implemented from the locked v1.1 product, SRS, roadmap, architecture, and UI/UX specifications in this workspace.

## Roadmap status

- T-01: Android project shell, offline manifest, and package boundaries - implemented
- T-02: Room schema, transactional repositories, and spoiler-safe queries - implemented
- T-03 through T-22: pending, to be completed in roadmap dependency order

## Build

The project targets Android 8.0+ (API 26) and compiles against API 36.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

The app intentionally declares no network permissions and disables Android cloud backup.
