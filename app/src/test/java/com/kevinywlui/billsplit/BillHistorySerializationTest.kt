package com.kevinywlui.billsplit

import com.kevinywlui.billsplit.data.deserialize
import com.kevinywlui.billsplit.data.serialize
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.model.SavedBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillHistorySerializationTest {

    private fun bill(
        id: String = "test-id",
        savedAt: Long = 1_000_000L,
        people: List<Person> = emptyList(),
        items: List<LineItem> = emptyList(),
        tax: Double = 0.0,
        tip: Double = 0.0,
        otherFees: Double = 0.0,
        grandTotal: Double = 0.0,
        restaurantName: String = "",
        venmoIds: Set<String> = emptySet(),
        imagePath: String? = null,
        finalShares: Map<String, Double> = emptyMap()
    ) = SavedBill(
        id = id, savedAt = savedAt,
        people = people, items = items, tax = tax, tip = tip,
        otherFees = otherFees, grandTotal = grandTotal,
        restaurantName = restaurantName, venmoRequestedPersonIds = venmoIds,
        receiptImagePath = imagePath, finalShares = finalShares
    )

    @Test
    fun `round-trip preserves all fields`() {
        val p = Person(name = "Alice", venmoUsername = "alice99", avatarColorIndex = 3)
        val item = LineItem(name = "Pasta", price = 14.50, assignedPersonIds = listOf(p.id))
        val original = bill(
            people = listOf(p),
            items = listOf(item),
            tax = 1.20, tip = 2.50, otherFees = 0.50,
            grandTotal = 18.70,
            restaurantName = "Café Roma",
            venmoIds = setOf(p.id),
            imagePath = "/data/receipts/abc.jpg",
            finalShares = mapOf(p.id to 18.70)
        )
        val result = deserialize(serialize(listOf(original))).first()

        assertEquals(original.id, result.id)
        assertEquals(original.savedAt, result.savedAt)
        assertEquals(original.restaurantName, result.restaurantName)
        assertEquals(original.grandTotal, result.grandTotal, 0.001)
        assertEquals(original.tax, result.tax, 0.001)
        assertEquals(original.tip, result.tip, 0.001)
        assertEquals(original.otherFees, result.otherFees, 0.001)
        assertEquals(original.venmoRequestedPersonIds, result.venmoRequestedPersonIds)
        assertEquals(original.receiptImagePath, result.receiptImagePath)
        assertEquals(original.finalShares, result.finalShares)
        assertEquals(1, result.people.size)
        assertEquals(p.name, result.people[0].name)
        assertEquals(p.venmoUsername, result.people[0].venmoUsername)
        assertEquals(1, result.items.size)
        assertEquals(item.name, result.items[0].name)
        assertEquals(item.price, result.items[0].price, 0.001)
    }

    @Test
    fun `null receiptImagePath round-trips as null`() {
        val result = deserialize(serialize(listOf(bill(imagePath = null)))).first()
        assertNull(result.receiptImagePath)
    }

    @Test
    fun `list ordering is preserved`() {
        val bills = listOf(bill(restaurantName = "A"), bill(restaurantName = "B"), bill(restaurantName = "C"))
        val result = deserialize(serialize(bills))
        assertEquals(listOf("A", "B", "C"), result.map { it.restaurantName })
    }

    @Test
    fun `corrupt top-level JSON returns empty list`() {
        assertTrue(deserialize("not json at all").isEmpty())
    }

    @Test
    fun `corrupt single entry is skipped, others survive`() {
        // serialize now emits a versioned envelope {"version":1,"bills":[...]};
        // inject a corrupt entry into the bills array before the closing "]}".
        val good = serialize(listOf(bill(restaurantName = "Good")))
        val withCorrupt = good.dropLast(2) + """, {"id": "x", "savedAt": "NOT_A_LONG"}]}"""
        val result = deserialize(withCorrupt)
        assertEquals(1, result.size)
        assertEquals("Good", result[0].restaurantName)
    }

    @Test
    fun `serialize emits a versioned envelope`() {
        val out = serialize(listOf(bill(restaurantName = "X")))
        assertTrue(out.contains("\"version\""))
        assertTrue(out.contains("\"bills\""))
    }

    @Test
    fun `legacy bare array still deserializes (migration)`() {
        // Data written before schema versioning was a bare JSON array.
        val legacy = """[{"id":"old","savedAt":1000,"people":[],"items":[],
            "tax":1.0,"grandTotal":5.0,"restaurantName":"Legacy","finalShares":{}}]"""
        val result = deserialize(legacy)
        assertEquals(1, result.size)
        assertEquals("Legacy", result[0].restaurantName)
        assertEquals(1.0, result[0].tax, 0.001)
    }

    @Test
    fun `missing optional fields default correctly`() {
        val minimalJson = """[{"id":"x","savedAt":1000,"people":[],"items":[],"finalShares":{}}]"""
        val result = deserialize(minimalJson)
        assertEquals(1, result.size)
        assertEquals(0.0, result[0].tax, 0.001)
        assertEquals(0.0, result[0].tip, 0.001)
        assertEquals("", result[0].restaurantName)
        assertNull(result[0].receiptImagePath)
        assertTrue(result[0].venmoRequestedPersonIds.isEmpty())
    }
}
