package com.kevinywlui.billsplit

import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.util.Money
import com.kevinywlui.billsplit.util.VENMO_NOTE_MAX_LENGTH
import com.kevinywlui.billsplit.util.buildVenmoChargeUrl
import com.kevinywlui.billsplit.util.buildVenmoNote
import com.kevinywlui.billsplit.util.normalizeVenmoUsername
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VenmoLinkTest {

    private fun person(name: String) = Person(name = name, venmoUsername = name.lowercase())
    private fun item(name: String, price: Double, vararg ids: String) =
        LineItem(name = name, price = price, assignedPersonIds = ids.toList())

    // --- normalizeVenmoUsername ---

    @Test
    fun `plain username passes through`() {
        assertEquals("Kevin-Lui", normalizeVenmoUsername("Kevin-Lui"))
    }

    @Test
    fun `leading at-sign and whitespace are stripped`() {
        assertEquals("Kevin-Lui", normalizeVenmoUsername("  @Kevin-Lui "))
    }

    @Test
    fun `pasted profile urls reduce to the username`() {
        assertEquals("Kevin-Lui", normalizeVenmoUsername("https://venmo.com/u/Kevin-Lui"))
        assertEquals("Kevin-Lui", normalizeVenmoUsername("https://account.venmo.com/u/Kevin-Lui"))
        assertEquals("Kevin-Lui", normalizeVenmoUsername("venmo.com/Kevin-Lui"))
        assertEquals("Kevin-Lui", normalizeVenmoUsername("www.venmo.com/Kevin-Lui/"))
        assertEquals("Kevin-Lui", normalizeVenmoUsername("https://venmo.com/u/Kevin-Lui?utm_source=share"))
        assertEquals("Kevin-Lui", normalizeVenmoUsername("HTTPS://VENMO.COM/u/Kevin-Lui"))
    }

    // --- buildVenmoChargeUrl ---

    @Test
    fun `charge url has expected params and formatted amount`() {
        val url = buildVenmoChargeUrl("Kevin-Lui", 12.5, "lunch")
        assertEquals(
            "https://venmo.com/?txn=charge&audience=private&recipients=Kevin-Lui&amount=12.50&note=lunch",
            url
        )
    }

    @Test
    fun `legacy saved username with at-sign still produces a clean link`() {
        val url = buildVenmoChargeUrl("@Kevin-Lui", 5.0, "x")
        assertTrue(url.contains("recipients=Kevin-Lui&"))
    }

    @Test
    fun `note is percent-encoded without plus-for-space`() {
        val url = buildVenmoChargeUrl("kevin", 20.15, "Luigi's: Pizza ($12.00) & more #1 = $20.15")
        val note = url.substringAfter("note=")
        assertFalse(note.contains("+"))
        assertFalse(note.contains(" "))
        assertFalse(note.contains("&"))
        assertFalse(note.contains("#"))
        assertTrue(note.contains("%20"))
    }

    // --- buildVenmoNote ---

    @Test
    fun `itemized note reconciles to the per-person total`() {
        val p1 = person("Alice"); val p2 = person("Bob")
        val session = BillSession(
            people = listOf(p1, p2),
            items = listOf(item("Pizza", 20.0, p1.id), item("Guac", 7.0, p1.id, p2.id)),
            tax = 5.0,
            restaurantName = "Luigi's"
        )
        val note = buildVenmoNote(session, p1)
        assertTrue(note.startsWith("Luigi's: Pizza ($20.00) + Guac ÷2 ($3.50)"))
        assertTrue(note.contains("Fees/Tip"))
        assertTrue(note.endsWith("= ${Money.dollars(session.totalFor(p1.id))}"))
    }

    @Test
    fun `long item lists collapse to a count and keep the total`() {
        val p1 = person("Alice")
        val items = (1..20).map {
            item("Extremely Long Menu Item Name Number $it", 3.0, p1.id)
        }
        val session = BillSession(people = listOf(p1), items = items, restaurantName = "Luigi's")
        val note = buildVenmoNote(session, p1)
        assertTrue(note.length <= VENMO_NOTE_MAX_LENGTH)
        assertTrue(note.contains("20 items"))
        assertTrue(note.endsWith("= ${Money.dollars(session.totalFor(p1.id))}"))
    }

    @Test
    fun `huge restaurant name is dropped before the amounts are`() {
        val p1 = person("Alice")
        val session = BillSession(
            people = listOf(p1),
            items = listOf(item("Pizza", 20.0, p1.id)),
            restaurantName = "R".repeat(400)
        )
        val note = buildVenmoNote(session, p1)
        assertTrue(note.length <= VENMO_NOTE_MAX_LENGTH)
        assertFalse(note.contains("RRRR"))
        assertTrue(note.endsWith("= ${Money.dollars(session.totalFor(p1.id))}"))
    }
}
