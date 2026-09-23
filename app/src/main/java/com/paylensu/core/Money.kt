package com.paylensu.core

/**
 * Dinero entero en centavos. Prohibido Double/Float para montos: el redondeo
 * binario rompe la exactitud de vuelto y IVA.
 *
 * Es [Serializable] y cabe en 8 bytes: dentro del presupuesto de 1200 B por
 * entrada de carrito.
 */
data class Money(val cents: Long) : java.io.Serializable {

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun times(factor: Long): Money = Money(cents * factor)

    /** Multiplicación por porcentaje entero (16 -> +16%) con redondeo half-up. */
    fun percent(percent: Long): Money {
        val raw = cents * percent
        val half = if (raw >= 0) 50L else -50L
        return Money((raw + half) / 100L)
    }

    val isZero: Boolean get() = cents == 0L
    val isNegative: Boolean get() = cents < 0L

    fun abs(): Money = Money(if (cents < 0) -cents else cents)

    /**
     * Formato moneda estable: agrupación de miles y 2 decimales, sin depender
     * de locale del dispositivo (misma salida en QA y producción).
     * @param symbol ej. "$" o "Bs. "
     */
    fun format(symbol: String = "$"): String {
        val negative = cents < 0
        var n = if (negative) -cents else cents
        val dec = (n % 100).toString().padStart(2, '0')
        n /= 100
        var groups = n.toString()
        var i = groups.length - 3
        while (i > 0) {
            groups = groups.substring(0, i) + "," + groups.substring(i)
            i -= 3
        }
        return (if (negative) "-" else "") + symbol + groups + "." + dec
    }

    companion object {
        val ZERO = Money(0)

        /** Parsea "12.50", "1,234.56" o "1420" (entero = centavos directos). */
        fun parse(text: String): Money? {
            val t = text.trim()
            if (t.isEmpty()) return null
            return t.toLongOrNull()?.let { Money(it) }
                ?: t.toDoubleOrNull()?.let { Money(Math.round(it * 100)) }
        }

        /** Convierte un monto en Bs (centavos Bs) a USD (centavos) a la tasa dada. */
        fun fromBs(bsCents: Long, rateCents: Long): Money =
            Money(divRoundHalfUp(bsCents * 100, rateCents))

        /** Convierte USD (centavos) a Bs (centavos): usd * rate / 100. */
        fun toBs(usdCents: Long, rateCents: Long): Long =
            divRoundHalfUp(usdCents * rateCents, 100L)

        internal fun divRoundHalfUp(num: Long, den: Long): Long {
            if (den == 0L) return 0L
            val q = num / den
            val r = num % den
            val adjust = if (kotlin.math.abs(r * 2) >= kotlin.math.abs(den)) 1L else 0L
            val sign = if ((num < 0) xor (den < 0)) -1L else 1L
            return q + sign * adjust
        }
    }
}
