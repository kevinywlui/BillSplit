package com.kevinywlui.billsplit.model

/**
 * Transient result of [com.kevinywlui.billsplit.ocr.ClaudeReceiptParser.parse]. Not persisted;
 * the ViewModel folds these fields into the live [BillSession] immediately after a scan.
 */
data class ParsedReceipt(
    val items: List<LineItem> = emptyList(),
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val otherFees: Double = 0.0,
    val receiptTotal: Double = 0.0,
    val restaurantName: String = ""
)
