package com.paylensu.model

import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.data.CartRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartEntryTest {

    @Test
    fun `entrada nunca supera 1200 bytes`() {
        val label = "X".repeat(10_000) // por más que venga basura del OCR
        val e = CartEntry(1, label.take(128), Money(1_250), CurrencySide.USD, 0L)
        assertTrue(e.byteSize() <= 1200)
        assertEquals(153, e.byteSize()) // 25 + 128 UTF-8
    }

    @Test
    fun `totales mezclan usd y ves con la tasa`() {
        val rate = ExchangeRate(3_688, ExchangeRate.Source.AUTO, 0L)
        val entries = listOf(
            CartEntry(1, "A", Money(1_000), CurrencySide.USD, 0),   // $10.00
            CartEntry(2, "B", Money(73_760), CurrencySide.VES, 0),  // Bs 737.60 = $20
        )
        val (usd, bs) = CartRepository.totals(entries, rate)
        assertEquals(3_000L, usd.cents)      // $30.00
        assertEquals(Money.toBs(3_000, 3_688), bs.cents)
    }

    @Test
    fun `totales sin tasa solo suman usd`() {
        val entries = listOf(
            CartEntry(1, "A", Money(1_000), CurrencySide.USD, 0),
            CartEntry(2, "B", Money(73_760), CurrencySide.VES, 0),
        )
        val (usd, _) = CartRepository.totals(entries, null)
        assertEquals(1_000L, usd.cents)
    }
}
