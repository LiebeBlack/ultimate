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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paylensu.core.Money
import com.paylensu.finance.PriceMath
import com.paylensu.model.CartEntry

/**
 * Sheet de bajo overhead del carrito: lista minimalista con key estable por id
 * (reutilización sin re-mediciones), borrar ítem individual y vaciar con undo
 * de 3 s manejado por el caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartSheet(
    entries: List<CartEntry>,
    totalUsd: Money,
    totalBs: Money,
    rateText: String,
    ivaPercent: Long,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onUndo: () -> Unit,
    onCharge: () -> Unit,
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
                Column {
                    Text("CART — ${entries.size} items", style = HudTheme.LabelBright)
                    Text(
                        "${totalUsd.format("$")}  •  ${totalBs.format("Bs. ")}  •  TASA $rateText",
                        style = HudTheme.Label,
                    )
                    Text(
                        "Impuestos a cobrar (IVA): ${PriceMath.ivaOf(totalUsd, ivaPercent).format("$")}",
                        style = HudTheme.Label.copy(color = HudTheme.Warn),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // COBRAR solo existe si hay algo que cobrar.
                    if (entries.isNotEmpty()) {
                        Surface(
                            onClick = onCharge,
                            color = HudTheme.Purple,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "COBRAR",
                                style = HudTheme.Button,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Surface(
                        onClick = onClear,
                        color = HudTheme.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.neonFrame(HudTheme.Purple, corner = 8.dp, halo = false),
                    ) {
                        Text(
                            "VACIAR",
                            style = HudTheme.LabelBright.copy(color = HudTheme.Purple),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).neonDivider())

            if (entries.isEmpty()) {
                Text(
                    "Carrito vacío",
                    style = HudTheme.Label,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            // Reordenado/borrado animado (movimiento, no saltos).
                            Modifier.animateItem().fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = HudTheme.PriceMid)
                                Text(
                                    if (entry.side == com.paylensu.model.CurrencySide.USD) "USD"
                                    else "VES",
                                    style = HudTheme.Label,
                                )
                            }
                            Text(
                                entry.amount.format(if (entry.side == com.paylensu.model.CurrencySide.USD) "$" else "Bs. "),
                                style = HudTheme.PriceMid,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Surface(
                                onClick = { onRemove(entry.id) },
                                color = HudTheme.Black,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.neonFrame(
                                    HudTheme.Cyan.copy(alpha = 0.7f),
                                    stroke = HudTheme.StrokeThin,
                                    corner = 6.dp,
                                    halo = false,
                                ),
                            ) {
                                Text(
                                    "✕",
                                    style = HudTheme.LabelBright,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            androidx.compose.material3.TextButton(
                onClick = onUndo,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Text("DESHACER ÚLTIMO CAMBIO", style = HudTheme.Label)
            }
        }
    }
}
