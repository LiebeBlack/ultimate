package com.paylensu.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.model.TxEntry
import com.paylensu.model.WalletState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sheet de la CUENTA: cuánto hay en efectivo $ y en Pago Móvil (Bs), con
 * equivalencias cruzadas a la tasa, fijado manual del saldo contado y el
 * historial de movimientos cobrados con sus impuestos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSheet(
    wallet: WalletState,
    rate: ExchangeRate?,
    history: List<TxEntry>,
    onSetWallet: (cashUsdCents: Long, mobileBsCents: Long) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = HudTheme.Black,
        scrimColor = HudTheme.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CUENTA", style = HudTheme.LabelBright)
                if (history.isNotEmpty()) {
                    // Destructivo sin undo: confirmación a doble toque (1er tap
                    // arma el estado, 2do borra; timeout de 3 s desarma).
                    var confirmClear by remember { mutableStateOf(false) }
                    LaunchedEffect(confirmClear) {
                        if (confirmClear) {
                            kotlinx.coroutines.delay(3000)
                            confirmClear = false
                        }
                    }
                    Surface(
                        onClick = {
                            if (confirmClear) {
                                confirmClear = false
                                onClearHistory()
                            } else {
                                confirmClear = true
                            }
                        },
                        color = HudTheme.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.neonFrame(
                            if (confirmClear) HudTheme.Warn else HudTheme.Purple,
                            corner = 8.dp,
                            halo = false,
                        ),
                    ) {
                        Text(
                            if (confirmClear) "¿SEGURO? TOCA DE NUEVO" else "VACIAR HISTORIAL",
                            style = HudTheme.LabelBright.copy(
                                color = if (confirmClear) HudTheme.Warn else HudTheme.Purple,
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // ---- Saldos con equivalencia cruzada ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BalanceCard(
                    title = "EFECTIVO",
                    main = Money(wallet.cashUsdCents).format("$"),
                    sub = rate?.let { Money(Money.toBs(wallet.cashUsdCents, it.rateCents)).format("Bs. ") } ?: "—",
                    accent = HudTheme.Cyan,
                    modifier = Modifier.weight(1f),
                )
                BalanceCard(
                    title = "PAGO MÓVIL",
                    main = Money(wallet.mobileBsCents).format("Bs. "),
                    sub = rate?.let { Money.fromBs(wallet.mobileBsCents, it.rateCents).format("$") } ?: "—",
                    accent = HudTheme.Purple,
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- Fijar saldo contado ----
            var cashText by remember { mutableStateOf("") }
            var mobileText by remember { mutableStateOf("") }
            Text(
                "FIJAR SALDO CONTADO",
                style = HudTheme.LabelBright,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text("Efectivo $", style = HudTheme.Label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HudTheme.Cyan,
                        unfocusedBorderColor = HudTheme.TextSecondary.copy(alpha = 0.4f),
                        cursorColor = HudTheme.Cyan,
                        focusedTextColor = HudTheme.TextPrimary,
                        unfocusedTextColor = HudTheme.TextPrimary,
                    ),
                )
                OutlinedTextField(
                    value = mobileText,
                    onValueChange = { mobileText = it },
                    label = { Text("Pago Móvil Bs", style = HudTheme.Label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HudTheme.Purple,
                        unfocusedBorderColor = HudTheme.TextSecondary.copy(alpha = 0.4f),
                        cursorColor = HudTheme.Purple,
                        focusedTextColor = HudTheme.TextPrimary,
                        unfocusedTextColor = HudTheme.TextPrimary,
                    ),
                )
            }
            Surface(
                onClick = {
                    // "20" significa $20.00 / Bs 20.00 (no centavos).
                    val cash = parseDecimal(cashText)
                    val mobile = parseDecimal(mobileText)
                    if (cash != null || mobile != null) {
                        onSetWallet(cash ?: wallet.cashUsdCents, mobile ?: wallet.mobileBsCents)
                        cashText = ""
                        mobileText = ""
                    }
                },
                color = HudTheme.Cyan,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    "GUARDAR SALDO",
                    style = HudTheme.Button,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).neonDivider())

            // ---- Historial de movimientos ----
            Text("MOVIMIENTOS (${history.size})", style = HudTheme.LabelBright)
            if (history.isEmpty()) {
                Text(
                    "Sin movimientos aún — cobra un precio o el carrito",
                    style = HudTheme.Label,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    items(history, key = { it.id }) { tx ->
                        Row(
                            // Borrado/reorden con movimiento, igual que el carrito.
                            Modifier.animateItem().fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(kindLabel(tx), style = HudTheme.LabelBright)
                                Text(dateLabel(tx.createdAt), style = HudTheme.Label)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(tx.totalUsd.format("$"), style = HudTheme.PriceMid)
                                Text(
                                    "imp. ${tx.taxesUsd.format("$")}",
                                    style = HudTheme.Label.copy(color = HudTheme.Warn),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta de saldo: título + monto principal + equivalencia cruzada. */
@Composable
private fun BalanceCard(
    title: String,
    main: String,
    sub: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = HudTheme.Black,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.neonFrame(accent, corner = 10.dp, halo = false),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = HudTheme.Label)
            Text(main, style = HudTheme.PriceMid.copy(color = accent))
            Text("≈ $sub", style = HudTheme.Label)
        }
    }
}

private fun kindLabel(tx: TxEntry): String = when (tx.kind) {
    com.paylensu.model.TxKind.CART -> "CARRITO"
    com.paylensu.model.TxKind.MIXED -> "MIXTO"
    com.paylensu.model.TxKind.CASH -> "EFECTIVO"
    com.paylensu.model.TxKind.MOBILE -> "PAGO MÓVIL"
}

private val dateFormat by lazy { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
private fun dateLabel(millis: Long): String = dateFormat.format(Date(millis))

/** "20" -> 2000 ($20.00). "12,5" -> 1250. null si no es decimal válido. */
private fun parseDecimal(text: String): Long? =
    text.trim().replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) }
