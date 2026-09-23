package com.paylensu.parser

import com.paylensu.core.ExchangeRate
import com.paylensu.model.CurrencySide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceParserTest {

    private val rate = ExchangeRate(rateCents = 3_688, source = ExchangeRate.Source.AUTO, updatedAtMillis = 0L)

    @Test
    fun `dolar con decimales`() {
        val r = PriceParser.scan("Refresco  \$12.50", rate)
        assertEquals(1, r.size)
        assertEquals(1_250L, r[0].money.cents)
        assertEquals(CurrencySide.USD, r[0].side)
        assertTrue(r[0].confidence >= 90)
    }

    @Test
    fun `bolivares con separador es`() {
        val r = PriceParser.scan("1.234,56 Bs", rate)
        assertEquals(1, r.size)
        assertEquals(123_456L, r[0].money.cents)
        assertEquals(CurrencySide.VES, r[0].side)
    }

    @Test
    fun `usd con separador miles es`() {
        val r = PriceParser.scan("USD 3.999,99", rate)
        assertEquals(1, r.size)
        assertEquals(399_999L, r[0].money.cents)
        assertEquals(CurrencySide.USD, r[0].side)
    }

    @Test
    fun `sin simbolo bajo el umbral es dolares`() {
        // umbral = toBs(2000, 3688) = $737.60 -> 550.00 queda en USD
        val r = PriceParser.scan("550,00", rate)
        assertEquals(1, r.size)
        assertEquals(55_000L, r[0].money.cents)
        assertEquals(CurrencySide.USD, r[0].side)
    }

    @Test
    fun `sin simbolo sobre el umbral es bolivares`() {
        val r = PriceParser.scan("5500,00", rate)
        assertEquals(1, r.size)
        assertEquals(CurrencySide.VES, r[0].side)
    }

    @Test
    fun `codigos con ceros a la izquierda se rechazan`() {
        val r = PriceParser.scan("COD 007", rate)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `devuelve top3 ordenado por confianza`() {
        val text = """
            LATA $1.25
            COMBO 2.750,00 Bs
            TOTAL 45.00
        """.trimIndent()
        val r = PriceParser.scan(text, rate, limit = 3)
        assertEquals(3, r.size)
        // Empate de confianza 95/95 ($1.25 vs 2.750,00 Bs): el sort estable
        // conserva el orden de lectura, así que gana la primera línea (USD).
        assertEquals(CurrencySide.USD, r[0].side)
        assertTrue(r.any { it.side == CurrencySide.VES })
        assertTrue(r[0].confidence >= r[1].confidence)
        assertTrue(r[1].confidence >= r[2].confidence)
    }

    @Test
    fun `texto vacio no explota`() {
        assertTrue(PriceParser.scan("", rate).isEmpty())
        assertTrue(PriceParser.scan("   \n  ", rate).isEmpty())
    }

    @Test
    fun `palabra de recibo impulsa la confianza sin simbolo`() {
        val plain = PriceParser.scan("45.00", rate)[0]
        val hinted = PriceParser.scan("TOTAL 45.00", rate)[0]
        assertEquals(55, plain.confidence)
        assertEquals(63, hinted.confidence) // 55 + 8 por "TOTAL"
    }

    @Test
    fun `cifras sin simbolo fuera de cotas se descartan`() {
        // Código de producto de 12 dígitos: ruido OCR, nunca un precio.
        assertTrue(PriceParser.scan("REF 123456789012", rate).isEmpty())
    }
}
