package com.kevinywlui.billsplit.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kevinywlui.billsplit.model.SavedBill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.io.File

private val Context.billHistoryDataStore by preferencesDataStore(name = "bill_history")
private val HISTORY_KEY = stringPreferencesKey("bills_json")

private val json = Json { ignoreUnknownKeys = true }

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

internal fun serialize(bills: List<SavedBill>): String = json.encodeToString(bills)

internal fun deserialize(jsonStr: String): List<SavedBill> {
    val arr = runCatching { json.parseToJsonElement(jsonStr).jsonArray }.getOrNull()
        ?: return emptyList()
    return arr.mapNotNull { element ->
        runCatching { json.decodeFromJsonElement(SavedBill.serializer(), element) }
            .getOrElse { e -> Log.e("BillSplit", "Skipping corrupt bill entry", e); null }
    }
}
