package com.paylensu.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import com.paylensu.PayLensApp
import com.paylensu.R
import com.paylensu.camera.CameraController
import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.data.RateSync
import com.paylensu.finance.MixedPayment
import com.paylensu.finance.PriceMath
import com.paylensu.model.DetectedPrice
import com.paylensu.model.TxEntry
import com.paylensu.model.TxKind
import com.paylensu.model.WalletState
import com.paylensu.ocr.OcrClient
import com.paylensu.ocr.OcrFactoryImpl
import com.paylensu.ui.ScannerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Burbuja flotante (chat head) con escáner, ResultCard y teclado sobre otras
 * apps. Servicio foreground especialUse + TYPE_APPLICATION_OVERLAY. Apps con
 * FLAG_SECURE (bancos) bloquean la vista detrás, pero la calculadora manual
 * y el carrito siguen operativos.
 */
class BubbleService : Service(), BubbleActions {

    companion object {
        private const val CHANNEL_ID = "paylensu_bubble"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            if (!OverlayPermission.canDraw(context)) {
                OverlayPermission.openSettings(context)
                return
            }
            ContextCompat.startForegroundService(
                context, Intent(context, BubbleService::class.java)
            )
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, BubbleService::class.java))
    }

    // Estado interno equivalente al ScannerUiState pero mínimo para el panel.
    data class BubbleState(
        val expanded: Boolean = false,
        val cameraOn: Boolean = false,
        val scanning: Boolean = false,
        val prices: List<DetectedPrice> = emptyList(),
        val selected: Int = 0,
        val selectedBillCents: Long? = null,
        val keypadOpen: Boolean = false,
        val keypadValue: String = "",
        val message: String? = null,
        val rateText: String = "—",
        val wallet: WalletState = WalletState(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(BubbleState())
    val state = _state.asStateFlow()

    private val camera = CameraController()
    private var ocr: OcrClient? = null
    private var latestRate: ExchangeRate? = null
    private var latestIva: Long = 16L
    private var previewRef: PreviewView? = null
    private var pendingUndo: List<com.paylensu.model.CartEntry>? = null

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var owner: BubbleLifecycleOwner

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        owner = BubbleLifecycleOwner().apply { create(); start(); resume() }
        ocr = OcrClient(OcrFactoryImpl.create(this).create())
        installOverlay()
        scope.launch { syncRateIfNeeded() }
        scope.launch {
            PayLensApp.Graph.rateStore.state.collect { rs ->
                latestRate = rs.rate?.copy(thresholdUsdCents = rs.thresholdUsdCents)
                latestIva = rs.ivaPercent
                _state.update { it.copy(rateText = rs.rate?.format() ?: "—") }
            }
        }
        scope.launch {
            PayLensApp.Graph.wallet.state.collect { ws ->
                _state.update { it.copy(wallet = ws) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        runCatching { windowManager?.removeView(composeView) }
        composeView?.disposeComposition()
        owner.pause(); owner.stop(); owner.destroy()
        scope.cancel()
        ocr?.close()
        camera.stop()
        super.onDestroy()
    }

    // ---------- Overlay ----------

    private fun installOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { BubbleHud(service = this@BubbleService) }
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // teclado propio, sin IME
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = 240
        }
        windowManager?.addView(composeView, params)
    }

    private fun reposition(dx: Float, dy: Float) {
        val p = params ?: return
        p.x += dx.toInt(); p.y += dy.toInt()
        runCatching { windowManager?.updateViewLayout(composeView, p) }
    }

    private fun snapToEdge() {
        val p = params ?: return
        val dm = resources.displayMetrics
        // Aproximación ligera: ancla al borde más cercano en X.
        val leftDist = p.x
        val rightDist = dm.widthPixels - p.x
        p.x = if (leftDist < rightDist) 0 else dm.widthPixels - 220
        runCatching { windowManager?.updateViewLayout(composeView, p) }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Burbuja PayLens", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash_glyph)
            .setContentTitle("PayLens U activo")
            .setContentText("Toca la burbuja para calcular precios sin salir de tu app.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // ---------- Acciones desde la UI de la burbuja ----------

    override fun onToggleExpand() {
        val expanding = !_state.value.expanded
        _state.update { it.copy(expanded = expanding) }
        if (expanding) snapToEdge()
    }

    override fun onDrag(dx: Float, dy: Float) = mainHandler.post { reposition(dx, dy) }
    override fun onDragEnd() = mainHandler.post { snapToEdge() }

    override fun onToggleCamera() {
        val turningOn = !_state.value.cameraOn
        _state.update { it.copy(cameraOn = turningOn, message = null) }
        if (!turningOn) {
            camera.stop()
            previewRef = null
        }
    }

    fun onPreviewReady(pv: PreviewView?) {
        previewRef = pv
        if (pv != null && _state.value.cameraOn) bindCamera(pv)
    }

    private fun bindCamera(pv: PreviewView) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _state.update { it.copy(message = "Concede el permiso de cámara en la app principal") }
            return
        }
        scope.launch {
            runCatching { camera.start(this@BubbleService, owner, pv.surfaceProvider) }
                .onFailure { _state.update { s -> s.copy(message = "Cámara: ${it.message?.take(50)}") } }
        }
    }

    override fun onScan() {
        val s = _state.value
        if (s.scanning) return
        val rs = latestRate ?: run {
            _state.update { it.copy(message = "Sin tasa BCV guardada") }
            return
        }
        scope.launch {
            _state.update { it.copy(scanning = true, message = null) }
            val bmp = camera.scanBurst(ScannerModel.FRAMES_PER_SCAN)
            if (bmp == null) {
                _state.update { it.copy(scanning = false, message = "Cámara no lista") }
                return@launch
            }
            val prices = try {
                withContext(Dispatchers.Default) { ocr?.scanPrices(bmp, rs) ?: emptyList() }
            } catch (e: Exception) {
                emptyList<DetectedPrice>()
            } finally {
                bmp.recycle()
            }
            _state.update {
                it.copy(
                    scanning = false,
                    prices = prices,
                    selected = 0,
                    selectedBillCents = null,
                    message = if (prices.isEmpty()) "Sin precios legibles — usa el teclado" else null,
                )
            }
        }
    }

    override fun onClose() = stopSelf()

    override fun onSelectPrice(index: Int) = _state.update { it.copy(selected = index, selectedBillCents = null) }

    /** Cobra el precio seleccionado (misma fórmula que la app principal). */
    override fun onCharge() {
        val s = _state.value
        val rs = latestRate ?: run {
            _state.update { it.copy(message = "Sin tasa BCV guardada") }
            return
        }
        val p = s.prices.getOrNull(s.selected) ?: return
        val total = PriceMath.withIva(
            if (p.side == com.paylensu.model.CurrencySide.USD) p.money else rs.toUsd(p.money.cents),
            latestIva,
        )
        val opt = MixedPayment.resolve(total.cents, rs.rateCents, s.selectedBillCents) ?: return
        scope.launch {
            val taxes = PriceMath.taxesToCollect(total, latestIva, opt.cashCents)
            val netCash = (opt.cashCents - opt.changeUsd.cents).coerceAtLeast(0L)
            PayLensApp.Graph.tx.add(
                TxEntry(
                    id = 0L,
                    kind = TxKind.MIXED,
                    totalUsd = total,
                    cashUsd = Money(opt.cashCents),
                    mobileBs = opt.bsDue,
                    changeUsd = opt.changeUsd,
                    taxesUsd = taxes,
                    createdAt = System.currentTimeMillis(),
                )
            )
            PayLensApp.Graph.wallet.applyDelta(cashUsdCents = netCash, mobileBsCents = opt.bsDue)
            _state.update { it.copy(message = "Cobro registrado ✓") }
        }
    }
    override fun onSelectBill(cents: Long?) = _state.update { it.copy(selectedBillCents = cents) }
    override fun onAddCart() {
        val s = _state.value
        val p = s.prices.getOrNull(s.selected) ?: return
        scope.launch {
            PayLensApp.Graph.cart.add(p.text.take(128), p.money, p.side)
            _state.update { it.copy(message = "Añadido al carrito ✓") }
        }
    }
    override fun onOpenKeypad() {
        val s = _state.value
        val p = s.prices.getOrNull(s.selected)
        _state.update {
            it.copy(
                keypadOpen = true,
                keypadValue = p?.money?.let { m -> centsToPlain(m.cents) } ?: "",
            )
        }
    }
    override fun onKeypadChange(value: String) = _state.update { it.copy(keypadValue = value) }
    override fun onKeypadDone(money: Money?) {
        _state.update { st ->
            val base = st.prices.getOrNull(st.selected)
            val prices = if (money != null && money.cents > 0 && base != null) {
                st.prices.mapIndexed { i, p ->
                    if (i == st.selected) p.copy(money = money, confidence = 100, text = money.format("$")) else p
                }
            } else st.prices
            st.copy(keypadOpen = false, prices = prices, selectedBillCents = null)
        }
    }
    override fun onKeypadClose() = _state.update { it.copy(keypadOpen = false) }

    fun clearCart() = scope.launch {
        pendingUndo = PayLensApp.Graph.cart.clear()
        _state.update { it.copy(message = "Carrito vaciado") }
    }
    fun undoClear() {
        val snap = pendingUndo ?: return
        pendingUndo = null
        scope.launch { PayLensApp.Graph.cart.restore(snap) }
    }

    private suspend fun syncRateIfNeeded() {
        if (!PayLensApp.Graph.rateStore.isRateFresh()) {
            val d = withContext(Dispatchers.IO) { RateSync.fetchRate() }
            if (d != null) {
                PayLensApp.Graph.rateStore.saveRate(Math.round(d * 100), ExchangeRate.Source.AUTO)
            }
        }
    }

    private fun centsToPlain(cents: Long): String {
        val neg = cents < 0
        val n = if (neg) -cents else cents
        return (if (neg) "-" else "") + "${n / 100}.${(n % 100).toString().padStart(2, '0')}"
    }
}
