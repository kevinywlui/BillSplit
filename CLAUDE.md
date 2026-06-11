# CLAUDE.md

Guidance for AI agents (Claude Code and similar) working in this repository. Humans should
start with the [README](README.md), [ARCHITECTURE.md](ARCHITECTURE.md), and
[CONTRIBUTING.md](CONTRIBUTING.md) — this file only adds agent-specific orientation and
gotchas, it does not duplicate them.

## What this is

BillSplit is an Android app (Kotlin + Jetpack Compose, Material 3) that splits restaurant
bills: photograph a receipt, Claude's vision API extracts line items / tax / tip / total,
assign items to people, compute per-person shares, send Venmo charge links. Single-Activity,
single shared `BillViewModel`. Package root: `com.kevinywlui.billsplit`.

## Build & test

```bash
./gradlew testDebugUnitTest   # unit tests — fast, no device needed; run this after changes
./gradlew assembleDebug       # debug APK
```

- JDK 21 runs Gradle; bytecode target is JVM 11 (`build.gradle.kts`). Don't "fix" this mismatch
  — it is deliberate.
- Tests are pure-JVM in `app/src/test/java/com/kevinywlui/billsplit/`. There is no emulator in CI.

## Where things live (verify before trusting)

- `ocr/ClaudeReceiptParser.kt` — the only network call. Builds the Anthropic `/v1/messages`
  request by hand with `org.json` (the Android SDK's built-in copy, not a declared dependency)
  + OkHttp, scales the image to ≤1568px, extracts the first `{…}` from the response. There is
  **no on-device OCR** (ML Kit is declared in the version catalog but is *not* an app dependency
  and *not* imported — do not document or add it without a real reason).
- `ocr/ReceiptModel.kt` — registry of selectable vision models. The chosen id is sent to the
  API verbatim; a custom user-entered id is also valid. Default is `claude-sonnet-4-6`.
- `model/BillSession.kt` — the money model. All totals are computed properties; see its KDoc.
  Splits use the **largest-remainder method** and target the rounded sum of exact shares (not
  `effectiveTotal`) so unassigned items don't sprinkle stray cents.
- `viewmodel/BillViewModel.kt` — single source of truth; repository flows re-published as
  StateFlows; cancelable receipt job.
- `data/*Repository.kt` — DataStore (Preferences) storing JSON strings. History is wrapped in a
  versioned `BillHistoryEnvelope`.
- `util/Money.kt` — all currency/percent strings go through here, pinned to `Locale.US` because
  these strings flow into Venmo deep-link `amount=` params and are re-parsed downstream; a
  comma-decimal locale (e.g. `de-DE`) would break the parse.

## Hard rules / gotchas

- **Persisted data must stay readable across releases.** Before changing any `@Serializable`
  model, follow the data-compatibility policy in ARCHITECTURE.md (defaults + `ignoreUnknownKeys`
  for additive/removable; bump `HISTORY_SCHEMA_VERSION` + add a migration for incompatible
  changes). Extend `BillHistorySerializationTest` for any migration.
- **Never bundle the Anthropic API key.** It is entered at runtime and stored on-device only
  (`SettingsRepository`), never in `BuildConfig` or the APK.
- **Money formatting is locale-sensitive.** Use `Money.*`; do not hand-roll `String.format`
  with the default locale.
- **Releases are tag-driven.** `versionName`/`versionCode` come from the tag and CI run number;
  don't hardcode them. Don't commit signing material — keys come from repository secrets.
- **Don't document generated output** under `build/`.

## When you change behavior

Update the docs you touched (README ↔ ARCHITECTURE are cross-linked; keep them DRY) and add or
extend a unit test. Run `./gradlew testDebugUnitTest` before finishing.
