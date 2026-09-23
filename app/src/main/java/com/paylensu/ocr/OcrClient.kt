package com.paylensu.ocr

import com.paylensu.core.ExchangeRate
import com.paylensu.model.DetectedPrice
import com.paylensu.parser.PriceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Cliente OCR: cadena frame->OCR->parser con presupuesto de tiempo estricto.
 * El OCR corre en Dispatchers.Default con timeout corto; si el motor tarda
 * más de [OCR_TIMEOUT_MS], se devuelve lo último conocido (o error) para que
 * el HUD nunca se cuelgue.
 */
class OcrClient(private val engine: OcrEngine) {

    /** Ejecuta OCR y parseo. Devuelve top-N precios de la escena. */
    suspend fun scanPrices(
        bitmap: android.graphics.Bitmap,
        rate: ExchangeRate,
        limit: Int = 3,
    ): List<DetectedPrice> = withTimeout(OCR_TIMEOUT_MS) {
        val output = engine.recognize(bitmap)
        PriceParser.scan(output.fullText, rate, limit)
    }

    suspend fun scanWithBlocks(
        bitmap: android.graphics.Bitmap,
        rate: ExchangeRate,
        limit: Int = 3,
    ): Pair<List<DetectedPrice>, OcrOutput?> = try {
        withTimeout(OCR_TIMEOUT_MS) {
            val output = engine.recognize(bitmap)
            PriceParser.scan(output.fullText, rate, limit) to output
        }
    } catch (e: TimeoutCancellationException) {
        emptyList<DetectedPrice>() to null
    }

    fun close() = engine.close()

    companion object {
        /** Presupuesto duro por escaneo: el objetivo percibido es < 100 ms. */
        const val OCR_TIMEOUT_MS = 400L
        val OCR_DISPATCHER = Dispatchers.Default
    }
}
