package com.paylensu.ui

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paylensu.PayLensApp
import com.paylensu.camera.CameraController
import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.data.RateSync
import com.paylensu.data.CartRepository
import com.paylensu.model.CartEntry
import com.paylensu.model.CurrencySide
import com.paylensu.model.DetectedPrice
import com.paylensu.ocr.OcrClient
import com.paylensu.ocr.OcrFactoryImpl
import com.paylensu.finance.MixedPayment
import com.paylensu.finance.PriceMath
import com.paylensu.model.PaymentOption
import com.paylensu.model.TxEntry
import com.paylensu.model.TxKind
import com.paylensu.model.WalletState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado completo del visor. Recomposición solo de lo que cambia. */
data class ScannerUiState(
    val rate: ExchangeRate? = null,
    val ivaPercent: Long = 16,
    val scanning: Boolean = false,
    val pulseTick: Int = 0,
    val prices: List<DetectedPrice> = emptyList(),
    val selected: Int = 0,
    val boxes: List<Rect> = emptyList(),   // fracciones (0..1) del área de preview
    val bitmapAspect: Float = 0f,          // ancho/alto del frame analizado (mapeo FILL_CENTER)
    val wallet: WalletState = WalletState(), // saldo: efectivo $ + Pago Móvil Bs
    val keypadOpen: Boolean = false,
    val keypadValue: String = "",
    val selectedBillCents: Long? = null,
    val message: String? = null,           // aviso transitorio (errores, añadido)
    val sheetOpen: Boolean = false,
    val walletSheetOpen: Boolean = false,
)

/**
 * Orquestador del visor: tasa, carrito, cámara y OCR. Sin OCR continuo:
 * todo sucede SOLO dentro de [tapScan] (ráfaga 3 frames -> mejor frame ->
 * OCR con presupuesto de tiempo).
 */
class ScannerModel(app: Application) : AndroidViewModel(app) {

    private val graph = PayLensApp.Graph
    private val camera = CameraController()
    private val ocr by lazy { OcrClient(OcrFactoryImpl.create(app).create()) }

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private var pendingUndo: List<CartEntry>? = null

    init {
        viewModelScope.launch {
            graph.rateStore.state.collect { rs ->
                _state.update {
                    it.copy(
                        rate = rs.rate?.copy(thresholdUsdCents = rs.thresholdUsdCents),
                        ivaPercent = rs.ivaPercent,
                    )
                }
            }
        }
        viewModelScope.launch { syncRateIfNeeded() }
        viewModelScope.launch {
            graph.wallet.state.collect { ws ->
                _state.update { it.copy(wallet = ws) }
            }
        }
    }

    val cameraController: CameraController get() = camera

    private suspend fun syncRateIfNeeded() {
        if (!graph.rateStore.isRateFresh()) {
            val d = withContext(Dispatchers.IO) { RateSync.fetchRate() }
            if (d != null) {
                graph.rateStore.saveRate(Math.round(d * 100), ExchangeRate.Source.AUTO)
            }
        }
    }

