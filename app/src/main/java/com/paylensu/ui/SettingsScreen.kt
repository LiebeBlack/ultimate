package com.paylensu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paylensu.PayLensApp
import com.paylensu.core.ExchangeRate
import com.paylensu.hud.HudTheme
import com.paylensu.hud.neonDivider
import com.paylensu.hud.neonFrame
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Ajustes mínimos: tasa BCV manual (offline-first), IVA y umbral $/Bs.
 * Cada campo escribe directo en DataStore; la UI reacciona por Flow.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rateState by PayLensApp.Graph.rateStore.state.collectAsStateWithLifecycle(
        initialValue = com.paylensu.data.RateState(null, 16, 2_000, null)
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxSize()
            .background(HudTheme.Black)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                color = HudTheme.Black,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.neonFrame(HudTheme.Cyan, corner = 8.dp, halo = false),
            ) {
                Text(
                    "← VOLVER",
                    style = HudTheme.LabelBright,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(0.dp))
            Text(
                "  AJUSTES",
                style = HudTheme.PriceMid,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().neonDivider()) {}

        // ---- Tasa ----
        Text("TASA BCV", style = HudTheme.LabelBright, modifier = Modifier.padding(top = 16.dp))
        val savedRate = rateState.rate
        Text(
            text = savedRate?.let { "Actual: ${it.format()} (${it.source.name})" } ?: "Sin tasa guardada",
            style = HudTheme.Label,
        )

        var manual by remember(savedRate?.rateCents) {
            mutableStateOf(savedRate?.format() ?: "")
        }
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            label = { Text("Tasa manual (Bs por $1)", style = HudTheme.Label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HudTheme.Cyan,
                unfocusedBorderColor = HudTheme.TextSecondary.copy(alpha = 0.4f),
                cursorColor = HudTheme.Cyan,
                focusedTextColor = HudTheme.TextPrimary,
                unfocusedTextColor = HudTheme.TextPrimary,
            ),
        )
        Row(Modifier.padding(top = 8.dp)) {
            Surface(
                onClick = {
                    manual.replace(',', '.').toDoubleOrNull()?.let { bs ->
                        if (bs > 0) {
                            val cents = Math.round(bs * 100)
                            scope.launch {
                                PayLensApp.Graph.rateStore.saveRate(cents, ExchangeRate.Source.MANUAL)
                            }
                        }
                    }
                },
                color = HudTheme.Cyan,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "GUARDAR TASA",
                    style = HudTheme.Button,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Surface(
                onClick = {
                    scope.launch { PayLensApp.Graph.rateStore.setOcrError(null) }
                },
                color = HudTheme.Black,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 8.dp).neonFrame(HudTheme.Purple, corner = 8.dp, halo = false),
            ) {
                Text(
                    "LIMPIAR AVISO OCR",
                    style = HudTheme.LabelBright.copy(color = HudTheme.Purple),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        if (rateState.ocrEngineError != null) {
            Text(
                "Motor OCR: ${rateState.ocrEngineError}",
                style = HudTheme.Label.copy(color = HudTheme.Warn),
            )
        }

        // ---- IVA ----
        Text("IVA (%)", style = HudTheme.LabelBright, modifier = Modifier.padding(top = 24.dp))
        Row {
            listOf(0L, 8L, 16L).forEach { p ->
                val selected = rateState.ivaPercent == p
                Surface(
                    onClick = {
                        scope.launch { PayLensApp.Graph.rateStore.saveIva(p) }
                    },
                    color = HudTheme.Black,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .neonFrame(
                            if (selected) HudTheme.Purple else HudTheme.Cyan.copy(alpha = 0.5f),
                            corner = 8.dp,
                            halo = false,
                        ),
                ) {
                    Text(
                        "$p%",
                        style = if (selected) HudTheme.LabelBright.copy(color = HudTheme.Purple) else HudTheme.LabelBright,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // ---- Umbral heurístico ----
        Text(
            "UMBRAL $/Bs SIN SÍMBOLO ($ equivalentes)",
            style = HudTheme.LabelBright,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Si el precio leído supera TASA × umbral, se interpreta como Bs.",
            style = HudTheme.Label,
        )
        Row(Modifier.padding(top = 8.dp)) {
            listOf(10_00L, 20_00L, 50_00L).forEach { c ->
                val selected = rateState.thresholdUsdCents == c
                Surface(
                    onClick = {
                        scope.launch { PayLensApp.Graph.rateStore.saveThresholdUsd(c) }
                    },
                    color = HudTheme.Black,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .neonFrame(
                            if (selected) HudTheme.Purple else HudTheme.Cyan.copy(alpha = 0.5f),
                            corner = 8.dp,
                            halo = false,
                        ),
                ) {
                    Text(
                        "$${c / 100}",
                        style = if (selected) HudTheme.LabelBright.copy(color = HudTheme.Purple) else HudTheme.LabelBright,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
