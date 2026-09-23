package com.paylensu.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paylensu.PayLensApp
import com.paylensu.data.CartRepository
import com.paylensu.hud.CartSheet
import com.paylensu.hud.CartStickyBar
import com.paylensu.hud.HudTheme
import com.paylensu.hud.NumericKeypad
import com.paylensu.hud.OcrBoxOverlay
import com.paylensu.hud.ResultCard
import com.paylensu.hud.ReticleOverlay
import com.paylensu.hud.WalletSheet
import com.paylensu.hud.neonFrame
import com.paylensu.hud.pressScale
import kotlinx.coroutines.delay

/**
 * Visor Tap-to-Scan: preview 720p + HUD neón. Toda la CPU pesada sucede
 * SOLO dentro de model.tapScan(); entre toques el árbol Compose queda quieto.
 */
@Composable
fun ScannerScreen(
    model: ScannerModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current

    // ---- Permiso de cámara (único runtime permission) ----
    var hasCamera by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }
    LaunchedEffect(Unit) {
        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasCamera) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // ---- Carrito: recomposición mínima (solo la sticky bar y el sheet leen esto) ----
    val cartEntries by PayLensApp.Graph.cart.entries.collectAsStateWithLifecycle()
    val txHistory by PayLensApp.Graph.tx.entries.collectAsStateWithLifecycle()
    val totals = remember(cartEntries, state.rate) {
        CartRepository.totals(cartEntries, state.rate)
    }

    // Tamaño del área de preview + aspecto del bitmap para mapear los bounding
    // boxes con la MISMA geometría de PreviewView.FILL_CENTER (escala mayor +
    // centrado), evitando cajas desplazadas en pantallas 19.5:9.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val boxRects = remember(state.boxes, viewSize, state.bitmapAspect) {
        val vw = viewSize.width.toFloat()
        val vh = viewSize.height.toFloat()
        if (vw == 0f || vh == 0f) {
            emptyList()
        } else {
            val a = if (state.bitmapAspect > 0f) state.bitmapAspect else vw / vh
            val scale = if (vw / vh > a) vw / a else vh
            val imgW = a * scale
            val imgH = scale
            val dx = (vw - imgW) / 2f
            val dy = (vh - imgH) / 2f
            state.boxes.map {
                androidx.compose.ui.geometry.Rect(
                    it.left * imgW + dx,
                    it.top * imgH + dy,
                    it.right * imgW + dx,
                    it.bottom * imgH + dy,
                )
            }
        }
    }

    // Auto-dismiss del mensaje transitorio.
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2600)
            model.dismissMessage()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(HudTheme.Black)
            .onSizeChanged { viewSize = it }
            .clickable(
                enabled = hasCamera && !state.scanning && !state.keypadOpen && !state.sheetOpen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // Confirmación táctil del disparo: el HUD "responde" antes del OCR.
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                model.tapScan()
            },
    ) {
        // Preview continuo 720p (barato: GPU compone, CPU duerme).
        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        if (hasCamera) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        // PERFORMANCE = SurfaceView: cero copia al árbol Compose,
                        // menos latencia y batería en gama baja.
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    }
                },
                update = { pv -> previewView = pv },
                modifier = Modifier.fillMaxSize(),
            )
        }
        LaunchedEffect(previewView, hasCamera) {
            val pv = previewView
            if (pv != null && hasCamera) {
                model.cameraController.start(context, owner, pv.surfaceProvider)
            }
        }

        // Retícula neón (idle = estática) + cajas OCR animadas.
        ReticleOverlay(
            modifier = Modifier.fillMaxSize(),
            scanning = state.scanning,
            pulseTick = state.pulseTick,
        )
        OcrBoxOverlay(boxes = boxRects, modifier = Modifier.fillMaxSize())

        // Barra superior: tasa + burbuja + ajustes.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rate = state.rate
            Surface(
                color = HudTheme.Black,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.neonFrame(HudTheme.Cyan, corner = 8.dp, halo = false),
            ) {
                Text(
                    text = if (rate != null) "TASA BCV ${rate.format()} • ${rate.source.name}"
                    else "SIN TASA",
                    style = HudTheme.LabelBright,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopChip("BURBUJA") {
                    com.paylensu.overlay.BubbleService.start(context)
                }
                TopChip("CUENTA") { model.openWalletSheet() }
                TopChip(label = "AJUSTES", onClick = onOpenSettings)
            }
        }

        // Mensaje transitorio: entra cae desde arriba, sale fade (sin pop brusco).
        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { -it / 3 },
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                color = HudTheme.Black,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(top = 120.dp)
                    .neonFrame(HudTheme.Purple, corner = 8.dp, halo = false),
            ) {
                Text(
                    state.message.orEmpty(),
                    style = HudTheme.LabelBright,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Hint inicial.
        if (state.prices.isEmpty() && !state.scanning) {
            Text(
                "Toca la retícula para escanear",
                style = HudTheme.Label,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 60.dp),
            )
        }

        // Selección de los top-3 precios + Card HUD inteligente.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            if (state.prices.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.prices.forEachIndexed { i, p ->
                        TopChip(
                            label = p.text,
                            selected = i == state.selected,
                        ) { model.selectPrice(i) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val selected = state.prices.getOrNull(state.selected)
            val rate = state.rate
            // La Card entra EXPANDIÉNDOSE al aparecer el primer precio y se
            // recoge al limpiar: la disposición respira con cada escaneo.
            AnimatedVisibility(
                visible = selected != null && rate != null,
                enter = fadeIn(tween(120)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(160)),
            ) {
                if (selected != null && rate != null) {
                    ResultCard(
                        price = selected,
                        rate = rate,
                        ivaPercent = state.ivaPercent,
                        selectedBillCents = state.selectedBillCents,
                        onSelectBill = model::selectBill,
                        onAddCart = model::addToCart,
                        onEditPrice = model::openKeypad,
                        onCharge = model::chargePayment,
                    )
                }
            }
        }

        // HUD Sticky Bar del carrito (viva en todo momento).
        CartStickyBar(
            itemCount = cartEntries.size,
            totalUsd = totals.first,
            totalBs = totals.second,
            rateText = state.rate?.format() ?: "—",
            onOpenSheet = model::openSheet,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Teclado numérico overlay: scrim con fade FIJO + keypad que sube deslizándose.
        AnimatedVisibility(
            visible = state.keypadOpen,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HudTheme.Black.copy(alpha = 0.72f))
                    .clickable { model.closeKeypad() },
            )
        }
        AnimatedVisibility(
            visible = state.keypadOpen,
            enter = fadeIn(tween(140)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NumericKeypad(
                value = state.keypadValue,
                onValueChange = model::keypadChange,
                onDone = model::keypadDone,
                modifier = Modifier
                    .padding(12.dp)
                    .width(320.dp),
            )
        }

        // Sheet del carrito.
        if (state.sheetOpen) {
            CartSheet(
                entries = cartEntries,
                totalUsd = totals.first,
                totalBs = totals.second,
                rateText = state.rate?.format() ?: "—",
                ivaPercent = state.ivaPercent,
                onRemove = model::removeEntry,
                onClear = model::clearCart,
                onUndo = model::undoClear,
                onCharge = model::chargeCart,
                onDismiss = model::closeSheet,
            )
        }

        // Sheet de la CUENTA: saldo, fijado manual e historial de movimientos.
        if (state.walletSheetOpen) {
            WalletSheet(
                wallet = state.wallet,
                rate = state.rate,
                history = txHistory,
                onSetWallet = model::setWallet,
                onClearHistory = model::clearHistory,
                onDismiss = model::closeWalletSheet,
            )
        }
    }
}

@Composable
private fun TopChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = HudTheme.Black,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .pressScale(interaction)
            .neonFrame(
                if (selected) HudTheme.Purple else HudTheme.Cyan,
                stroke = if (selected) HudTheme.StrokeMid else HudTheme.StrokeThin,
                corner = 8.dp,
                halo = false,
            ),
    ) {
        Text(
            label,
            style = HudTheme.LabelBright,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
