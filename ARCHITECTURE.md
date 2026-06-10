# BillSplit — Architecture

BillSplit is an Android app for splitting restaurant bills. Users add people, photograph a receipt, assign each item to whoever ordered it, optionally add a tip or adjustment, and the app computes each person's exact share and generates Venmo charge links.

---

## User Flow

1. **Home** — manage the guest list; toggle who is on this bill from a saved roster
2. **Camera** — photograph the receipt; the image is sent directly to Claude for extraction
3. **Item Assignment** — review extracted items, fix names/prices, assign each item to people; adjust receipt total and tip
4. **Summary** — see each person's itemized share; send Venmo charge requests
5. **History** — browse and re-open past bills

Navigation uses Jetpack Compose Navigation with slide transitions. All five screens share a single `BillViewModel` instance scoped to the navigation graph.

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
│   └── ClaudeReceiptParser.kt    — vision API call + JSON parsing
├── data/
│   ├── PeopleRepository.kt       — DataStore: saved person roster
│   └── BillHistoryRepository.kt  — DataStore: past bills
└── ui/
    ├── screens/
    │   ├── HomeScreen.kt
    │   ├── CameraScreen.kt
    │   ├── ItemAssignmentScreen.kt
    │   ├── SummaryScreen.kt
    │   └── HistoryScreen.kt
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

── computed ────────────────────────────────────────────────────────────────
subtotal             = sum of all item prices
totalFees            = tax + tip + otherFees
effectiveReceiptTotal = userTotal ?: (receiptTotal if > 0 else subtotal+totalFees)
effectiveTotal        = effectiveReceiptTotal + adjustments

finalShares: Map<String, Double>   (lazy — computed once per BillSession instance)
  Each person's share = (their food subtotal / bill subtotal) × effectiveTotal
  Rounded via largest-remainder method so shares sum exactly to effectiveTotal.

foodShareFor(personId)   = sum of item.shareFor(personId) across all items
feeShareFor(personId)    = totalFor(personId) - foodShareFor(personId)
fractionFor(personId)    = foodShareFor(personId) / subtotal
```

### `SavedBill`
A frozen snapshot of a completed session, persisted to history. Contains the same fields as `BillSession` plus `savedAt` (epoch ms) and a pre-computed `finalShares` map. Adjustments are folded into `grandTotal` at save time.

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

Rounding: every share is floored to cents; the leftover cents are distributed one at a time to the people with the largest fractional remainders (largest-remainder method). This guarantees `Σ total(P) == effectiveTotal` exactly.

---

## Receipt Parsing Pipeline

```
Camera bitmap
      │
      ▼
ClaudeReceiptParser.parse(bitmap)
  1. Scale bitmap to ≤ 1568px on longest dimension
  2. JPEG-compress at 85% quality
  3. Base64-encode
  4. POST to https://api.anthropic.com/v1/messages
       model: claude-sonnet-4-6
       temperature: 0
       content: [image block, text prompt]
  5. Parse JSON from response
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

---

## Venmo Deep Link

When a person has a `venmoUsername` and their total > $0, Summary shows a charge button. Tapping it:

1. Builds a note string:
   ```
   Pacific Catch: Salmon ($20.95) + Fries ÷2 ($4.00) + Fees/Tip ($7.42=18%) = $32.37
   ```
   - Per-item amounts are each person's actual share (÷N for shared items)
   - Fees/tip shown as dollar amount + percentage of food subtotal
   - Smart truncation: if > 280 chars, collapses items to `N items ($X.XX)`; last resort appends `…` at 277 chars

2. Fires `Intent.ACTION_VIEW` with:
   ```
   https://venmo.com/?txn=charge&audience=private
     &recipients=<venmoUsername>
     &amount=<total>
     &note=<URL-encoded note>
   ```

3. Marks the person as requested in `session.venmoRequestedPersonIds` (shown as a checkmark in the UI).

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

---

## ViewModel

`BillViewModel` (AndroidViewModel) is the single source of truth for the active session and drives all screens.

```
State flows:
  session             StateFlow<BillSession>           active bill being worked on
  isProcessing        StateFlow<Boolean>               true while Claude API call is in flight
  errorMessage        StateFlow<String?>               shown as inline error on ItemAssignment
  saveMessage         StateFlow<String?>               shown as snackbar on Summary
  savedPeople         from PeopleRepository.people
  billHistory         from BillHistoryRepository.bills

Key mutations:
  processReceiptImage(bitmap)     → cancels previous job, calls ClaudeReceiptParser, updates session
  assignItem(itemId, personIds)   → updates item.assignedPersonIds
  addLineItem / removeLineItem    → manual item management
  updateLineItemPrice(itemId, $)  → price correction
  setUserTotal($)                 → overrides effectiveReceiptTotal
  setAdjustments($)               → sets tip/adjustment amount
  setRestaurantName(name)
  togglePersonInBill(personId)    → add/remove from session.people
  addNewPerson / updatePerson / deleteSavedPerson  → roster management (all atomic)
  toggleVenmoRequested(personId)
  saveCurrentBill()               → snapshots session → SavedBill → BillHistoryRepository
  loadBillIntoSession(bill)       → restores a SavedBill into session for re-display
  resetSession()                  → clears session and error for a new bill
```

---

## Screens

### HomeScreen
- Lists saved people with toggle chips (in/out of current bill)
- Add person: bottom sheet with name + optional Venmo username
- Edit/delete via long-press menu
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

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Architecture | Single-Activity, MVVM (AndroidViewModel) |
| Camera | CameraX |
| Networking | OkHttp (manual JSON via `org.json`) |
| Persistence | Jetpack DataStore (Preferences) |
| AI | Claude claude-sonnet-4-6 via Anthropic Messages API (vision) |
