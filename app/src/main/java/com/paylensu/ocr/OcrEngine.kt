package com.paylensu.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Abstracción del motor OCR. Los flavors bundledOcr/thinOcr proveen la
 * implementación real vía [OcrFactory] con el mismo FQCN.
 */
interface OcrEngine {
    /**
     * Reconoce texto sobre un bitmap ya normalizado (gris, <= 1080 px,
     * rotado a vertical). Debe llamarse desde un dispatcher de CPU.
     */
    suspend fun recognize(bitmap: Bitmap): OcrOutput

    /** Libera el cliente nativo si el motor lo requiere. */
    fun close() = Unit
}

/** Bloque reconocido con su caja en coordenadas de imagen. */
data class OcrBlock(
    val text: String,
    val bounds: Rect,
)

data class OcrOutput(
    val fullText: String,
    val blocks: List<OcrBlock>,
)

/** Factoría por flavor: misma firma, implementación distinta. */
interface OcrFactory {
    fun create(): OcrEngine
    /** Mensaje de error amigable si el motor no está disponible. */
    fun describe(): String
}
