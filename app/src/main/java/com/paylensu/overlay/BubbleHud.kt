package com.paylensu.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paylensu.PayLensApp
import com.paylensu.data.CartRepository

/**
 * Glue Compose de la burbuja: recolecta los 3 StateFlows (servicio, tasa,
 * carrito) y delega el dibujo a [BubbleRoot]. El servicio implementa
 * [BubbleActions] directamente.
 */
@Composable
fun BubbleHud(service: BubbleService) {
    val bubble by service.state.collectAsStateWithLifecycle()
    val rateState by PayLensApp.Graph.rateStore.state.collectAsStateWithLifecycle(
        initialValue = com.paylensu.data.RateState(null, 16, 2_000, null)
    )
    val cartEntries by PayLensApp.Graph.cart.entries.collectAsStateWithLifecycle()
    val totals = CartRepository.totals(cartEntries, rateState.rate)

    BubbleRoot(
        expanded = bubble.expanded,
        cameraOn = bubble.cameraOn,
        scanning = bubble.scanning,
        rateText = bubble.rateText,
        itemCount = cartEntries.size,
        totalUsd = totals.first,
        totalBs = totals.second,
        priceChips = bubble.prices.map { it.text },
        selectedIndex = bubble.selected,
        selectedPrice = bubble.prices.getOrNull(bubble.selected),
        rate = rateState.rate,
        ivaPercent = rateState.ivaPercent,
        selectedBillCents = bubble.selectedBillCents,
        keypadOpen = bubble.keypadOpen,
        keypadValue = bubble.keypadValue,
        message = bubble.message,
        wallet = bubble.wallet,
        actions = service,
        onPreviewReady = service::onPreviewReady,
    )
}
