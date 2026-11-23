# Episodic Reader (Android v1.1)

Offline-first Android episodic book reader built in Kotlin, Jetpack Compose (Material 3), and Room (SQLite) following the approved v1.1 specifications and 22-task MVP Development Roadmap (`T-01` through `T-22`).

## Core Capabilities
- **100% Offline & Private**: Zero network permissions (`INTERNET` and `ACCESS_NETWORK_STATE` absent in the merged manifest), zero telemetry, zero login, `android:allowBackup="false"`.
- **Pre-Bundled Curated Starter Classic**: *White Nights* by Fyodor Dostoevsky seeded locally on first launch with 6 pre-cut 900–1,200 word episodes, 5 curated character profiles with alias tone notes, spoiler-locked progressive facts, and 3-bullet recaps.
- **Local EPUB & Text-Based PDF Import**: Storage Access Framework (`ACTION_OPEN_DOCUMENT`) import with magic-byte validation, 50 MB / storage headroom guards, SHA-256 duplicate detection (`DLG-01c`), DRM/password/scanned-PDF rejection (`ERR-001`..`ERR-008`), startup orphan cleanup, and deterministic 900–1,200 word episode slicing without splitting paragraphs (`BR-002`).
- **Calm Episodic Reading & Display Settings**: Single-column reflowable view with hidden total book length (`BR-001`), 2dp intra-episode progress hairline, live Light / Sepia / High-Contrast Dark WCAG 2.1 AA themes (`>= 4.5:1`), 12–28sp text sizing (2sp steps), and Compact/Standard/Relaxed line height (zero Bionic bold-prefix styling per `BR-014`).
- **Spoiler-Free Memory & 48-Hour Recaps**: Monotonic `max_read_seq` watermark (`BR-003`), progress-gated (`seq_index <= max_read_seq`) curated character glossary (`SH-02`) and deterministic prior-mentions lookup (`SH-03`), plus automatic 48-hour / manual "Previously On..." recaps (`OV-01`, `ERR-010` negative-clock clamp).
- **Local Habit Loop**: Daily episode count, consecutive-day local streak (silent reset on missed days), 7-day activity count, and all-time totals (`SH-04`) preserved across book deletions (`ON DELETE SET NULL`, `BR-013`).

## Roadmap Status
All 22 MVP tasks (`T-01`–`T-22`) implemented and verified via JVM unit tests, WCAG AA contrast checks, offline merged-manifest audit, and debug APK compilation.

## Build & Verification
```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```
