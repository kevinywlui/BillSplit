# BillSplit — Architecture

BillSplit is an Android app for splitting restaurant bills. Users add people, photograph a receipt, assign each item to whoever ordered it, optionally add a tip or adjustment, and the app computes each person's exact share and generates Venmo charge links.

---

## User Flow

1. **Home** — manage the guest list; toggle who is on this bill from a saved roster
2. **Camera** — photograph the receipt; the image is sent directly to Claude for extraction
3. **Item Assignment** — review extracted items, fix names/prices, assign each item to people; adjust receipt total and tip
4. **Summary** — see each person's itemized share; send Venmo charge requests
5. **History** — browse and re-open past bills
6. **Settings** — Anthropic API key and receipt-model picker (reachable from Home)

Navigation uses Jetpack Compose Navigation with slide transitions. All screens share a single `BillViewModel` instance scoped to the navigation graph.

For an end-user feature overview and setup steps, see the [README](README.md).

---

## Package Structure

```
com.kevinywlui.billsplit
├── MainActivity.kt               — single-activity host
├── navigation/
│   └── AppNavigation.kt          — NavHost + Screen sealed class
├── model/
│   ├── Person.kt                 — id, name, venmoUsername, avatarColorIndex
│   ├── LineItem.kt               — id, name, price, assignedPersonIds; shareFor()
│   ├── BillSession.kt            — live session state + all computed totals
│   ├── ParsedReceipt.kt          — raw output from ClaudeReceiptParser
│   └── SavedBill.kt              — snapshot persisted to history
├── viewmodel/
│   └── BillViewModel.kt          — single ViewModel bridging all screens
├── ocr/
│   ├── ClaudeReceiptParser.kt    — vision API call + JSON parsing
│   └── ReceiptModel.kt           — registry of selectable vision models
├── data/
│   ├── PeopleRepository.kt       — DataStore: saved person roster
│   ├── BillHistoryRepository.kt  — DataStore: past bills (versioned)
│   └── SettingsRepository.kt     — DataStore: API key + receipt model id
├── util/
│   ├── Money.kt                  — locale-safe currency/percent formatting
│   └── ShareText.kt              — plain-text group-chat breakdown
└── ui/
    ├── screens/
    │   ├── HomeScreen.kt
    │   ├── CameraScreen.kt
    │   ├── ItemAssignmentScreen.kt
    │   ├── SummaryScreen.kt
    │   ├── HistoryScreen.kt
    │   └── SettingsScreen.kt
    ├── components/
    │   └── PersonAvatar.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## Data Model

### `Person`
```
id               UUID string (stable across sessions)
name             display name
venmoUsername    optional; enables Venmo deep link on Summary screen
avatarColorIndex index into a fixed palette for avatar background color
```

### `LineItem`
```
id               UUID string
name             item name as extracted / edited by user
price            full item price (not per-person)
assignedPersonIds list of Person IDs who share this item

shareFor(personId) = price / assignedPersonIds.size   (0 if not assigned)
isAssigned         = assignedPersonIds.isNotEmpty()
```

### `BillSession` (live session, computed properties)
```
people               List<Person>          participants in this bill
items                List<LineItem>        all line items
tax                  Double                from receipt (Claude-extracted)
tip                  Double                from receipt (Claude-extracted)
otherFees            Double                surcharges, delivery, etc.
receiptTotal         Double                total printed on receipt (Claude-extracted)
userTotal            Double?               user override of receipt total (null = use Claude value)
adjustments          Double                user-added flat amount on top (tip entered manually)
restaurantName       String
venmoRequestedPersonIds  Set<String>       tracks who has been charged via Venmo
receiptImagePath     String?               on-disk path of the saved receipt photo
receiptTotalFromOcr  Boolean               true only for a freshly scanned bill;
                                           gates the discrepancy warning so it never
                                           fires for manual or reloaded-history bills

── computed ────────────────────────────────────────────────────────────────
subtotal             = sum of all item prices
totalFees            = tax + tip + otherFees
grandTotal           = subtotal + totalFees
effectiveReceiptTotal = userTotal ?: (receiptTotal if > 0 else grandTotal)
effectiveTotal        = effectiveReceiptTotal + adjustments

finalShares: Map<String, Double>   (lazy — computed once per BillSession instance)
  Each person's share = (their food subtotal / bill subtotal) × effectiveTotal
  Rounded via largest-remainder method (see Fee / Tip Arithmetic below).
  Falls back to an even split when subtotal is 0 (no priced items yet).

