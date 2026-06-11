package com.kevinywlui.billsplit.ocr

/**
 * Vision models the user can choose for receipt parsing (Settings → Receipt model).
 * [id] is the Anthropic API `model` string sent to /v1/messages. Listed cheapest to
 * priciest; the cost blurbs are rough per-scan estimates for a typical receipt
 * (~2.4k input tokens + ~300 output tokens).
 *
 * Sonnet 4.6 is the default: receipt OCR is a mechanical extract-and-structure task it
 * handles well at modest cost. Haiku is cheaper/faster for clean receipts; Opus is the
 * more capable (pricier, slower) option for messy or faint ones.
 *
 * To add a model, append an entry here — the Settings picker and the round-trip test
 * iterate [entries], so no other code changes are needed. If an id ever stops being
 * accepted, the API returns a 4xx and parsing surfaces the usual error; the user can
 * switch back to another model in Settings.
 */
enum class ReceiptModel(val id: String, val label: String, val blurb: String) {
    HAIKU_4_5("claude-haiku-4-5", "Claude Haiku 4.5", "Cheapest · fastest · <1¢ per scan"),
    SONNET_4_6("claude-sonnet-4-6", "Claude Sonnet 4.6", "Default · balanced · ~1¢ per scan"),
    OPUS_4_8("claude-opus-4-8", "Claude Opus 4.8", "Most capable · ~6¢ per scan");

    companion object {
        /** Used when no model has been chosen yet (blank stored id). */
        val DEFAULT = SONNET_4_6
    }
}
