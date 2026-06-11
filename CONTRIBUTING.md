# Contributing to BillSplit

Thanks for helping out. This guide covers cloning, building, testing, and cutting a release.
For how the app is put together, read [ARCHITECTURE.md](ARCHITECTURE.md); for an end-user
overview, see the [README](README.md).

## Prerequisites

- JDK 21 (used to run Gradle; the project compiles to JVM 11 bytecode)
- Android SDK platform 34 and build-tools 34.0.0 (`minSdk` 26, `compile`/`targetSdk` 34)
- Android Studio is convenient but not required — the Gradle wrapper (`./gradlew`) is enough

The wrapper pins the Gradle version, so you do not need a system Gradle install.

## Clone, build, test

```bash
git clone https://github.com/kevinywlui/BillSplit.git
cd BillSplit

./gradlew testDebugUnitTest   # unit tests (parser, money, bill session, share text, serialization)
./gradlew assembleDebug       # debug APK → app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Receipt scanning needs an Anthropic API key, entered **in the app** under Settings (it is
never bundled into the build). Without one you can still add items manually and exercise the
rest of the app.

## Tests

Unit tests live in `app/src/test/java/com/kevinywlui/billsplit/` and run on the local JVM (no
device/emulator):

- `MoneyTest` — locale-safe currency/percent formatting
- `BillSessionTest` — share math and largest-remainder rounding
- `ClaudeReceiptParserTest` — JSON extraction and HTTP-status → error mapping
- `ShareTextTest` — plain-text group-chat breakdown
- `BillHistorySerializationTest` — persisted-history round-trip **and the legacy bare-array path**

Run them with `./gradlew testDebugUnitTest`. CI runs exactly this on every push to `main` and
every pull request (see [`.github/workflows/android.yml`](.github/workflows/android.yml)).

## Changing persisted data

Bill history and the people roster are stored on-device (DataStore) and **must stay readable
across releases**. Before touching `SavedBill`, `Person`, `LineItem`, or any other persisted
model, read the data-compatibility policy in [ARCHITECTURE.md](ARCHITECTURE.md#data-compatibility-policy):

- Additive/removable field changes are safe (defaults + `ignoreUnknownKeys`).
- Incompatible changes (rename, re-meaning, unit change) require bumping
  `HISTORY_SCHEMA_VERSION` and adding a migration branch in `deserialize()`.
- Extend `BillHistorySerializationTest` to guard any migration you add.

## Cutting a release

Releases are produced by [`.github/workflows/release.yml`](.github/workflows/release.yml), which
triggers on pushing a `v*` tag:

```bash
git tag v1.2.2
git push origin v1.2.2
```

The workflow builds a **signed** release APK and publishes a GitHub Release with auto-generated
notes. `versionName` is derived from the tag (leading `v` stripped) and `versionCode` is the
workflow run number — you do not edit these by hand. Signing keys come from repository secrets
(`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`).
Local `assembleRelease` builds are intentionally **unsigned** because those secrets are absent.

## Pull requests

- Keep changes focused; run `./gradlew testDebugUnitTest` before opening a PR.
- Update [ARCHITECTURE.md](ARCHITECTURE.md) / [README.md](README.md) when behavior or structure changes.
- If you change agent-relevant gotchas, also update [CLAUDE.md](CLAUDE.md). These are the items
  in its *Hard rules / gotchas* section: persisted-data migrations, the no-bundled-key rule,
  locale-sensitive money formatting, the tag-driven release process, the build prerequisites
  (JDK-21-runner / JVM-11-target split), and the no-documenting-`build/`-output rule.
- Add or extend a unit test for any non-trivial logic change.
