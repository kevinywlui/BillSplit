package com.kevinywlui.billsplit

import com.kevinywlui.billsplit.ocr.parseReceiptJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeReceiptParserTest {

    private fun claudeResponse(json: String) = json  // parseReceiptJson receives the text block directly

    @Test
    fun `parses all fields from clean JSON`() {
        val text = """{
            "restaurantName": "Pacific Catch",
            "items": [
                {"name": "Salmon Bowl", "price": 20.95},
                {"name": "Side Fries", "price": 4.00}
            ],
            "tax": 2.10,
            "tip": 5.00,
            "otherFees": 1.50,
            "total": 33.55
        }"""
        val result = parseReceiptJson(text)
        assertEquals("Pacific Catch", result.restaurantName)
        assertEquals(2, result.items.size)
        assertEquals("Salmon Bowl", result.items[0].name)
        assertEquals(20.95, result.items[0].price, 0.001)
        assertEquals(2.10, result.tax, 0.001)
        assertEquals(5.00, result.tip, 0.001)
        assertEquals(1.50, result.otherFees, 0.001)
        assertEquals(33.55, result.receiptTotal, 0.001)
    }

    @Test
    fun `strips JSON from markdown code fences`() {
        val text = "```json\n{\"restaurantName\":\"Cafe\",\"items\":[],\"tax\":0,\"tip\":0,\"otherFees\":0,\"total\":0}\n```"
        val result = parseReceiptJson(text)
        assertEquals("Cafe", result.restaurantName)
    }

    @Test
    fun `strips leading explanation text before JSON`() {
        val text = "Here is the parsed receipt:\n{\"restaurantName\":\"Diner\",\"items\":[],\"tax\":1.0,\"tip\":0,\"otherFees\":0,\"total\":1.0}"
        val result = parseReceiptJson(text)
        assertEquals("Diner", result.restaurantName)
        assertEquals(1.0, result.tax, 0.001)
    }

    @Test
    fun `filters out items with empty or too-short names`() {
        val text = """{
            "restaurantName": "",
            "items": [
                {"name": "x", "price": 5.00},
                {"name": "", "price": 3.00},
                {"name": "Valid Item", "price": 10.00}
            ],
            "tax": 0, "tip": 0, "otherFees": 0, "total": 18.0
        }"""
        val result = parseReceiptJson(text)
        assertEquals(1, result.items.size)
        assertEquals("Valid Item", result.items[0].name)
    }

    @Test
    fun `filters out items with zero or negative price`() {
        val text = """{
            "restaurantName": "",
            "items": [
                {"name": "Free Item", "price": 0.0},
                {"name": "Paid Item", "price": 8.50}
            ],
            "tax": 0, "tip": 0, "otherFees": 0, "total": 8.50
        }"""
        val result = parseReceiptJson(text)
        assertEquals(1, result.items.size)
        assertEquals("Paid Item", result.items[0].name)
    }

    @Test
    fun `null items array returns empty item list`() {
        val text = """{"restaurantName": "Test", "items": null, "tax": 1.0, "tip": 0, "otherFees": 0, "total": 1.0}"""
        val result = parseReceiptJson(text)
        assertTrue(result.items.isEmpty())
        assertEquals(1.0, result.tax, 0.001)
    }

    @Test
    fun `missing items key returns empty item list without crash`() {
        val text = """{"restaurantName": "Test", "tax": 1.0, "tip": 0, "otherFees": 0, "total": 1.0}"""
        val result = parseReceiptJson(text)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `empty string returns empty ParsedReceipt`() {
        val result = parseReceiptJson("")
        assertTrue(result.items.isEmpty())
        assertEquals("", result.restaurantName)
        assertEquals(0.0, result.tax, 0.001)
    }

    @Test
    fun `no JSON braces returns empty ParsedReceipt`() {
        val result = parseReceiptJson("I could not parse the receipt.")
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `missing restaurantName defaults to empty string`() {
        val text = """{"items": [], "tax": 0, "tip": 0, "otherFees": 0, "total": 5.0}"""
        val result = parseReceiptJson(text)
        assertEquals("", result.restaurantName)
    }

    @Test
    fun `restaurantName is trimmed`() {
        val text = """{"restaurantName": "  Cafe Roma  ", "items": [], "tax": 0, "tip": 0, "otherFees": 0, "total": 0}"""
        val result = parseReceiptJson(text)
        assertEquals("Cafe Roma", result.restaurantName)
    }
}
