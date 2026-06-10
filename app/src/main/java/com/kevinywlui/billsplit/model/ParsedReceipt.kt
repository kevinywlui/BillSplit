package com.kevinywlui.billsplit.model

data class ParsedReceipt(
    val items: List<LineItem> = emptyList(),
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val otherFees: Double = 0.0,
    val receiptTotal: Double = 0.0,
    val restaurantName: String = ""
)
