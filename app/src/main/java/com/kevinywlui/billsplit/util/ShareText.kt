package com.kevinywlui.billsplit.util

import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.Person

private const val ATTRIBUTION = "split with Bill Split"

/**
 * One person's plain-text line for a group-chat breakdown, e.g.:
 *
 *     Alice — $24.50
 *       Pizza ($12.00)
 *       Guac ÷2 ($3.50)
 *       Fees, taxes & tip: $9.00
 *
 * The fee line is derived as `total - food` so the breakdown always reconciles
 * to the authoritative per-person total ([BillSession.totalFor]).
 */
fun buildPersonShareLine(session: BillSession, person: Person): String {
    val total = session.totalFor(person.id)
    val foodShare = session.foodShareFor(person.id)
    val feeShare = total - foodShare
    // Only a bill with actual fees/tip/override shows a fee line — otherwise a
    // sub-cent rounding remainder would print a phantom "$0.01" fee.
    val billHasFees = (session.effectiveTotal - session.subtotal) > 0.005
    val items = session.items.filter { person.id in it.assignedPersonIds }
    return buildString {
        append(person.name).append(" — ").append(Money.dollars(total))
        items.forEach { item ->
            val label = if (item.assignedPersonIds.size > 1)
                "${item.name} ÷${item.assignedPersonIds.size}" else item.name
            append("\n  ").append(label).append(" (").append(Money.dollars(item.shareFor(person.id))).append(")")
        }
        if (billHasFees && feeShare > 0.005) {
            append("\n  Fees, taxes & tip: ").append(Money.dollars(feeShare))
        }
    }
}

/**
 * The whole bill as plain text for pasting into a group chat. No markdown —
 * SMS/WhatsApp/Signal render `*`/`#`/`-` literally, so this uses only line
 * breaks and spaces.
 */
fun buildShareText(session: BillSession): String = buildString {
    val header = session.restaurantName.ifBlank { "Bill Split" }
    // Use the summed per-person totals (what's actually being split) so the header
    // always reconciles with the lines below; unassigned items show in the footer.
    val splitTotal = session.people.sumOf { session.totalFor(it.id) }
    append(header).append(" — ").append(Money.dollars(splitTotal)).append("\n")
    val n = session.people.size
    append(n).append(if (n == 1) " person, " else " people, ").append(ATTRIBUTION)

    session.people.forEach { person ->
        append("\n\n").append(buildPersonShareLine(session, person))
    }

    val unassigned = session.unassignedItems
    if (unassigned.isNotEmpty()) {
        append("\n\nUnassigned (not split): ")
        append(unassigned.joinToString(", ") { "${it.name} (${Money.dollars(it.price)})" })
    }
}
