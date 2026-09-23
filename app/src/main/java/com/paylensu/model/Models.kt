package com.paylensu.model

import com.paylensu.core.Money

/** Direccion de conversion inferida del contexto del precio leido. */
enum class CurrencySide { USD, VES }

/** Un precio detectado por OCR sobre la escena. */
data class DetectedPrice(
    val money: Money,
    val side: CurrencySide,
    val confidence: Int,          // 0..100: longitud de contexto, simbolo explicito, etc.
    val text: String,             // texto crudo leido
    val boxIndex: Int,            // indice del TextBlock en la respuesta OCR (-1 si global)
) : java.io.Serializable

/** Resultado completo de un escaneo (1..3 frames -> mejor frame -> OCR). */
data class ScanResult(
    val prices: List<DetectedPrice>,
    val elapsedMs: Long,
    val framesUsed: Int,
) {
    val top: DetectedPrice? get() = prices.firstOrNull()
}

/** Linea de la matrix de pago mixto para un billete dado. */
data class PaymentOption(
    val billCents: Long,          // billete en USD centavos (500 = $5)
    val cashCents: Long,          // efectivo USD aplicado
    val bsDue: Long,              // Bs a pagar por pago movil (centavos Bs)
    val changeUsd: Money,         // vuelto en USD (0 si no aplica)
    val changeBs: Long,           // mismo vuelto en Bs
    val optimal: Boolean,
) : java.io.Serializable

/** Item del carrito: entrada SQLite <= 1200 B (label truncado a 128 chars). */
data class CartEntry(
    val id: Long,
    val label: String,
    val amount: Money,
    val side: CurrencySide,
    val createdAt: Long,
) : java.io.Serializable {
    /** Garantia de tamano: id(8) + amount(8) + side(1) + created(8) + UTF-8(label<=128) <= 1200. */
    fun byteSize(): Int = 25 + label.toByteArray(Charsets.UTF_8).size
}

/** Tipo de movimiento de una transacción cobrada. */
enum class TxKind { MIXED, CASH, MOBILE, CART }

/**
 * Transacción cobrada (historial). En USD centavos con desglose en Bs y de
 * impuestos a cobrar al momento del cobro.
 */
data class TxEntry(
    val id: Long,
    val kind: TxKind,
    val totalUsd: Money,
    val cashUsd: Money,          // efectivo aplicado (0 si todo Pago Móvil)
    val mobileBs: Long,          // Pago Móvil en centavos de Bs (0 si todo efectivo)
    val changeUsd: Money,        // vuelto entregado en $ (0 si no aplica)
    val taxesUsd: Money,         // impuestos contenidos en el total (IVA/IGTF)
    val createdAt: Long,
) : java.io.Serializable

/** Estado de la cuenta: cuánto hay en efectivo $ y en Pago Móvil (Bs). */
data class WalletState(
    val cashUsdCents: Long = 0L,
    val mobileBsCents: Long = 0L,
) {
    fun plus(cashUsdCents: Long, mobileBsCents: Long): WalletState =
        WalletState(
            cashUsdCents = (this.cashUsdCents + cashUsdCents).coerceAtLeast(0L),
            mobileBsCents = (this.mobileBsCents + mobileBsCents).coerceAtLeast(0L),
        )
}
