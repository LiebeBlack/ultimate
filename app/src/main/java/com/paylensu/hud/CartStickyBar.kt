package com.paylensu.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.paylensu.core.Money

/**
 * HUD Sticky Bar (footer fijo): barra negra OLED con textura diagonal sutil y
 * borde neón superior. Muestra en vivo: nº de items, total USD y su
 * equivalente exacto en VES, con PULSO spring cuando cambian los totales
 * (feedback sin vibración ni redraws fuera de la capa). Affordance "DESGLOSE"
 * invita a deslizar/abrir el sheet. navigationBarsPadding defensivo: nunca
 * queda bajo la barra gestual.
 */
@Composable
fun CartStickyBar(
    itemCount: Int,
    totalUsd: Money,
    totalBs: Money,
    rateText: String,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pulso 1.0 -> 1.04 -> 1.0 SOLO en transform de capa al cambiar totales.
    val pulse = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(totalUsd, totalBs) {
        if (!initialized) {
            initialized = true
        } else {
            pulse.snapTo(1.04f)
            pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 1200f))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .oledTexture(HudTheme.Cyan)
            .neonTopLine(HudTheme.Cyan, HudTheme.StrokeMid)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpenSheet,
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$itemCount items", style = HudTheme.LabelBright)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TASA $rateText", style = HudTheme.Label)
                Text(
                    "  DESGLOSE ▲",
                    style = HudTheme.Label.copy(color = HudTheme.Cyan),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                    // Pivot a la izquierda: el monto "late" desde su origen.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(totalUsd.format("$"), style = HudTheme.PriceMid)
            Text(totalBs.format("Bs. "), style = HudTheme.PriceMid.copy(color = HudTheme.Purple))
        }
    }
}
