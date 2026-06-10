package com.kevinywlui.billsplit.util

import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.Person
import java.net.URLEncoder

/** Venmo caps payment notes around this length; longer notes get cut off. */
internal const val VENMO_NOTE_MAX_LENGTH = 280

/**
 * Reduces whatever the user typed or pasted into a bare Venmo username:
 * strips whitespace, a leading "@" (Venmo displays handles with one, so it's
 * what people copy), and pasted profile links like
 * "https://account.venmo.com/u/Kevin-Lui" or "venmo.com/Kevin-Lui".
 *
 * Deliberately lenient — no charset/length checks — so a valid username can
 * never be rejected; the worst case is the same broken link the raw input
 * would have produced anyway.
 */
fun normalizeVenmoUsername(raw: String): String {
    var u = raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
    for (host in listOf("account.venmo.com/", "venmo.com/")) {
        if (u.startsWith(host, ignoreCase = true)) {
            u = u.substring(host.length).removePrefix("u/")
            break
        }
    }
    return u.substringBefore('?').trimEnd('/').removePrefix("@").trim()
}

/**
 * The charge deep link. The `?recipients=` query form is what Venmo's desktop
 * web honors (confirmed working on desktop); the mobile app and mobile site
 * ignore the amount/note prefill — see the help dialog on the Summary screen.
 *
 * Both `recipients` and `note` are percent-encoded. Spaces become "%20" rather
 * than URLEncoder's "+": "%20" decodes to a space under both RFC 3986 and
 * form-style query parsing, while "+" survives as a literal plus under the
 * former. The username is normalized again here so rosters saved before
 * normalization existed (e.g. with a leading "@") still produce working links.
 */
fun buildVenmoChargeUrl(venmoUsername: String, amount: Double, note: String): String =
    "https://venmo.com/?txn=charge&audience=private" +
        "&recipients=${encodeQueryParam(normalizeVenmoUsername(venmoUsername))}" +
        "&amount=${Money.format(amount)}" +
        "&note=${encodeQueryParam(note)}"

private fun encodeQueryParam(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/**
 * One person's charge note, e.g.:
 *
 *     Luigi's: Pizza ($12.00) + Guac ÷2 ($3.50) + Fees/Tip ($4.65=30%) = $20.15
 *
 * Kept within [VENMO_NOTE_MAX_LENGTH] by degrading in steps that each preserve
 * the arithmetic: itemized → items collapsed to a count → restaurant prefix
 * dropped → (last resort) hard truncation.
 */
fun buildVenmoNote(session: BillSession, person: Person): String {
    val total = session.totalFor(person.id)
    val foodShare = session.foodShareFor(person.id)
    val feeShare = session.feeShareFor(person.id)
    // A bill with no fees/tip/override shouldn't show a sub-cent rounding remnant as a fee.
    val showFees = (session.effectiveTotal - session.subtotal) > 0.005 && feeShare > 0.005
    val assignedItems = session.items.filter { person.id in it.assignedPersonIds }

    val prefix = if (session.restaurantName.isNotBlank()) "${session.restaurantName}: " else ""
    val feesPart = if (showFees) {
        val pct = if (foodShare > 0) (feeShare / foodShare) * 100 else 0.0
        "Fees/Tip (${Money.dollars(feeShare)}=${Money.percent(pct)})"
    } else null
    val totalPart = "= ${Money.dollars(total)}"

    fun assemble(prefix: String, itemParts: List<String>): String {
        val parts = buildList { addAll(itemParts); if (feesPart != null) add(feesPart) }
        return "$prefix${parts.joinToString(" + ")} $totalPart"
    }

    val itemParts = assignedItems.map { item ->
        val label = if (item.assignedPersonIds.size > 1)
            "${item.name} ÷${item.assignedPersonIds.size}" else item.name
        "$label (${Money.dollars(item.shareFor(person.id))})"
    }

    val full = assemble(prefix, itemParts)
    if (full.length <= VENMO_NOTE_MAX_LENGTH) return full

    // Collapse individual items into a count summary
    val collapsedItems = listOf("${assignedItems.size} items (${Money.dollars(foodShare)})")
    val collapsed = assemble(prefix, collapsedItems)
    if (collapsed.length <= VENMO_NOTE_MAX_LENGTH) return collapsed

    // Drop the restaurant prefix rather than truncate into the amounts
    val noPrefix = assemble("", collapsedItems)
    if (noPrefix.length <= VENMO_NOTE_MAX_LENGTH) return noPrefix

    // Last resort: hard truncate with ellipsis
    return noPrefix.take(VENMO_NOTE_MAX_LENGTH - 3) + "..."
}
