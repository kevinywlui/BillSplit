package com.kevinywlui.billsplit.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Frozen snapshot of a completed bill, persisted to history via [com.kevinywlui.billsplit.data.BillHistoryRepository].
 *
 * This is a versioned on-disk schema: every field has a default and `Json` is configured with
 * `ignoreUnknownKeys = true`, so additive/removable changes deserialize old data unchanged.
 * Any INCOMPATIBLE change (rename, re-meaning, unit change) must bump `HISTORY_SCHEMA_VERSION`
 * and add a migration — see the data-compatibility policy in ARCHITECTURE.md.
 *
 * [grandTotal] is the session's final [BillSession.effectiveTotal] (override + tip already folded in),
 * and [finalShares] is pre-computed at save time so reopening a bill never recomputes the split.
 */
@Serializable
data class SavedBill(
    val id: String = UUID.randomUUID().toString(),
    val savedAt: Long = System.currentTimeMillis(),
    val people: List<Person> = emptyList(),
    val items: List<LineItem> = emptyList(),
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val otherFees: Double = 0.0,
    val grandTotal: Double = 0.0,
    val finalShares: Map<String, Double> = emptyMap(),
    val restaurantName: String = "",
    val venmoRequestedPersonIds: Set<String> = emptySet(),
    val receiptImagePath: String? = null
)
