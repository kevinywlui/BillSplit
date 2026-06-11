package com.kevinywlui.billsplit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kevinywlui.billsplit.ocr.ReceiptModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val API_KEY = stringPreferencesKey("anthropic_api_key")
private val RECEIPT_MODEL = stringPreferencesKey("receipt_model_id")

/**
 * Stores user settings on-device. The Anthropic API key lives here (not in
 * BuildConfig) so it is supplied at runtime per install and never bundled into
 * the distributed APK.
 */
class SettingsRepository(private val context: Context) {

    val apiKey: Flow<String> = context.settingsDataStore.data.map { it[API_KEY] ?: "" }

    suspend fun getApiKey(): String = apiKey.first()

    suspend fun setApiKey(key: String) {
        context.settingsDataStore.edit { it[API_KEY] = key.trim() }
    }

    /** API id of the vision model used for receipt parsing; falls back to the default. */
    val receiptModelId: Flow<String> =
        context.settingsDataStore.data.map { it[RECEIPT_MODEL] ?: ReceiptModel.DEFAULT.id }

    suspend fun getReceiptModelId(): String = receiptModelId.first()

    suspend fun setReceiptModelId(id: String) {
        context.settingsDataStore.edit { it[RECEIPT_MODEL] = id }
    }
}
