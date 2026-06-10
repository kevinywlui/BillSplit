package com.kevinywlui.billsplit

import com.kevinywlui.billsplit.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyTest {

    @Test
    fun `format produces two decimals with a dot`() {
        assertEquals("12.50", Money.format(12.5))
        assertEquals("0.00", Money.format(0.0))
        assertEquals("1234.05", Money.format(1234.05))
    }

    @Test
    fun `dollars and percent`() {
        assertEquals("$12.50", Money.dollars(12.5))
        assertEquals("18%", Money.percent(18.0))
    }

    @Test
    fun `format is locale-independent`() {
        val original = Locale.getDefault()
        try {
            // Germany uses a comma decimal separator; Money must still emit a dot,
            // because the output flows into the Venmo amount= param and toDoubleOrNull().
            Locale.setDefault(Locale.GERMANY)
            assertEquals("33.55", Money.format(33.55))
            assertEquals("$33.55", Money.dollars(33.55))
        } finally {
            Locale.setDefault(original)
        }
    }
}
