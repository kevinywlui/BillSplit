package com.kevinywlui.billsplit.ocr

/**
 * Vision models the user can choose for receipt parsing (Settings → Receipt model).
 * [id] is the Anthropic API `model` string sent to /v1/messages.
 *
 * Sonnet 4.6 is the default: receipt OCR is a mechanical extract-and-structure
 * task that it handles well, at roughly ~1¢ per scan. Fable 5 is the more capable
 * (and ~3–4× pricier, somewhat slower) frontier option for receipts Sonnet trips on.
 *
 * NOTE: the `claude-fable-5` id is taken from Anthropic's Fable 5 announcement; if a
 * model id ever stops being accepted, the API returns a 4xx and parsing surfaces the
 * usual error — the user can switch back to Sonnet in Settings.
 */
enum class ReceiptModel(val id: String, val label: String, val blurb: String) {
    SONNET_4_6("claude-sonnet-4-6", "Claude Sonnet 4.6", "Default · fast · ~1¢ per scan"),
    FABLE_5("claude-fable-5", "Claude Fable 5", "Most capable · slower · ~4¢ per scan");

    companion object {
        val DEFAULT = SONNET_4_6

        /** Map a stored API id back to a model, falling back to [DEFAULT] for unknown/blank ids. */
        fun fromId(id: String?): ReceiptModel = entries.find { it.id == id } ?: DEFAULT
    }
}
