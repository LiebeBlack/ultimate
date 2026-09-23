package com.paylensu.finance

import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.model.PaymentOption

/**
 * Matrix de vuelto / pago mixto dinamico.
 *
 * Estrategia: para cada billete disponible, el usuario paga en efectivo USD lo
 * mas cercano sin exceder (o el billete completo si acepta vuelto), y el resto
 * sale por Pago Movil en Bs a la tasa BCV. La fila "optima" es el mayor
 * billete que cubre SIN exceder el total; si ninguno cubre, es la fila exacta
 * (todo por Pago Movil).
 */
object MixedPayment {

    val BILLS_CENTS = longArrayOf(
        100L, 200L, 500L, 1_000L, 2_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L
    ) // $1..$100

    /**
     * @param totalUsdCents total de la compra en USD centavos (con IVA ya aplicado).
     * @param rateCents     tasa BCV en centavos de Bs por USD.
     * @param freeAmountUsd monto libre ingresado por el teclado (ej. 123123312), opcional.
     */
    fun matrix(
        totalUsdCents: Long,
        rateCents: Long,
        freeAmountUsd: Long? = null,
    ): List<PaymentOption> {
        if (totalUsdCents <= 0 || rateCents <= 0) return emptyList()
        val totalBsCents = Money.toBs(totalUsdCents, rateCents)
        val bills = if (freeAmountUsd != null && freeAmountUsd > 0) {
            BILLS_CENTS + freeAmountUsd
        } else BILLS_CENTS

        val out = ArrayList<PaymentOption>(bills.size + 1)
        var bestIdx = -1
        var bestCover = -1L

        for (bill in bills.distinct().sorted()) {
            val cash = if (bill <= totalUsdCents) bill else totalUsdCents
            val overpay = bill > totalUsdCents
            val remainingUsd = totalUsdCents - cash
            val changeUsdCents = if (overpay) bill - totalUsdCents else 0L
            val bsDue = if (remainingUsd > 0) Money.toBs(remainingUsd, rateCents) else 0L
            // Vuelto Bs cuando paga con billete mayor al total:
            val changeBsFromOverpay = if (overpay) Money.toBs(changeUsdCents, rateCents) else 0L
            val changeUsd = Money(changeUsdCents)
            out += PaymentOption(
                billCents = bill,
                cashCents = cash,
                bsDue = bsDue,
                changeUsd = changeUsd,
                changeBs = changeBsFromOverpay,
                optimal = false,
            )
            // Fila óptima = el billete que MÁS cubre SIN exceder el total.
            // Los sobrepagos generan vuelto y nunca son la fila óptima.
            if (!overpay && cash > bestCover) {
                bestCover = cash
                bestIdx = out.lastIndex
            }
        }
        if (bestIdx >= 0) {
            out[bestIdx] = out[bestIdx].copy(optimal = true)
        }
        // Fila "exacto": todo en Bs por Pago Movil, cero efectivo.
        out += PaymentOption(
            billCents = 0,
            cashCents = 0,
            bsDue = totalBsCents,
            changeUsd = Money.ZERO,
            changeBs = 0,
            optimal = out.none { it.optimal },
        )
        return out
    }

    /**
     * Fila de cobro EFECTIVA: la opción que el usuario eligió (por billete) o
     * la óptima si no eligió nada. Devuelve null si la matrix está vacía.
     * Compartida por la ResultCard (mostrar) y el cobro (asentar): jamás
     * divergen.
     */
    fun resolve(
        totalUsdCents: Long,
        rateCents: Long,
        selectedBillCents: Long?,
    ): PaymentOption? {
        val opts = matrix(totalUsdCents, rateCents)
        if (opts.isEmpty()) return null
        return selectedBillCents?.let { sel -> opts.firstOrNull { it.billCents == sel } }
            ?: opts.firstOrNull { it.optimal }
            ?: opts.last()
    }
}
