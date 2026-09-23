package com.paylensu.core

import java.io.Serializable

/** Tasa BCV: cuántos bolívares (en Bs) cuesta 1 USD. Guardada como centavos de Bs. */
data class ExchangeRate(
    val rateCents: Long,
    val source: Source,
    val updatedAtMillis: Long,
    val thresholdUsdCents: Long = USD_THRESHOLD_CENTS, // editable en Ajustes
) : Serializable {

    enum class Source { AUTO, MANUAL, FALLBACK }

    fun toBs(usd: Money): Long = Money.toBs(usd.cents, rateCents)
    fun toUsd(bsCents: Long): Money = Money.fromBs(bsCents, rateCents)

    /** Formato de tasa: "36.88" (sin símbolo). */
    fun format(): String {
        val dec = (rateCents % 100).toString().padStart(2, '0')
        return "${rateCents / 100}.$dec"
    }

    /** Heurística: si el monto leído supera esto, probablemente es Bs. */
    fun bsThresholdCents(): Long = Money.toBs(thresholdUsdCents, rateCents)

    companion object {
        /** Umbral base editable desde Ajustes: $20 equivalente. */
        const val USD_THRESHOLD_CENTS = 2_000L
    }
}
