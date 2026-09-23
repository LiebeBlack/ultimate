package com.paylensu.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Retícula Sci-Fi del visor:
 *  - Idle = CERO animación (píxeles estáticos, CPU/GPU dormidas — batería).
 *  - Pulso al tocar: spring liviano (<150 ms) sobre SOLO transform de capa
 *    (scale/alpha) vía graphicsLayer → sin re-layout ni re-composición.
 *  - Barrido de escaneo: una pasada de 220 ms dibujada dentro del mismo
 *    draw pass (lectura de estado en draw: re-dibujo puro, sin recompose).
 */
@Composable
fun ReticleOverlay(
    modifier: Modifier = Modifier,
    scanning: Boolean,
    pulseTick: Int,
) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseTick) {
        if (pulseTick > 0) {
            pulse.snapTo(0.94f)
            pulse.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 1400f))
        }
    }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(scanning) {
        if (scanning) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(durationMillis = 220, easing = LinearEasing))
        } else {
            sweep.snapTo(0f)
        }
    }

    Box(modifier.graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
        alpha = 0.85f + 0.15f * pulse.value
    }) {
        Canvas(Modifier.fillMaxSize().drawWithCache {
            val stroke = 2.dp.toPx()
            val inset = 10.dp.toPx()
            val arm = size.minDimension * 0.16f
            val w = size.width
            val h = size.height
            onDrawBehind {
                val c = HudTheme.Cyan
                // 4 esquinas (brackets)
                drawLine(c, Offset(inset, inset + arm), Offset(inset, inset), stroke)
                drawLine(c, Offset(inset, inset), Offset(inset + arm, inset), stroke)
                drawLine(c, Offset(w - inset - arm, inset), Offset(w - inset, inset), stroke)
                drawLine(c, Offset(w - inset, inset), Offset(w - inset, inset + arm), stroke)
                drawLine(c, Offset(w - inset, h - inset - arm), Offset(w - inset, h - inset), stroke)
                drawLine(c, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), stroke)
                drawLine(c, Offset(inset + arm, h - inset), Offset(inset, h - inset), stroke)
                drawLine(c, Offset(inset, h - inset), Offset(inset, h - inset - arm), stroke)
                // textura: guías laterales al 15%
                val g = c.copy(alpha = 0.15f)
                drawLine(g, Offset(inset * 0.4f, h / 2f), Offset(inset, h / 2f), stroke)
                drawLine(g, Offset(w - inset, h / 2f), Offset(w - inset * 0.4f, h / 2f), stroke)
                // barrido de escaneo (solo mientras scanning)
                val s = sweep.value
                if (s > 0f && s < 1f) {
                    val y = inset + (h - 2 * inset) * s
                    drawLine(
                        color = HudTheme.Purple,
                        start = Offset(w * 0.18f, y),
                        end = Offset(w * 0.82f, y),
                        strokeWidth = 2.5f,
                    )
                }
            }
        }) {}
    }
}

/**
 * Recuadros animados del texto detectado (bounding boxes del OCR escalados a
 * coordenadas del preview). Stroke plano cian + relleno al 8%.
 */
@Composable
fun OcrBoxOverlay(
    boxes: List<Rect>,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    color: Color = HudTheme.Cyan,
) {
    Canvas(modifier) {
        boxes.forEachIndexed { i, r ->
            val c = if (i == highlightIndex) HudTheme.Purple else color
            drawRect(c.copy(alpha = 0.08f), topLeft = r.topLeft, size = r.size)
            drawRect(
                c,
                topLeft = r.topLeft,
                size = r.size,
                style = Stroke(width = if (i == highlightIndex) 3f else 1.8f),
            )
        }
    }
}
