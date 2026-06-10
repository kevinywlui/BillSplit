package com.kevinywlui.billsplit

import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSessionTest {

    private fun person(name: String) = Person(name = name)
    private fun item(price: Double, vararg personIds: String) =
        LineItem(name = "item", price = price, assignedPersonIds = personIds.toList())

    // ── shareFor ──────────────────────────────────────────────────────────────

    @Test
    fun `shareFor splits item equally among assignees`() {
        val p1 = person("a"); val p2 = person("b"); val p3 = person("c")
        val item = item(9.0, p1.id, p2.id, p3.id)
        assertEquals(3.0, item.shareFor(p1.id), 0.001)
        assertEquals(3.0, item.shareFor(p2.id), 0.001)
        assertEquals(0.0, item.shareFor("other"), 0.001)
    }

    @Test
    fun `shareFor returns zero when item is unassigned`() {
        val item = LineItem(name = "x", price = 10.0)
        assertEquals(0.0, item.shareFor("anyone"), 0.001)
    }

    // ── effectiveTotal priority ───────────────────────────────────────────────

    @Test
    fun `userTotal takes priority over receiptTotal and grandTotal`() {
        val session = BillSession(
            items = listOf(item(10.0)),
            tax = 2.0,
            receiptTotal = 40.0,
            userTotal = 50.0
        )
        assertEquals(50.0, session.effectiveTotal, 0.001)
    }

    @Test
    fun `receiptTotal used when userTotal is null`() {
        val session = BillSession(
            items = listOf(item(10.0)),
            tax = 2.0,
            receiptTotal = 40.0,
            userTotal = null
        )
        assertEquals(40.0, session.effectiveTotal, 0.001)
    }

    @Test
    fun `grandTotal used when userTotal and receiptTotal are absent`() {
        val session = BillSession(
            items = listOf(item(10.0)),
            tax = 2.0
        )
        assertEquals(12.0, session.effectiveTotal, 0.001)
    }

    @Test
    fun `adjustments stack on top of effectiveReceiptTotal`() {
        val session = BillSession(
            items = listOf(item(10.0)),
            tax = 2.0,
            adjustments = 5.0
        )
        assertEquals(17.0, session.effectiveTotal, 0.001)
    }

    // ── finalShares ───────────────────────────────────────────────────────────

    @Test
    fun `finalShares sum exactly to effectiveTotal`() {
        val p1 = person("a"); val p2 = person("b"); val p3 = person("c")
        val session = BillSession(
            people = listOf(p1, p2, p3),
            items = listOf(
                item(10.0, p1.id),
                item(20.0, p2.id),
                item(30.0, p3.id)
            ),
            tax = 9.0
        )
        assertEquals(69.0, session.finalShares.values.sum(), 0.001)
    }

    @Test
    fun `largest-remainder ensures shares sum to exact cents`() {
        // $10 split 3 ways: naive = $3.333... each, should be $3.34 + $3.33 + $3.33
        val p1 = person("a"); val p2 = person("b"); val p3 = person("c")
        val session = BillSession(
            people = listOf(p1, p2, p3),
            items = listOf(item(10.0, p1.id, p2.id, p3.id))
        )
        val shares = session.finalShares.values.sorted()
        assertEquals(10.0, shares.sum(), 0.001)
        assertEquals(3.33, shares[0], 0.001)
        assertEquals(3.33, shares[1], 0.001)
        assertEquals(3.34, shares[2], 0.001)
    }

    @Test
    fun `subtotal zero splits effectiveTotal evenly`() {
        val p1 = person("a"); val p2 = person("b")
        val session = BillSession(
            people = listOf(p1, p2),
            items = emptyList(),
            userTotal = 20.0
        )
        val shares = session.finalShares
        assertEquals(10.0, shares[p1.id]!!, 0.001)
        assertEquals(10.0, shares[p2.id]!!, 0.001)
        assertEquals(20.0, shares.values.sum(), 0.001)
    }

    @Test
    fun `finalShares empty when no people`() {
        val session = BillSession(items = listOf(item(10.0)))
        assertTrue(session.finalShares.isEmpty())
    }

    @Test
    fun `unassigned items excluded from split`() {
        val p1 = person("a")
        val session = BillSession(
            people = listOf(p1),
            items = listOf(
                item(10.0, p1.id),
                item(5.0)         // unassigned
            ),
            tax = 1.0
        )
        // subtotal=15, p1 food=10, so p1 gets 10/15 of effectiveTotal (16.0)
        val expected = (10.0 / 15.0) * 16.0
        assertEquals(expected, session.finalShares[p1.id]!!, 0.01)
        assertEquals(1, session.unassignedItems.size)
    }

    @Test
    fun `proportional fee allocation across two people`() {
        val p1 = person("a"); val p2 = person("b")
        val session = BillSession(
            people = listOf(p1, p2),
            items = listOf(
                item(20.0, p1.id),
                item(10.0, p2.id)
            ),
            tax = 6.0   // $2 to p2, $4 to p1
        )
        val shares = session.finalShares
        assertEquals(36.0, shares.values.sum(), 0.001)
        // p1 has 2/3 of food, so 2/3 of total = 24.0
        assertEquals(24.0, shares[p1.id]!!, 0.01)
        assertEquals(12.0, shares[p2.id]!!, 0.01)
    }

    // ── unassigned-items rounding (regression: no stray cents) ────────────────

    @Test
    fun `unassigned items do not add stray cents`() {
        val p1 = person("a"); val p2 = person("b")
        val session = BillSession(
            people = listOf(p1, p2),
            items = listOf(
                item(10.0, p1.id),
                item(10.0, p2.id),
                item(10.0)          // unassigned — nobody pays for it
            )
        )
        // Each assigned person pays exactly their $10; the unassigned $10 is excluded,
        // and rounding must NOT inflate anyone (old bug produced $10.01 each = $20.02).
        assertEquals(10.0, session.finalShares[p1.id]!!, 0.001)
        assertEquals(10.0, session.finalShares[p2.id]!!, 0.001)
        assertEquals(20.0, session.finalShares.values.sum(), 0.001)
    }

    // ── discrepancy predicate ─────────────────────────────────────────────────

    @Test
    fun `hasReceiptDiscrepancy true when ocr total differs beyond epsilon`() {
        val session = BillSession(
            items = listOf(item(10.0), item(5.0)),  // grandTotal 15
            receiptTotal = 20.0,
            receiptTotalFromOcr = true
        )
        assertTrue(session.hasReceiptDiscrepancy())
    }

    @Test
    fun `hasReceiptDiscrepancy false within epsilon`() {
        val session = BillSession(
            items = listOf(item(10.0), item(5.0)),  // grandTotal 15
            receiptTotal = 15.0,
            receiptTotalFromOcr = true
        )
        assertTrue(!session.hasReceiptDiscrepancy())
    }

    @Test
    fun `hasReceiptDiscrepancy false when not from ocr`() {
        val session = BillSession(
            items = listOf(item(10.0), item(5.0)),  // grandTotal 15
            receiptTotal = 20.0,
            receiptTotalFromOcr = false              // e.g. reloaded history bill
        )
        assertTrue(!session.hasReceiptDiscrepancy())
    }
}
