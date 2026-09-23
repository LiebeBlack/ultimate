package com.paylensu.hud

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paylensu.core.Money
import com.paylensu.hud.pressScale

/**
 * Micro-calculadora táctil (Overlay Numeric Pad): corrige dígitos distorsionados
 * por la iluminación del estante SIN volver a fotografiar. Grid 4x3 plano,
 * ripple limitado, cero animaciones de layout (solo fade).
 */
@Composable
fun NumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: (Money?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = HudTheme.Black,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.neonFrame(HudTheme.Purple).padding(12.dp)) {
            // Display
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .neonFrame(HudTheme.Cyan, corner = 8.dp, halo = false)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = value.ifEmpty { "0.00" },
                    style = HudTheme.PriceMid,
                )
            }
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "⌫"),
            )
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        KeypadKey(
                            label = key,
                            modifier = Modifier.weight(1f).height(52.dp),
                            onClick = {
                                when (key) {
                                    "⌫" -> onValueChange(value.dropLast(1))
                                    "." -> if (!value.contains('.')) onValueChange(
                                        if (value.isEmpty()) "0." else "$value."
                                    )
                                    else -> if (value.length < 12) onValueChange(value + key)
                                }
                            },
                        )
                    }
                    // Columna 4: C arriba, OK abajo (una sola tecla viva por columna)
                    when (row.first()) {
                        "1" -> KeypadKey("C", Modifier.weight(1f).height(52.dp)) { onValueChange("") }
                        "7" -> KeypadKey(
                            "OK",
                            Modifier.weight(1f).height(52.dp),
                            accent = true,
                        ) { onDone(Money.parse(value)) }
                        else -> KeypadKey("", Modifier.weight(1f).height(52.dp), enabled = false) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> HudTheme.TextSecondary.copy(alpha = 0.3f)
        accent -> HudTheme.Purple
        else -> HudTheme.Cyan
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .pressScale(interaction, 0.92f) // teclas: respuesta más notoria
            .neonFrame(color, corner = 8.dp, halo = false),
        color = HudTheme.Black,
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (accent) HudTheme.LabelBright else HudTheme.PriceMid,
                color = if (enabled) color else HudTheme.TextSecondary.copy(alpha = 0.3f),
            )
        }
    }
}
