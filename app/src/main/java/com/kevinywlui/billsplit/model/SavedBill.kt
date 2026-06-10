package com.kevinywlui.billsplit.model

import kotlinx.serialization.Serializable
import java.util.UUID

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
