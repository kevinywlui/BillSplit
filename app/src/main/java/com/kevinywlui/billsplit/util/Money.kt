package com.kevinywlui.billsplit.util

import java.util.Locale

/**
 * Locale-independent money formatting. Pinned to [Locale.US] so the decimal
 * separator is always "." — critical because these strings flow into the Venmo
 * `amount=` deep-link param and are re-parsed with [String.toDoubleOrNull].
 * A comma-decimal default locale would otherwise emit "12,34" and break both.
 */
object Money {
    /** e.g. 12.5 -> "12.50" */
    fun format(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    /** e.g. 12.5 -> "$12.50" */
    fun dollars(amount: Double): String = "$" + format(amount)

    /** e.g. 18.0 -> "18%" (whole-number percent) */
    fun percent(value: Double): String = String.format(Locale.US, "%.0f%%", value)

    /** e.g. 17.5 -> "17.5%" (one decimal) */
    fun percent1(value: Double): String = String.format(Locale.US, "%.1f%%", value)
}