totalFor(personId)       = finalShares[personId] ?: 0
foodShareFor(personId)   = sum of item.shareFor(personId) across all items
feeShareFor(personId)    = totalFor(personId) - foodShareFor(personId)
fractionFor(personId)    = foodShareFor(personId) / subtotal

hasReceiptDiscrepancy(ε = 0.02)  true if this is a scanned bill and |grandTotal −
                                 receiptTotal| > ε  (drives the Summary warning)
receiptDiscrepancy       = grandTotal − receiptTotal
```

### `SavedBill`
A frozen snapshot of a completed session, persisted to history. Carries the people, items,
`tax`/`tip`/`otherFees`, `restaurantName`, `venmoRequestedPersonIds`, and `receiptImagePath`,
plus `savedAt` (epoch ms) and a pre-computed `finalShares` map. There is no separate
adjustments/userTotal field — the session's `effectiveTotal` (override and tip already folded in)
is stored as `grandTotal`. On reload, `loadBillIntoSession` maps `grandTotal` back into
`receiptTotal`, so a reopened bill reproduces the same shares.

### `ParsedReceipt`
Transient struct returned by `ClaudeReceiptParser`. Same numeric fields as `BillSession` minus session-specific state. The ViewModel maps it into a `BillSession` update immediately after parsing.

---

## Fee / Tip Arithmetic

```
effectiveReceiptTotal  =  userTotal  OR  receiptTotal  OR  subtotal+totalFees

effectiveTotal  =  effectiveReceiptTotal + adjustments
                              ▲
                     user-entered tip (flat $ or % of effectiveReceiptTotal
                     or derived from a user-specified final total)

For each person P:
  foodShare(P)  =  Σ item.shareFor(P)
  total(P)      =  (foodShare(P) / subtotal) × effectiveTotal
```

Rounding: every share is floored to cents; the leftover cents are distributed one at a time to the people with the largest fractional remainders (largest-remainder method). The distribution targets the **rounded sum of the exact shares**, not `effectiveTotal` directly — when some items are left unassigned the exact shares legitimately sum to less than `effectiveTotal`, and targeting `effectiveTotal` would sprinkle stray cents onto arbitrary people. So `Σ total(P)` equals `effectiveTotal` exactly when every item is assigned, and otherwise equals the (smaller) rounded sum of what was actually assigned. When `subtotal` is 0 (no priced items yet) the split falls back to an even division among participants.

---

## Receipt Parsing Pipeline

```
Camera bitmap
      │
      ▼
ClaudeReceiptParser.parse(bitmap)
  1. Scale bitmap to ≤ 1568px on longest dimension
     (same ≤1568px / 85% JPEG bound that BillViewModel.saveBitmapToFile already applied
      before writing the photo to disk — the parser re-scales independently so the API
      payload stays bounded regardless of how the bitmap reached it)
  2. JPEG-compress at 85% quality
  3. Base64-encode
  4. POST to https://api.anthropic.com/v1/messages
       model: user-selected (Settings → Receipt model; default claude-sonnet-4-6)
       max_tokens: 1024, temperature: 0
       content: [image block, text prompt]
       headers: x-api-key, anthropic-version: 2023-06-01
  5. Parse JSON from response (the first {…} substring is extracted, so a stray
     prose preamble does not break parsing)
      │
      ▼
ParsedReceipt { items, tax, tip, otherFees, receiptTotal, restaurantName }
      │
      ▼
