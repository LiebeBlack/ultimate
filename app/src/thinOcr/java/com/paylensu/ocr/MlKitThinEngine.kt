package com.paylensu.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.tasks.Task
// El artifact thin expone las MISMAS APIs que el bundled: el FQCN compartido
// es lo que permite intercambiar flavors sin tocar el resto de la app.
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Motor OCR thin: usa Play Services (APK pequeño, modelo descargable).
 * Si el módulo no está disponible, la Task falla y la UI muestra el error
 * con opción de reintentar tras la descarga automática de GMS.
 */
class MlKitThinEngine(private val appContext: Context) : OcrEngine {

    private val client by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    val available: Boolean
        get() = GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    override suspend fun recognize(bitmap: Bitmap): OcrOutput {
        if (!available) throw IllegalStateException("Play Services sin módulo de texto")
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = client.process(image).awaitCompat()
        val blocks = result.textBlocks.map { b ->
            OcrBlock(text = b.text, bounds = b.boundingBox ?: Rect())
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

/** Factoría del flavor thinOcr. Misma firma que la bundled. */
object OcrFactoryImpl {
    fun create(context: Context): OcrFactory = object : OcrFactory {
        override fun create(): OcrEngine = MlKitThinEngine(context)
        override fun describe(): String = "ML Kit thin (Play Services)"
    }
}
