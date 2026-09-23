package com.paylensu.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `formato agrupa miles y siempre 2 decimales`() {
        assertEquals("$1,234.56", Money(123_456).format("$"))
        assertEquals("$0.05", Money(5).format("$"))
        assertEquals("Bs. 55.00", Money(5_500).format("Bs. "))
        assertEquals("-$10.00", Money(-1_000).format("$"))
    }

    @Test
    fun `porcentaje con redondeo half-up`() {
        // 14.25 + 16% = 16.53 (1425 + 228)
        assertEquals(1_653L, Money(1_425).percent(16).cents.let { Money(1_425).cents + it })
    }

    @Test
    fun `suma resta y valor absoluto`() {
        val a = Money(1_000) + Money(250)
        assertEquals(1_250L, a.cents)
        assertEquals(750L, (a - Money(500)).cents)
        assertEquals(500L, Money(-500).abs().cents)
    }

    @Test
    fun `toBs usa half-up`() {
        // $14.20 a 36.88 = 523.696 -> 523.70
        assertEquals(52_370L, Money.toBs(1_420, 3_688))
    }

    @Test
    fun `fromBs invierte la conversion`() {
        // Bs 523.70 a 36.88 -> $14.20 (1420.17 -> 1420)
        assertEquals(1_420L, Money.fromBs(52_370, 3_688).cents)
    }

    @Test
    fun `parse acepta enteros y decimales`() {
        assertEquals(1_420L, Money.parse("1420")?.cents)
        assertEquals(1_420L, Money.parse("14.20")?.cents)
        assertEquals(null, Money.parse(""))
    }

    @Test
    fun `division half-up simetrica`() {
        assertEquals(2L, Money.divRoundHalfUp(150, 100))   // 1.5 -> 2
        assertEquals(1L, Money.divRoundHalfUp(149, 100))   // 1.49 -> 1
        assertEquals(3L, Money.divRoundHalfUp(250, 100))   // 2.5 -> 3
        assertEquals(-3L, Money.divRoundHalfUp(-250, 100)) // -2.5 -> -3
    }
}
