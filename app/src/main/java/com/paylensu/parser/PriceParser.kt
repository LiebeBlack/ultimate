package com.paylensu.parser

import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.model.CurrencySide
import com.paylensu.model.DetectedPrice

/**
 * Smart Regex del parser financiero.
 *
 * Reglas:
 *  - Simbolo explicito manda: "$"/"USD" -> USD; "Bs"/"VES" -> VES.
 *  - Sin simbolo: heuristica por magnitud contra la tasa guardada
 *    (mayor a umbral*tasa -> Bs; menor -> USD) + cotas de sanidad.
 *  - Palabras de recibo (TOTAL, PAGAR, IMPORTE...) impulsan la confianza:
 *    la disposicion del texto dice tanto como el simbolo.
 *  - Acepta separadores "1.234,56" (es) y "1,234.56" (en).
 *  - Devuelve top-3 ordenado por confianza para desglose en HUD.
 */
object PriceParser {

    private const val SYM = """(?:[$]\s*|US[D$]\s*|Bs\.?\s*|VES\s*)?"""

    // int: 1.234 / 1,234 / 123   dec: ,56 / .56
    private val LINE = Regex(
        pattern = SYM +
            """(?<int>\d{1,3}(?:[.,]\d{3})+|\d+)""" +
            """(?:[.,](?<dec>\d{1,2}))?""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    // Marcas de moneda en el contexto de la cifra (ventana +-8 chars).
    private val USD_CTX = Regex("""[$]|US[D$]""", RegexOption.IGNORE_CASE)
    private val VES_CTX = Regex("""Bs|VES""", RegexOption.IGNORE_CASE)

    // Palabras de recibo: sin simbolo, su presencia sube la confianza.
    private val RECEIPT_HINT =
        Regex("""total|subtotal|pagar|importe|amount|abonar|cancelar""", RegexOption.IGNORE_CASE)

    /** Cotas de sanidad para cifras SIN simbolo: $0.01 .. $9,999,999.99. */
    private const val MIN_PLAUSIBLE_CENTS = 1L
    private const val MAX_PLAUSIBLE_CENTS = 999_999_999L

    /** Escanea todo el texto OCR y devuelve hasta [limit] precios ordenados. */
    fun scan(text: String, rate: ExchangeRate, limit: Int = 3): List<DetectedPrice> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<DetectedPrice>(limit)
        var boxIndex = 0
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.length < 2) { boxIndex++; continue }
            val lineHint = RECEIPT_HINT.containsMatchIn(line)
            for (m in LINE.findAll(line)) {
                val intPart = m.groups["int"]?.value ?: continue
                val decPart = m.groups["dec"]?.value
                val cents = normalize(intPart, decPart) ?: continue
                val window = line.substring(
                    (m.range.first - 8).coerceAtLeast(0),
                    (m.range.last + 9).coerceAtMost(line.length)
                )
                val hasUsd = USD_CTX.containsMatchIn(window)
                val hasVes = VES_CTX.containsMatchIn(window)
                val explicit = (hasUsd xor hasVes)
                val side = when {
                    hasUsd && !hasVes -> CurrencySide.USD
                    hasVes && !hasUsd -> CurrencySide.VES
                    else -> magnitudeSide(cents, rate)
                }
                var confidence = when {
                    explicit -> 90 + (if (decPart != null) 5 else 0)
                    decPart != null -> 55
                    else -> 40
                }
                if (!explicit && lineHint) confidence += 8
                if (confidence > 99) confidence = 99
                // Cifras sin simbolo fuera de cotas = ruido OCR (fechas, codigos
                // largos): se descartan SIEMPRE. Con simbolo explícito se confían.
                if (!explicit && cents !in MIN_PLAUSIBLE_CENTS..MAX_PLAUSIBLE_CENTS) continue
                if (out.size < limit || confidence > out.minOf { it.confidence }) {
                    if (out.size == limit) {
                        val idx = out.indices.minBy { out[it].confidence }
                        if (confidence > out[idx].confidence) out[idx] = DetectedPrice(
                            Money(cents), side, confidence, m.value.trim(), boxIndex
                        )
                    } else {
                        out += DetectedPrice(Money(cents), side, confidence, m.value.trim(), boxIndex)
                    }
                }
                break // una sola cifra por línea: evita duplicar fechas/códigos
            }
            boxIndex++
        }
        return out.sortedByDescending { it.confidence }
    }

    /** Normaliza separadores ES/EN a centavos. Devuelve null si no parece precio. */
    internal fun normalize(intPart: String, decPart: String?): Long? {
        val intClean = intPart.replace(".", "").replace(",", "")
        // "000" o "007" sin decimales suele ser codigo de producto, no precio.
        if (intClean.length > 1 && intClean[0] == '0') return null
        val dec = decPart ?: return intClean.toLongOrNull()?.let { it * 100 }
        if (dec.length == 1) {
            // "12,5" -> 12.50 (el dígito único es decena de centavos).
            return (intClean.toLongOrNull() ?: return null) * 100 + (dec.single() - '0') * 10
        }
        return (intClean.toLongOrNull() ?: return null) * 100 + dec.take(2).toLong()
    }

    private fun magnitudeSide(cents: Long, rate: ExchangeRate): CurrencySide =
        if (cents > rate.bsThresholdCents()) CurrencySide.VES else CurrencySide.USD
}
