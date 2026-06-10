# BillSplit

An Android app for splitting restaurant bills with friends. Scan a receipt, assign items to people, and get a per-person breakdown with proportional tax and tip — all in a few taps.

## Features

- **Receipt scanning** — photograph a receipt and ML Kit OCR + Claude Sonnet extract the line items, tax, tip, and total automatically
- **Inline assignment** — tap person avatars directly on each item to assign it; no dialogs to open
- **Proportional fees** — tax, tip, and other fees are split proportionally to each person's food share using the largest-remainder method so amounts always add up exactly
- **Persistent people** — saved people with color avatars carry over between sessions; toggle who's on a given bill without re-entering names
- **Bill history** — save splits and review past bills
- **Venmo deep links** — one tap to request payment from each person
- **Discrepancy detection** — warns if the computed total differs from the receipt total by more than $0.02

## Setup

### Prerequisites

- Android Studio
- Android SDK (API 26+)
- An [Anthropic API key](https://console.anthropic.com/)

### API key

Create or edit `local.properties` in the project root and add:

```
ANTHROPIC_API_KEY=sk-ant-...
```

This file is gitignored and never committed.

### Build

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- CameraX for capture
- ML Kit Text Recognition for OCR
- Claude Sonnet (`claude-sonnet-4-6`) via Anthropic API for contextual receipt parsing
- DataStore Preferences for persistent storage
- Navigation Compose with slide transitions
