package com.paylensu.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Motor OCR bundled: modelo Latin (~6 MB) incluido en el APK. No requiere
 * Google Play Services ni descarga en el primer uso — clave en Android Go.
 */
class MlKitBundledEngine : OcrEngine {

    private val client by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrOutput {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = client.process(image).awaitCompat()
        val blocks = result.textBlocks.map { b ->
            OcrBlock(
                text = b.text,
                bounds = b.boundingBox ?: Rect(),
            )
        }
        return OcrOutput(fullText = result.text, blocks = blocks)
    }

    override fun close() = client.close()

    private suspend fun <T> Task<T>.awaitCompat(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            addOnCanceledListener { if (cont.isActive) cont.cancel() }
        }
}

/** Factoría del flavor bundledOcr. Misma firma que la thin. */
object OcrFactoryImpl {
    fun create(context: android.content.Context): OcrFactory = object : OcrFactory {
        override fun create(): OcrEngine = MlKitBundledEngine()
        override fun describe(): String = "ML Kit (modelo incluido en APK)"
    }
}