BillViewModel updates BillSession
```

**Prompt rules:**
- Only food/drink items in the `items` array; tax/subtotal/total are not items
- Quantities > 1 are split into individual items at unit price
- Tax, tip, surcharges, delivery → separate fee fields
- `restaurantName` from the top of the receipt

OkHttp timeouts: connect 30s, write 60s, read 60s. The call is wrapped in `suspendCancellableCoroutine` so cancelling the coroutine also cancels the in-flight HTTP request.

**Error handling:** a non-2xx response (or empty body) raises `ReceiptParseException(statusCode, …)`.
The pure, unit-tested `classifyParseError(statusCode)` maps it to a coarse `ReceiptParseError`
category — `401/403 → AUTH`, `429 → RATE_LIMIT`, `5xx → SERVER`, `null → NETWORK`, else `UNKNOWN`.
`BillViewModel.processReceiptImage` catches the exception, turns the category into a friendly
sentence, and publishes it on `errorMessage` (shown as an inline error card on ItemAssignment).
A successful call that yields zero items is not an error: it sets `errorMessage` to
"No items detected. Add them manually." so the user can fall back to manual entry.

**Model selection:** `ReceiptModel` (in `ocr/`) is the registry of preset vision models — each
preset carries its Anthropic API `model` id, a display label, and a cost blurb. The pieces:

- **Storage** — `SettingsRepository` persists the chosen id as a raw string (`receipt_model_id`
  key). It may be a preset id or a **custom id** the user typed (Settings → Receipt model → Custom…).
- **Scan path** — the id is read fresh on each scan and passed straight into
  `ClaudeReceiptParser.parse(bitmap, key, model)`. The parser never consults the enum, so any
  valid Anthropic model id works without a code change.
- **Settings UI** — the picker maps a saved id back to a preset by exact match
  (`ReceiptModel.entries.find`), showing "Custom" when none matches.
- **Fallbacks** — a blank stored id falls back to `ReceiptModel.DEFAULT` (Sonnet 4.6); an
  unknown id simply fails the next scan with the usual error.

---

## Venmo Deep Link

When a person has a `venmoUsername` and their total > $0, Summary shows a charge button. Tapping it:

1. Builds a note string:
   ```
   Pacific Catch: Salmon ($20.95) + Fries ÷2 ($4.00) + Fees/Tip ($7.42=18%) = $32.37
   ```
   - Per-item amounts are each person's actual share (÷N for shared items)
   - Fees/tip shown as a dollar amount + percentage of *that person's* food share
     (`(feeShare / foodShare) * 100`, and `0%` when that person's food share is 0)
   - Smart truncation: if > 280 chars, collapses items to `N items ($X.XX)`; last resort hard-truncates to 277 chars + `...`

2. Fires `Intent.ACTION_VIEW` with:
   ```
   https://venmo.com/?txn=charge&audience=private
     &recipients=<venmoUsername>
     &amount=<total>
     &note=<URL-encoded note>
   ```

3. Marks the person as requested in `session.venmoRequestedPersonIds` (shown as a checkmark in the UI).

> Note: only Venmo's **desktop web** honors the prefilled `amount`/`note`; the mobile app and
> mobile site ignore them. The Summary screen surfaces this caveat in-app so users aren't surprised.

---

## Persistence

Both repositories use **Jetpack DataStore** (Preferences), storing JSON strings under a single key. All mutations use atomic `edit { }` blocks — reads happen inside the transaction, not from a cached snapshot.

### `PeopleRepository` (`people` DataStore)
- Key: `people_json`
- Stores the user's saved roster of people across all bills
- Read as a `Flow<List<Person>>`; mutated via `addPerson`, `updatePerson`, `deletePerson`
- `addPerson` computes `avatarColorIndex` from the current list length inside the transaction

### `BillHistoryRepository` (`bill_history` DataStore)
- Key: `bills_json`
- Stores a JSON array of `SavedBill` snapshots, newest first
- `saveBill()` prepends; `deleteBill()` filters by id
- Deserialization uses per-entry `mapNotNull` so a corrupt entry is skipped without wiping other bills

### Data compatibility policy

**Persisted data must remain readable from one release to the next — updating the
app must never wipe a user's bill history or roster.** This is a hard requirement,
not a best-effort goal.

- **Serialization is forgiving by design.** Both repositories configure `Json` with
  `ignoreUnknownKeys = true`, and every persisted field has a default;
  `BillHistoryRepository` additionally sets `encodeDefaults = true` so its envelope
  `version` is always written even when it equals the default. So *additive* changes
  (new field) and *removable* changes (drop a field old data still carries) are safe
  — old payloads keep deserializing.
- **Incompatible changes need a migration.** Renaming a field, changing its meaning,
  or changing units is **not** safe. For those: bump `HISTORY_SCHEMA_VERSION`, and
  add a `when (version)` branch in `deserialize()` that upgrades older payloads to the
  current shape. Bill history is wrapped in a versioned `BillHistoryEnvelope`
  (`{"version":N,"bills":[…]}`) for exactly this reason; the legacy bare-array form
  (pre-versioning) is still accepted on read.
- **Degrade, never destroy.** If an individual entry can't be read, skip it
  (`mapNotNull`) rather than discarding the whole store.

When you touch `SavedBill`, `Person`, `LineItem`, or any other persisted model, decide
which bucket the change falls into and handle it accordingly. `BillHistorySerializationTest`
guards the round-trip and the legacy-format path — extend it when you add a migration.

---

## ViewModel

`BillViewModel` (AndroidViewModel) is the single source of truth for the active session and drives all screens.

```
State flows:
  session             StateFlow<BillSession>           active bill being worked on
  isProcessing        StateFlow<Boolean>               true while Claude API call is in flight
  errorMessage        StateFlow<String?>               shown as inline error on ItemAssignment
  saveMessage         StateFlow<String?>               shown as snackbar on Summary
  savedPeople         StateFlow<List<Person>>          mirrors PeopleRepository.people
  billHistory         StateFlow<List<SavedBill>>       mirrors BillHistoryRepository.bills
  apiKey              StateFlow<String>                mirrors SettingsRepository (for Settings UI)
  receiptModelId      StateFlow<String>                raw chosen model id (preset or custom)

