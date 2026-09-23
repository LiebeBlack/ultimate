package com.paylensu.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Cámara "Tap-to-Scan":
 *  - Preview continuo 720p (1280x720). El [ImageAnalysis] está enlazado pero
 *    SIN analyzer entre toques: no adquiere buffers, la CPU no procesa nada
 *    (prohibido OCR a 30 FPS).
 *  - Al tocar: ráfaga de 3 fotogramas del stream, se elige el más nítido por
 *    varianza de gradiente del plano Y y SOLO ese pasa al OCR.
 */
class CameraController {

    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private val analysisExecutor: Executor = Executors.newSingleThreadExecutor()

    private val resolution = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                android.util.Size(1280, 720),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
        )
        .build()

    suspend fun start(context: Context, owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val p = provider ?: ProcessCameraProvider.getInstance(context).await().also { provider = it }
        p.unbindAll()

        val preview = Preview.Builder()
            .setResolutionSelector(resolution)
            .build()
            .apply { setSurfaceProvider(surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        // Sin analyzer: ImageAnalysis no adquiere frames hasta que scanBurst lo arme.
        imageAnalysis.clearAnalyzer()

        p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        analysis = imageAnalysis
    }

    /**
     * Ráfaga de [frames] fotogramas. Devuelve el más nítido como Bitmap gris
     * (o null si la cámara no está lista). El caller recicla el bitmap.
     */
    suspend fun scanBurst(frames: Int = 3): Bitmap? {
        val imageAnalysis = analysis ?: return null
        val collected = ArrayList<ImageProxy>(frames)
        try {
            suspendCoroutine { cont ->
                var done = 0
                var resumed = false
                imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                    if (resumed) {
                        proxy.close() // frames que llegan mientras se suelta el analyzer
                        return@setAnalyzer
                    }
                    collected += proxy
                    done++
                    if (done >= frames) {
                        resumed = true
                        imageAnalysis.clearAnalyzer()
                        cont.resume(Unit)
                    }
                }
            }
            if (collected.isEmpty()) return null

            var bestIdx = 0
            var bestScore = -1L
            collected.forEachIndexed { i, img ->
                val plane = img.planes[0]
                val score = FramePrepper.sharpness(
                    y = plane.buffer,
                    width = img.width,
                    height = img.height,
                    rowStride = plane.rowStride,
                    pixelStride = plane.pixelStride,
                )
                if (score > bestScore) { bestScore = score; bestIdx = i }
            }
            val best = FramePrepper.toGrayBitmap(collected[bestIdx])
            return best
        } finally {
            // Libera los 3 buffers UNA sola vez (también en error/cancelación).
            // El doble close() sobre un ImageProxy ya cerrado puede lanzar.
            collected.forEach { runCatching { it.close() } }
        }
    }

    fun stop() {
        provider?.unbindAll()
    }

    private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
        suspendCoroutine { cont ->
            addListener({ cont.resume(get()) }, analysisExecutor)
        }
}
