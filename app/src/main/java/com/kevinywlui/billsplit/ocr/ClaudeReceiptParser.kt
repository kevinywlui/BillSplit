package com.kevinywlui.billsplit.ocr

import android.graphics.Bitmap
import android.util.Base64
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.ParsedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object ClaudeReceiptParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val JSON_MEDIA = "application/json".toMediaType()

    suspend fun parse(bitmap: Bitmap, apiKey: String): ParsedReceipt = withContext(Dispatchers.IO) {
        val imageData = run {
            val maxDim = 1568
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
            val scaled = if (scale < 1f)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            else bitmap
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        val prompt = """
            Parse this receipt image and return a single JSON object with no explanation or markdown.

            Rules for items:
            - Include only food and drink line items
            - If an item has quantity > 1, return it as separate individual items at the unit price
              (e.g. "2 Chicken Dolsot $49.90" → two items at $24.95 each)
            - Tax, subtotal, tip, total, change, and payment amounts are NOT food items

            Rules for fees:
            - Extract tax, tip, and any other fees (delivery, service charge, surcharge, etc.) into separate fields
            - Extract the final total charged (the largest "TOTAL" line, not subtotal)

            Return exactly this shape:
            {
              "restaurantName": "...",
              "items": [{"name": "...", "price": 0.00}],
              "tax": 0.00,
              "tip": 0.00,
              "otherFees": 0.00,
              "total": 0.00
            }

            For restaurantName: use the business name at the top of the receipt. Use an empty string if not found.
        """.trimIndent()

        val content = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", imageData)
                })
            })
            put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 1024)
            put("temperature", 0)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            }))
        }.toString().toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        val call = client.newCall(request)
        val responseText = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        cont.resumeWithException(Exception("API error ${response.code}"))
                        return@suspendCancellableCoroutine
                    }
                    val body = response.body?.string()
                    if (body != null) cont.resume(body)
                    else cont.resumeWithException(Exception("Empty response"))
                }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

        val text = JSONObject(responseText)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")

        parseReceiptJson(text)
    }
}

internal fun parseReceiptJson(text: String): ParsedReceipt {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}') + 1
    if (start == -1 || end == 0) return ParsedReceipt()

    val obj = JSONObject(text.substring(start, end))
    val arr = obj.optJSONArray("items") ?: JSONArray()
    val items = (0 until arr.length()).mapNotNull { i ->
        val item = arr.getJSONObject(i)
        val name = item.optString("name").trim()
        val price = item.optDouble("price", 0.0)
        if (name.length >= 2 && price > 0) LineItem(name = name, price = price) else null
    }

    return ParsedReceipt(
        items = items,
        tax = obj.optDouble("tax", 0.0),
        tip = obj.optDouble("tip", 0.0),
        otherFees = obj.optDouble("otherFees", 0.0),
        receiptTotal = obj.optDouble("total", 0.0),
        restaurantName = obj.optString("restaurantName", "").trim()
    )
}
