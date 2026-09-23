package com.paylensu.hud

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tema HUD OLED puro:
 *  - Fondo #000000 absoluto (píxeles apagados en AMOLED).
 *  - Neón por stroke vectorial PLANO: cero blur, cero sombras gaussianas,
 *    cero gradientes. El "glow" se simula con un stroke más ancho a baja
 *    alpha del mismo color puro (barato: 1 draw call extra).
 */
object HudTheme {
    val Black = Color(0xFF000000)
    val Cyan = Color(0xFF00F3FF)
    val Purple = Color(0xFF9D00FF)
    val CyanAlpha = Color(0x2600F3FF)   // texturas / rellenos al 15%
    val PurpleAlpha = Color(0x269D00FF)
    val TextPrimary = Color(0xFFE9FDFF)
    val TextSecondary = Color(0xFF7FA6AC)
    val Warn = Color(0xFFFFB300)

    val StrokeThin: Dp = 1.dp
    val StrokeMid: Dp = 1.5.dp
    val StrokeThick: Dp = 2.dp

    // Cifras en monoespaciada tabular: ancho estable, sin reflow al cambiar dígitos.
    val PriceBig = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        color = Cyan,
    )
    val PriceMid = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = TextPrimary,
    )
    val Label = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        color = TextSecondary,
    )
    val LabelBright = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = Cyan,
    )
    val Button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Black,
    )
}
