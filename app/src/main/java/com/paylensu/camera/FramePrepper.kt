package com.paylensu.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Normalización de frames: plano Y -> bitmap gris (r=g=b) + downscale a
 * 1080 px máx. Trabaja solo con el plano Y (sin decodificar UV): ~60 % menos
 * trabajo antes del OCR y suficiente para leer precios.
 */
object FramePrepper {

    const val MAX_DIM = 1080

    /** Nitidez barata: varianza del gradiente vertical sobre filas muestreadas. */
    fun sharpness(y: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): Long {
        var acc = 0L
        var count = 0L
        var prev = 0
        var row = 0
        while (row < height) {
            val base = row * rowStride
            var x = 1
            while (x < width - 1) {
                val v = y.get(base + x * pixelStride).toInt() and 0xFF
                if (x > 1) {
                    val d = v - prev
                    acc += d.toLong() * d
                    count++
                }
                prev = v
                x += 4
            }
            row += 4
        }
        return if (count == 0L) 0L else acc / count
    }

    /**
     * Convierte el mejor frame a Bitmap gris rotado y reducido.
     * El caller es dueño del bitmap retornado (lo recicla).
     */
    fun toGrayBitmap(image: ImageProxy): Bitmap {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        buffer.rewind()
        val width = image.width
        val height = image.height
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        val pixels = IntArray(width * height)
        var p = 0
        for (row in 0 until height) {
            var idx = row * rowStride
            for (col in 0 until width) {
                val y = buffer.get(idx).toInt() and 0xFF
                pixels[p++] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
                idx += pixelStride
            }
        }
        var bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        bmp = rotate(bmp, image.imageInfo.rotationDegrees)
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide > MAX_DIM) {
            val scale = MAX_DIM.toFloat() / maxSide
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }
        return bmp
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
            .also { if (it != src) src.recycle() }
    }
}
