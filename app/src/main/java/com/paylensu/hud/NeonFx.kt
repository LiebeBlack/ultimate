package com.paylensu.hud

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Primitivas de neón SIN blur (prohibido RenderEffect/gaussian: cuesta GPU en
 * gama baja). Todo efecto es stroke plano del color puro; el "halo" es un
 * segundo trazo más ancho con alpha bajo — 2 draw calls, cero shaders extra.
 */

/** Marco redondeado neón con halo plano. */
fun Modifier.neonFrame(
    color: Color = HudTheme.Cyan,
    stroke: Dp = HudTheme.StrokeMid,
    corner: Dp = 12.dp,
    halo: Boolean = true,
): Modifier = drawBehind {
    val s = stroke.toPx()
    val r = corner.toPx()
    if (halo) {
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = s * 3.5f),
        )
    }
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = s),
    )
}

/** Línea divisoria neón de 1 px lógico. */
fun Modifier.neonDivider(color: Color = HudTheme.Cyan.copy(alpha = 0.45f)): Modifier = drawBehind {
    val y = size.height / 2f
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
}

/** Borde superior neón (para la Sticky Bar pegada al borde de pantalla). */
fun Modifier.neonTopLine(
    color: Color = HudTheme.Cyan,
    stroke: Dp = HudTheme.StrokeMid,
): Modifier = drawBehind {
    val s = stroke.toPx()
    drawLine(color, Offset(0f, s / 2), Offset(size.width, s / 2), strokeWidth = s)
}

/** Relleno negro con textura de líneas diagonales al 8% (1 draw pass). */
fun Modifier.oledTexture(tint: Color = HudTheme.Cyan): Modifier = drawBehind {
    drawRect(HudTheme.Black)
    val step = 14f
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = tint.copy(alpha = 0.08f),
            start = Offset(x, 0f),
            end = Offset(x + size.height, size.height),
            strokeWidth = 1f,
        )
        x += step
    }
}

/**
 * Micro-interacción táctil: escala al presionar con spring liviano (<150 ms).
 * SOLO transform de capa vía graphicsLayer: cero re-layout, cero re-draw del
 * árbol. Pasar el MISMO [MutableInteractionSource] al clickable/Surface para
 * que observe las pulsaciones reales.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1600f),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
