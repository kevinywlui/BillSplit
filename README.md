# BillSplit

An Android app for splitting restaurant bills with friends. Scan a receipt, assign items to people, and get a per-person breakdown with proportional tax and tip — all in a few taps.

## Features

- **Receipt scanning** — photograph a receipt and Claude reads the line items, tax, tip, and total automatically. The model is selectable in **Settings** (Claude Sonnet 4.6 by default; a more capable model can be chosen for tricky receipts)
- **Inline assignment** — tap person avatars directly on each item to assign it; no dialogs to open
- **Proportional fees** — tax, tip, and other fees are split proportionally to each person's food share using the largest-remainder method so amounts always add up exactly
- **Persistent people** — saved people with color avatars carry over between sessions; toggle who's on a given bill without re-entering names
- **Bill history** — save splits and review past bills
- **Venmo deep links** — one tap to request payment from each person (the prefilled
  amount/note is honored by Venmo's desktop web; the mobile app and mobile site ignore
  them, so the in-app button notes this)
- **Discrepancy detection** — warns if the computed total differs from the receipt total by more than $0.02

## Setup

### Prerequisites

- Android Studio (or the command-line Android SDK + Gradle wrapper)
- Android SDK platform 34 and build-tools (`minSdk` 26, `compile`/`targetSdk` 34)
- JDK 21 (used to run Gradle; the project compiles to JVM 11 bytecode per `build.gradle.kts`).
  The JDK-21-runner / JVM-11-target split is deliberate — see [CLAUDE.md](CLAUDE.md#build--test).
- An [Anthropic API key](https://console.anthropic.com/settings/keys) — optional; only receipt
  scanning needs it, items can also be added manually

### API key

Receipt scanning uses Claude. The key is entered **in the app at runtime**, not
baked into the build: open **Settings** (gear icon on the home screen) and paste
your [Anthropic API key](https://console.anthropic.com/settings/keys). It's stored
only on-device (DataStore) and is never bundled into the APK. Without a key you can
still add items manually.

### Receipt model

The same Settings screen has a **Receipt model** picker. Claude Sonnet 4.6 is the
default — it parses receipts well at roughly ~1¢ per scan. Haiku is cheaper/faster
and Opus is more capable, at higher cost and slightly more latency. Choosing
**Custom…** lets you enter any Anthropic model id (e.g. a dated snapshot or a newly
released model) without waiting for an app update. The choice is stored on-device and
applied to every subsequent scan; an unrecognized id simply fails the next scan with
the normal error, so you can switch back.

### Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # run the unit tests
```

Install on a connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

For signed release builds see [Releases](#releases). Contributors should read
[CONTRIBUTING.md](CONTRIBUTING.md).

## Data compatibility

> **Policy: persisted data must stay readable from one release to the next.**

The app stores bill history and the saved-people roster on-device (DataStore). A
user who updates the app must never lose that data. Concretely, when changing any
persisted model (`SavedBill`, `Person`, `LineItem`, …):

- **Additive/removable fields are safe** — `kotlinx.serialization` is configured
  with `ignoreUnknownKeys = true` and field defaults, so old data still deserializes.
- **Incompatible changes** (renaming a field, changing its meaning or units) require
  a migration: bump `HISTORY_SCHEMA_VERSION` in `BillHistoryRepository` and add a
  `when (version)` branch in `deserialize()` that upgrades old payloads. The version
  is stamped into every write precisely so old data is identifiable.
- **Never ship a change that drops or corrupts existing bills.** If you can't migrate
  cleanly, fall back to skipping the bad entry (as `deserialize` already does), not
  wiping history.

See *Persistence* in [ARCHITECTURE.md](ARCHITECTURE.md) for details.

## Releases

Releases are cut by pushing a `v*` Git tag (e.g. `git tag v1.2.1 && git push origin v1.2.1`).
The [`release.yml`](.github/workflows/release.yml) workflow then builds a **signed** release
APK and publishes it as a GitHub Release with auto-generated notes.

- `versionName` comes from the tag (the leading `v` is stripped); `versionCode` is the
  workflow run number. Locally these default to `1.0` / `1` unless you pass
  `-PversionName` / `-PversionCode`.
- Signing keys come from repository secrets, decoded at build time. Local `assembleRelease`
  builds stay **unsigned** because no keystore env vars are present.
- Every push to `main` and every pull request runs [`android.yml`](.github/workflows/android.yml):
  unit tests (`testDebugUnitTest`) then `assembleDebug`, uploading the debug APK as an artifact.

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- CameraX for receipt capture
- Claude via the Anthropic Messages API for vision-based receipt parsing — the image is
  sent directly to Claude (no on-device OCR step); model selectable in Settings
  (`claude-sonnet-4-6` by default)
- Jetpack DataStore (Preferences) for persistent storage
- OkHttp for the Anthropic API call; JSON is assembled/parsed with `org.json` (provided to
  the app at runtime by the Android SDK's `android.jar` — declared in `build.gradle.kts` only
  in `testImplementation` scope, since the local JVM tests have no `android.jar`)
- Navigation Compose with slide transitions
