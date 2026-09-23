package com.paylensu.finance

import com.paylensu.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceMathTest {

    @Test
    fun `iva incluido redondo`() {
        // neto 14.25 -> total con 16% = 16.53
        assertEquals(1_653L, PriceMath.withIva(Money(1_425), 16).cents)
    }

    @Test
    fun `desglose inverso del total con iva`() {
        val total = Money(1_653)
        val iva = PriceMath.ivaOf(total, 16)
        assertEquals(228L, iva.cents)
        assertEquals(1_425L, PriceMath.netOf(total, 16).cents)
        // idempotencia: aplicar IVA al neto devuelve el total
        assertEquals(total.cents, PriceMath.withIva(Money(1_425), 16).cents)
    }

    @Test
    fun `iva cero es identidad`() {
        assertEquals(9_999L, PriceMath.withIva(Money(9_999), 0).cents)
        assertEquals(0L, PriceMath.ivaOf(Money(9_999), 0).cents)
    }
}

class MixedPaymentTest {

    private val rate = 3_688L // 36.88 Bs/USD

    @Test
    fun `billete menor deja resto en pago movil`() {
        val opts = MixedPayment.matrix(totalUsdCents = 1_420, rateCents = rate)
        val ten = opts.first { it.billCents == 1_000L }
        assertEquals(1_000L, ten.cashCents)
        // resto $4.20 -> 420*3688/100 = 15489.6 -> 15490
        assertEquals(15_490L, ten.bsDue)
        assertEquals(0L, ten.changeUsd.cents)
    }

    @Test
    fun `billete mayor genera vuelto en dolares y bs`() {
        val opts = MixedPayment.matrix(totalUsdCents = 1_420, rateCents = rate)
        val twenty = opts.first { it.billCents == 2_000L }
        assertEquals(1_420L, twenty.cashCents) // solo aplica el total
        assertEquals(580L, twenty.changeUsd.cents) // $5.80
        assertEquals(Money.toBs(580, rate), twenty.changeBs)
    }

    @Test
    fun `fila exacta todo pago movil`() {
        val opts = MixedPayment.matrix(totalUsdCents = 1_420, rateCents = rate)
        val exact = opts.last()
        assertEquals(0L, exact.billCents)
        assertEquals(Money.toBs(1_420, rate), exact.bsDue)
        // Óptimo = el mayor billete que cubre SIN exceder ($10, no el de $20).
        assertEquals(1, opts.count { it.optimal })
        assertEquals(1_000L, opts.first { it.optimal }.billCents)
    }

    @Test
    fun `total menor al billete minimo deja el optimo en la fila exacta`() {
        val opts = MixedPayment.matrix(totalUsdCents = 50, rateCents = rate) // $0.50
        assertEquals(1, opts.count { it.optimal })
        assertEquals(0L, opts.first { it.optimal }.billCents) // solo Pago Móvil
    }

    @Test
    fun `monto libre grande entra como billete`() {
        val opts = MixedPayment.matrix(1_420, rate, freeAmountUsd = 123_123_312)
        assertTrue(opts.any { it.billCents == 123_123_312L })
    }

    @Test
    fun `entradas invalidas devuelven vacio`() {
        assertTrue(MixedPayment.matrix(0, rate).isEmpty())
        assertTrue(MixedPayment.matrix(1_000, 0).isEmpty())
    }

    @Test
    fun `resolve usa el billete elegido o la optima`() {
        // Sin selección: cae en la óptima (mayor billete sin exceder = $10).
        val optimal = MixedPayment.resolve(1_420, rate, null)!!
        assertEquals(1_000L, optimal.billCents)
        assertEquals(true, optimal.optimal)
        // Con selección: gana la fila del billete tocado aunque no sea óptima.
        val chosen = MixedPayment.resolve(1_420, rate, 20_00L)!!
        assertEquals(2_000L, chosen.billCents)
        // Selección inexistente en la matrix: cae en la óptima.
        val fallback = MixedPayment.resolve(1_420, rate, 77_777L)!!
        assertEquals(1_000L, fallback.billCents)
        // Matrix vacía: null.
        assertTrue(MixedPayment.resolve(0, rate, null) == null)
    }

    @Test
    fun `impuestos a cobrar incluyen igtf solo con efectivo`() {
        val total = Money(1_653) // $16.53 con IVA 16 incluido
        val soloIva = PriceMath.taxesToCollect(total, 16, 0)
        assertEquals(228L, soloIva.cents)
        val conIgtf = PriceMath.taxesToCollect(total, 16, 1_000)
        // IVA 228 + IGTF 3% de 1653 = 50 (49.59 half-up) -> 278
        assertEquals(228L + PriceMath.igtf(total).cents, conIgtf.cents)
        assertEquals(278L, conIgtf.cents)
    }
}