    /** Tap en el visor: ráfaga -> OCR -> parser. Event-driven puro. */
    fun tapScan() {
        val s = _state.value
        if (s.scanning) return
        val rate = s.rate
        if (rate == null) {
            _state.update { it.copy(message = "Configura la tasa BCV en Ajustes") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, pulseTick = it.pulseTick + 1, message = null) }
            val bmp = camera.scanBurst(FRAMES_PER_SCAN)
            if (bmp == null) {
                _state.update { it.copy(scanning = false, message = "Cámara no lista") }
                return@launch
            }
            val prices: List<DetectedPrice>
            val boxes: List<Rect>
            var aspect = 0f
            try {
                val (p, ocrOut) = ocr.scanWithBlocks(bmp, rate)
                aspect = bmp.width / bmp.height.toFloat()
                boxes = ocrOut?.blocks?.mapNotNull { b ->
                    if (b.bounds.isEmpty) null
                    else Rect(
                        // bmp viene rotado: width = aspect * height.
                        (b.bounds.left / (aspect * bmp.height)).coerceIn(0f, 1f),
                        (b.bounds.top / bmp.height.toFloat()).coerceIn(0f, 1f),
                        (b.bounds.right / (aspect * bmp.height)).coerceIn(0f, 1f),
                        (b.bounds.bottom / bmp.height.toFloat()).coerceIn(0f, 1f),
                    )
                } ?: emptyList()
                prices = p
            } catch (e: Exception) {
                graph.rateStore.setOcrError(e.message ?: "error OCR")
                _state.update { it.copy(scanning = false, message = "OCR: ${e.message?.take(60)}") }
                bmp.recycle()
                return@launch
            }
            bmp.recycle() // liberar YA: presupuesto de RAM
            _state.update {
                it.copy(
                    scanning = false,
                    prices = prices,
                    boxes = boxes,
                    bitmapAspect = aspect,
                    selected = 0,
                    selectedBillCents = null,
                    // Sin resultados: el teclado entra solo (corrección sin nueva foto).
                    keypadOpen = prices.isEmpty(),
                    message = if (prices.isEmpty()) "Sin precios legibles — corrige a mano" else null,
                )
            }
        }
    }

    fun selectPrice(index: Int) =
        _state.update { it.copy(selected = index, selectedBillCents = null) }

    fun selectBill(cents: Long?) =
        _state.update { it.copy(selectedBillCents = cents) }

    fun addToCart() {
        val s = _state.value
        val p = s.prices.getOrNull(s.selected) ?: return
        viewModelScope.launch {
            graph.cart.add(p.text.take(128), p.money, p.side)
            _state.update { it.copy(message = "Añadido al carrito ✓") }
        }
    }

    fun openSheet() = _state.update { it.copy(sheetOpen = true) }
    fun closeSheet() = _state.update { it.copy(sheetOpen = false) }

    fun openWalletSheet() = _state.update { it.copy(walletSheetOpen = true) }
    fun closeWalletSheet() = _state.update { it.copy(walletSheetOpen = false) }

    fun removeEntry(id: Long) = viewModelScope.launch { graph.cart.remove(id) }

    fun clearCart() = viewModelScope.launch {
        pendingUndo = graph.cart.clear()
        _state.update { it.copy(message = "Carrito vaciado") }
    }

    fun undoClear() {
        val snap = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch { graph.cart.restore(snap) }
    }

    // ---- Cobro, transacciones y cuenta ----

    /**
     * Cobra el precio seleccionado con la fila elegida de la matrix (o la
     * óptima): asienta la transacción, descuenta/suma el saldo de la cuenta y
     * muestra la confirmación.
     */
    fun chargePayment() {
        val s = _state.value
        val rate = s.rate ?: run {
            _state.update { it.copy(message = "Configura la tasa BCV en Ajustes") }
            return
        }
        val p = s.prices.getOrNull(s.selected) ?: return
        val total = PriceMath.withIva(
            if (p.side == CurrencySide.USD) p.money else rate.toUsd(p.money.cents),
            s.ivaPercent,
        )
        val opt = MixedPayment.resolve(total.cents, rate.rateCents, s.selectedBillCents) ?: return
        viewModelScope.launch {
            recordCharge(opt, total, s.ivaPercent, TxKind.MIXED)
            _state.update { it.copy(message = "Cobro registrado ✓") }
        }
    }

    /** Cobra TODO el carrito (fila óptima) y lo vacía. */
    fun chargeCart() {
        val s = _state.value
        val rate = s.rate ?: return
        viewModelScope.launch {
            val entries = graph.cart.entries.value
            if (entries.isEmpty()) {
                _state.update { it.copy(message = "Carrito vacío") }
                return@launch
            }
            val total = CartRepository.totals(entries, rate).first
            val opt = MixedPayment.resolve(total.cents, rate.rateCents, null) ?: return@launch
            recordCharge(opt, total, s.ivaPercent, TxKind.CART)
            graph.cart.clear()
            _state.update { it.copy(sheetOpen = false, message = "Cobro registrado ✓") }
        }
    }

    /** Asienta la transacción y aplica el saldo (efectivo neto + Pago Móvil). */
    private suspend fun recordCharge(
        opt: PaymentOption,
        total: Money,
        ivaPercent: Long,
        kind: TxKind,
    ) {
        val taxes = PriceMath.taxesToCollect(total, ivaPercent, opt.cashCents)
        val netCash = (opt.cashCents - opt.changeUsd.cents).coerceAtLeast(0L)
        graph.tx.add(
            TxEntry(
                id = 0L,
                kind = kind,
                totalUsd = total,
                cashUsd = Money(opt.cashCents),
                mobileBs = opt.bsDue,
                changeUsd = opt.changeUsd,
                taxesUsd = taxes,
                createdAt = System.currentTimeMillis(),
            )
        )
        graph.wallet.applyDelta(cashUsdCents = netCash, mobileBsCents = opt.bsDue)
    }

    /** Fija el saldo real contado (desde WalletSheet). */
    fun setWallet(cashUsdCents: Long, mobileBsCents: Long) =
        viewModelScope.launch { graph.wallet.set(cashUsdCents, mobileBsCents) }

    fun clearHistory() = viewModelScope.launch { graph.tx.clear() }

    fun openKeypad() {
        val s = _state.value
        val p = s.prices.getOrNull(s.selected)
        _state.update {
            it.copy(
                keypadOpen = true,
                keypadValue = p?.money?.let { m -> centsToPlain(m.cents) } ?: "",
            )
        }
    }

    fun keypadChange(v: String) = _state.update { it.copy(keypadValue = v) }

    fun keypadDone(money: Money?) {
        _state.update { st ->
            val base = st.prices.getOrNull(st.selected)
            val newPrices = if (money != null && money.cents > 0 && base != null) {
                st.prices.mapIndexed { i, p ->
                    if (i == st.selected) p.copy(money = money, confidence = 100, text = plainToLabel(money)) else p
                }
            } else st.prices
            st.copy(keypadOpen = false, prices = newPrices, selectedBillCents = null)
        }
    }

    fun closeKeypad() = _state.update { it.copy(keypadOpen = false) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun centsToPlain(cents: Long): String {
        val neg = cents < 0
        val n = if (neg) -cents else cents
        return (if (neg) "-" else "") + "${n / 100}.${(n % 100).toString().padStart(2, '0')}"
    }

    private fun plainToLabel(m: Money): String = m.format("$")

    companion object {
        const val FRAMES_PER_SCAN = 3
    }
}
