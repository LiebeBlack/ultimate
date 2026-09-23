package com.paylensu.overlay

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.paylensu.core.Money
import com.paylensu.hud.HudTheme
import com.paylensu.hud.NumericKeypad
import com.paylensu.hud.ResultCard
import com.paylensu.hud.neonDivider
import com.paylensu.hud.neonFrame
import com.paylensu.hud.pressScale

/** Acciones que el panel/head le piden al servicio. */
interface BubbleActions {
    fun onToggleExpand()
    fun onDrag(dx: Float, dy: Float)
    fun onDragEnd()
    fun onToggleCamera()
    fun onScan()
    fun onClose()
    fun onSelectPrice(index: Int)
    fun onSelectBill(cents: Long?)
    fun onAddCart()
    fun onCharge()
    fun onOpenKeypad()
    fun onKeypadChange(value: String)
    fun onKeypadDone(money: Money?)
    fun onKeypadClose()
}

/**
 * Burbuja flotante: head 84dp arrastrable (tap = expandir) ↔ panel 300dp con
 * mini-escáner, ResultCard reutilizada del HUD y teclado. Todo stroke plano.
 */
@Composable
fun BubbleRoot(
    expanded: Boolean,
    cameraOn: Boolean,
    scanning: Boolean,
    rateText: String,
    itemCount: Int,
    totalUsd: Money,
    totalBs: Money,
    priceChips: List<String>,
    selectedIndex: Int,
    selectedPrice: com.paylensu.model.DetectedPrice?,
    rate: com.paylensu.core.ExchangeRate?,
    ivaPercent: Long,
    selectedBillCents: Long?,
    keypadOpen: Boolean,
    keypadValue: String,
    message: String?,
    wallet: com.paylensu.model.WalletState,
    actions: BubbleActions,
    onPreviewReady: (PreviewView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) {
        // ---- Chat head ----
        Surface(
            color = HudTheme.Black,
            shape = CircleShape,
            modifier = modifier
                .size(84.dp)
                .neonFrame(HudTheme.Cyan, stroke = HudTheme.StrokeThick, corner = 42.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            actions.onDrag(amount.x, amount.y)
                        },
                        onDragEnd = { actions.onDragEnd() },
                    )
                }
                .clickable { actions.onToggleExpand() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("P$", style = HudTheme.PriceMid.copy(color = HudTheme.Purple))
            }
        }
    } else {
        // ---- Panel expandido ----
        Surface(
            color = HudTheme.Black,
            shape = RoundedCornerShape(14.dp),
            modifier = modifier
                .width(300.dp)
                .neonFrame(HudTheme.Cyan, stroke = HudTheme.StrokeMid, corner = 14.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("TASA $rateText", style = HudTheme.LabelBright)
                    Text(
                        "✕",
                        style = HudTheme.LabelBright,
                        modifier = Modifier
                            .clickable { actions.onToggleExpand() }
                            .padding(4.dp),
                    )
                }
                Text(
                    "$itemCount items • ${totalUsd.format("$")} • ${totalBs.format("Bs. ")}",
                    style = HudTheme.Label,
                )
                // Cuenta: cuánto hay en efectivo $ y en Pago Móvil (Bs).
                Text(
                    "CUENTA  ${Money(wallet.cashUsdCents).format("$")}  •  ${Money(wallet.mobileBsCents).format("Bs. ")}",
                    style = HudTheme.Label.copy(color = HudTheme.Warn),
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).neonDivider())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton(
                        label = if (cameraOn) "CÁMARA ON" else "CÁMARA",
                        accent = cameraOn,
                        modifier = Modifier.weight(1f),
                    ) { actions.onToggleCamera() }
                    MiniButton(
                        label = if (scanning) "…" else "ESCANEAR",
                        accent = false,
                        modifier = Modifier.weight(1f),
                        enabled = cameraOn && !scanning,
                    ) { actions.onScan() }
                }

                // Mini-preview: solo cuando la cámara está encendida.
                var pv by remember { mutableStateOf<PreviewView?>(null) }
                if (cameraOn) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            }
                        },
                        update = { pv = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(vertical = 8.dp),
                    )
                    LaunchedEffect(pv) { onPreviewReady(pv) }
                } else {
                    Spacer(Modifier.height(8.dp))
                    LaunchedEffect(Unit) { onPreviewReady(null) }
                }

                if (message != null) {
                    Text(message, style = HudTheme.Label.copy(color = HudTheme.Warn))
                }

                // Top-3 precios detectados.
                if (priceChips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        priceChips.forEachIndexed { i, chip ->
                            MiniButton(
                                label = chip,
                                accent = i == selectedIndex,
                                modifier = Modifier.weight(1f),
                            ) { actions.onSelectPrice(i) }
                        }
                    }
                }
                val price = selectedPrice
                val rateState = rate
                if (price != null && rateState != null) {
                    ResultCard(
                        price = price,
                        rate = rateState,
                        ivaPercent = ivaPercent,
                        selectedBillCents = selectedBillCents,
                        onSelectBill = actions::onSelectBill,
                        onAddCart = actions::onAddCart,
                        onEditPrice = actions::onOpenKeypad,
                        onCharge = actions::onCharge,
                    )
                }

                // Teclado dentro del panel: entra expandiéndose (sin saltos de layout).
                AnimatedVisibility(
                    visible = keypadOpen,
                    enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(160)),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        NumericKeypad(
                            value = keypadValue,
                            onValueChange = actions::onKeypadChange,
                            onDone = actions::onKeypadDone,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "cerrar",
                            style = HudTheme.Label,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { actions.onKeypadClose() },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                MiniButton(
                    label = "CERRAR BURBUJA",
                    accent = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { actions.onClose() }
            }
        }
    }
}

@Composable
private fun MiniButton(
    label: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> HudTheme.TextSecondary.copy(alpha = 0.4f)
        accent -> HudTheme.Purple
        else -> HudTheme.Cyan
    }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        color = HudTheme.Black,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .pressScale(interaction, 0.94f)
            .neonFrame(color, stroke = HudTheme.StrokeThin, corner = 8.dp, halo = false),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = HudTheme.LabelBright.copy(
                    color = if (enabled) color else HudTheme.TextSecondary.copy(alpha = 0.4f),
                ),
            )
        }
    }
}
