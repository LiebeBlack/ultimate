package com.paylensu.finance

import com.paylensu.core.Money

/** Calculos de precio: IVA configurable y totales. Todo en centavos. */
object PriceMath {

    /** Precio con IVA incluido. [percent] = 16 por defecto (editable en Ajustes). */
    fun withIva(net: Money, percent: Long): Money = net + net.percent(percent)

    /** Desglose: cuanto del total es IVA. */
    fun ivaOf(total: Money, percent: Long): Money {
        // total = net * (100+p)/100  =>  iva = total * p / (100+p)
        val raw = total.cents * percent
        val half = if (raw >= 0) 50L else -50L
        return Money((raw + half) / (100 + percent))
    }

    fun netOf(total: Money, percent: Long): Money = total - ivaOf(total, percent)

    /**
     * IGTF (Impuesto a las Grandes Transacciones Financieras) sobre pagos en
     * moneda extranjera (efectivo $). 3% por defecto, editable en llamadas.
     */
    fun igtf(total: Money, percent: Long = 3): Money = total.percent(percent)

    /**
     * Impuestos TOTALES a cobrar en una transacción: IVA contenido en el
     * total + IGTF si hubo parte pagada en efectivo $. Mismo cálculo que la
     * UI muestra y que el cobro asienta: jamás divergen.
     */
    fun taxesToCollect(totalWithIva: Money, ivaPercent: Long, cashUsdCents: Long): Money {
        val t = ivaOf(totalWithIva, ivaPercent)
        return if (cashUsdCents > 0) t + igtf(totalWithIva) else t
    }
}
