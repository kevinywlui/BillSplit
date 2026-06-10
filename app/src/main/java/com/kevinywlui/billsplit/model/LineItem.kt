package com.kevinywlui.billsplit.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LineItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val assignedPersonIds: List<String> = emptyList()
) {
    val isAssigned: Boolean get() = assignedPersonIds.isNotEmpty()

    fun shareFor(personId: String): Double =
        if (personId in assignedPersonIds) price / assignedPersonIds.size else 0.0
}
