package com.kevinywlui.billsplit

import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.util.Money
import com.kevinywlui.billsplit.util.buildPersonShareLine
import com.kevinywlui.billsplit.util.buildShareText
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextTest {

    private fun person(name: String) = Person(name = name)
    private fun item(name: String, price: Double, vararg ids: String) =
        LineItem(name = name, price = price, assignedPersonIds = ids.toList())

    @Test
    fun `person line reconciles to the per-person total`() {
        val p1 = person("Alice"); val p2 = person("Bob")
        val session = BillSession(
            people = listOf(p1, p2),
            items = listOf(item("Pizza", 20.0, p1.id), item("Salad", 10.0, p2.id)),
            tax = 6.0,
            restaurantName = "Luigi's"
        )
        val line = buildPersonShareLine(session, p1)
        assertTrue(line.startsWith("Alice — $"))
        assertTrue(line.contains("Pizza ($20.00)"))
        // fee line present and the header shows the authoritative per-person total
        assertTrue(line.contains("Fees, taxes & tip:"))
        assertTrue(line.contains(Money.dollars(session.totalFor(p1.id))))
    }

    @Test
    fun `share text includes header, everyone, and unassigned footer`() {
        val p1 = person("Alice"); val p2 = person("Bob")
        val session = BillSession(
            people = listOf(p1, p2),
            items = listOf(
                item("Pizza", 20.0, p1.id),
                item("Salad", 10.0, p2.id),
                item("Bread", 4.0)            // unassigned
            ),
            restaurantName = "Luigi's"
        )
        val text = buildShareText(session)
        assertTrue(text.startsWith("Luigi's — $"))
        assertTrue(text.contains("2 people"))
        assertTrue(text.contains("Alice — $"))
        assertTrue(text.contains("Bob — $"))
        assertTrue(text.contains("Unassigned (not split): Bread ($4.00)"))
        // plain text: no markdown emphasis characters
        assertTrue(!text.contains("**"))
    }

    @Test
    fun `share text falls back to Bill Split when restaurant blank`() {
        val p1 = person("Alice")
        val session = BillSession(
            people = listOf(p1),
            items = listOf(item("Pizza", 20.0, p1.id))
        )
        assertTrue(buildShareText(session).startsWith("Bill Split — $"))
    }
}
