package com.kevinywlui.billsplit.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kevinywlui.billsplit.model.SavedBill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.io.File

private val Context.billHistoryDataStore by preferencesDataStore(name = "bill_history")
private val HISTORY_KEY = stringPreferencesKey("bills_json")

// encodeDefaults so the schema `version` is always written, even when it equals its default.
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Current persisted-history schema version, stamped into every write. Reading is
 * currently version-agnostic (additive/removable fields are handled by
 * `ignoreUnknownKeys` + defaults). For a future INCOMPATIBLE change (renamed or
 * re-meaning'd field, units change), bump this AND add a `when (version)` migration
 * branch in [deserialize] — the version slot exists so that data is identifiable.
 */
const val HISTORY_SCHEMA_VERSION = 1

/** Versioned envelope around the saved bills, so future schema changes can be migrated. */
@Serializable
internal data class BillHistoryEnvelope(
    val version: Int = HISTORY_SCHEMA_VERSION,
    val bills: List<SavedBill> = emptyList()
)

class BillHistoryRepository(private val context: Context) {

    val bills: Flow<List<SavedBill>> = context.billHistoryDataStore.data.map { prefs ->
        deserialize(prefs[HISTORY_KEY] ?: "[]")
    }

    suspend fun saveBill(bill: SavedBill) {
        context.billHistoryDataStore.edit { prefs ->
            val current = deserialize(prefs[HISTORY_KEY] ?: "[]")
            prefs[HISTORY_KEY] = serialize(listOf(bill) + current)
        }
    }

    suspend fun deleteBill(billId: String) {
        context.billHistoryDataStore.edit { prefs ->
            val current = deserialize(prefs[HISTORY_KEY] ?: "[]")
            current.find { it.id == billId }?.receiptImagePath?.let { File(it).delete() }
            prefs[HISTORY_KEY] = serialize(current.filter { it.id != billId })
        }
    }

}

internal fun serialize(bills: List<SavedBill>): String =
    json.encodeToString(BillHistoryEnvelope(HISTORY_SCHEMA_VERSION, bills))

internal fun deserialize(jsonStr: String): List<SavedBill> {
    val root = runCatching { json.parseToJsonElement(jsonStr) }.getOrNull() ?: return emptyList()
    // Accept both the versioned envelope {"version":N,"bills":[...]} and the legacy
    // bare array [...] written before schema versioning existed.
    val billsArray: JsonArray = when (root) {
        is JsonObject -> root["bills"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
        is JsonArray -> root
        else -> return emptyList()
    }
    return billsArray.mapNotNull { element ->
        runCatching { json.decodeFromJsonElement(SavedBill.serializer(), element) }
            .getOrElse { e -> Log.e("BillSplit", "Skipping corrupt bill entry", e); null }
    }
}