The four repository-backed flows (`savedPeople`, `billHistory`, `apiKey`, `receiptModelId`) are
collected in init and re-published as StateFlows, so every screen read goes through the ViewModel
— preserving the single-source-of-truth invariant.

Key mutations:
  processReceiptImage(bitmap)     → cancels previous job, saves the photo to disk, calls
                                    ClaudeReceiptParser, updates session (sets receiptTotalFromOcr)
  assignItem(itemId, personIds)   → updates item.assignedPersonIds
  addLineItem / removeLineItem    → manual item management
  updateLineItemPrice(itemId, $)  → price correction
  updateLineItemName(itemId, name)→ rename an item
  setUserTotal($)                 → overrides effectiveReceiptTotal
  setAdjustments($)               → sets tip/adjustment amount
  setRestaurantName(name)
  togglePersonInBill(personId)    → add/remove from session.people
  addNewPerson / updatePerson / deleteSavedPerson  → roster management (all atomic)
  toggleVenmoRequested(personId)
  setApiKey / setReceiptModelId   → persist Settings (blank model id is ignored)
  saveCurrentBill()               → snapshots session → SavedBill → BillHistoryRepository
  loadBillIntoSession(bill)       → restores a SavedBill into session for re-display
  deleteBill(billId)              → removes a saved bill (and its orphaned receipt image)
  resetSession()                  → clears session and error for a new bill
  clearError / clearSaveMessage   → dismiss transient UI messages
```

Receipt photos are saved under `filesDir/receipts/<uuid>.jpg` (downscaled to ≤1568px) and
referenced by path from the session and `SavedBill`. The ViewModel deletes orphaned images
when a scan is replaced, a session is reset, or a saved bill is deleted, so the directory
does not accumulate stale files.

---

## Screens

### HomeScreen
- Lists saved people as `SavedPersonCard`s; tapping a card toggles that person in/out of the
  current bill (a check icon marks those who are in)
- Add person: bottom sheet with name + optional Venmo username
- Edit/delete via the per-card edit icon, which opens a modal bottom sheet (delete is confirmed
  with a dialog)
- "Scan Receipt" → Camera; "History" → History

### CameraScreen
- CameraX preview with capture button
- On capture: bitmap handed to ViewModel, navigate to ItemAssignment

### ItemAssignmentScreen
- Scrollable list of `LineItem` cards; each has avatar chips for assignment
- Tap avatar chip → toggle that person on the item
- Tap item price → edit price sheet
- "Add item" button → add name + price manually
- **Totals card**: restaurant name (editable), receipt total (editable), adjustments
  - Adjustments row shows `$X.XX (Y%)` when non-zero
  - Edit sheet has three modes: **$ Amount** / **% Tip** / **$ Total**
    - % Tip: enter percentage, shows dollar equivalent live
    - $ Total: enter final total, shows `adjustment = $X.XX (Y.0%)`
- "View Summary" → Summary

### SummaryScreen
- Bill totals card: food subtotal, fees/taxes/tip, effective total
- Per-person card: itemized food items, fee share, bold total, Venmo button
- Unassigned items warning (error card)
- "Save Bill" → persists to history; "New Bill" → reset + Home

### HistoryScreen
- Chronological list of saved bills
- Tap → loads into session, navigates to Summary
- Swipe to delete

### SettingsScreen
- Anthropic API key field (stored on-device via `SettingsRepository`)
- Receipt-model picker over `ReceiptModel.entries`, plus a **Custom…** free-form id field
- Reachable from the Home screen; no `BillSession` interaction

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Architecture | Single-Activity, MVVM (AndroidViewModel) |
| Camera | CameraX |
| Networking | OkHttp (manual JSON via `org.json`, from the Android SDK) |
| Persistence | Jetpack DataStore (Preferences) |
| AI | Claude via Anthropic Messages API (vision); model selectable in Settings, default `claude-sonnet-4-6` |
