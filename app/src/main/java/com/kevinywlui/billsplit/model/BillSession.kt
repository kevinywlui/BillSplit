package com.kevinywlui.billsplit.model

data class BillSession(
    val people: List<Person> = emptyList(),
    val items: List<LineItem> = emptyList(),
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val otherFees: Double = 0.0,
    val receiptTotal: Double = 0.0,
    val userTotal: Double? = null,
    val adjustments: Double = 0.0,
    val restaurantName: String = "",
    val venmoRequestedPersonIds: Set<String> = emptySet(),
    val receiptImagePath: String? = null
) {
    val subtotal: Double get() = items.sumOf { it.price }
    val totalFees: Double get() = tax + tip + otherFees
    val grandTotal: Double get() = subtotal + totalFees

    // User override > Claude extraction > computed fallback
    val effectiveReceiptTotal: Double get() =
        userTotal ?: if (receiptTotal > 0.0) receiptTotal else grandTotal

    // Final total including adjustments (tip added on top of the receipt total)
    val effectiveTotal: Double get() = effectiveReceiptTotal + adjustments

    val unassignedItems: List<LineItem> get() = items.filter { !it.isAssigned }

    val finalShares: Map<String, Double> by lazy {
        if (people.isEmpty()) return@lazy emptyMap()

        val foodShares = people.associate { p -> p.id to items.sumOf { it.shareFor(p.id) } }

        val exact = if (subtotal > 0.0) {
            foodShares.mapValues { (_, food) -> (food / subtotal) * effectiveTotal }
        } else {
            val each = if (people.isNotEmpty()) effectiveTotal / people.size else 0.0
            foodShares.mapValues { each }
        }

        // Largest remainder: floor everyone, distribute leftover cents by biggest fractional part.
        val targetCents = Math.round(effectiveTotal * 100)
        val flooredCents = exact.mapValues { (_, v) -> (v * 100).toLong() }
        val remainders = exact.mapValues { (_, v) -> (v * 100) - (v * 100).toLong() }
        val leftover = (targetCents - flooredCents.values.sum()).coerceAtLeast(0)

        val finalCents = flooredCents.toMutableMap()
        remainders.entries.sortedByDescending { it.value }.take(leftover.toInt()).forEach {
            finalCents[it.key] = finalCents.getValue(it.key) + 1L
        }

        finalCents.mapValues { (_, c) -> c / 100.0 }
    }

    fun totalFor(personId: String): Double = finalShares[personId] ?: 0.0
    fun foodShareFor(personId: String): Double = items.sumOf { it.shareFor(personId) }
    fun feeShareFor(personId: String): Double = totalFor(personId) - foodShareFor(personId)
    fun fractionFor(personId: String): Double =
        if (subtotal > 0.0) foodShareFor(personId) / subtotal else 0.0
}
