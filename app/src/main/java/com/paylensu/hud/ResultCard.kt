package com.paylensu.hud

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import com.paylensu.finance.MixedPayment
import com.paylensu.finance.PriceMath
import com.paylensu.model.CurrencySide
import com.paylensu.model.DetectedPrice
import com.paylensu.hud.pressScale

/**
 * Card HUD inteligente: desglose automático sin botones.
 *  - Conversión exacta según el lado detectado (USD↔VES) a la tasa BCV.
 *  - Precio con IVA incluido (y su desglose neto/IVA).
 *  - Matrix de pago mixto: billete seleccionable, Bs por Pago Móvil y vuelto.
 * Todo stroke plano OLED; nada de sombras.
 */
@Composable
fun ResultCard(
    price: DetectedPrice,
    rate: ExchangeRate,
    ivaPercent: Long,
    selectedBillCents: Long?,
    onSelectBill: (Long) -> Unit,
    onAddCart: () -> Unit,
    onEditPrice: () -> Unit,
    onCharge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalUsd: Money
    val conversionLine: String
    if (price.side == CurrencySide.USD) {
        totalUsd = price.money
        conversionLine = Money.toBs(totalUsd.cents, rate.rateCents).let { bsCents ->
            Money(bsCents).format("Bs. ")
        }
    } else {
        val usd = rate.toUsd(price.money.cents)
        totalUsd = usd
        conversionLine = usd.format("$")
    }
    val totalWithIva = PriceMath.withIva(totalUsd, ivaPercent)
    val iva = PriceMath.ivaOf(totalWithIva, ivaPercent)

    Surface(
        modifier = modifier
            .background(HudTheme.Black)
            .neonFrame(HudTheme.Cyan),
        color = HudTheme.Black,
        shape = RoundedCornerShape(14.dp),
    ) {
        // Los cambios internos (chips de billetes, línea de vuelto) cambian de
        // tamaño con ANIMACIÓN de disposición, no con saltos de layout.
        Column(Modifier.fillMaxWidth().padding(14.dp).animateContentSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(price.text, style = HudTheme.Label)
                Text("TASA ${rate.format()}", style = HudTheme.LabelBright)
            }

            Text(
                if (price.side == CurrencySide.USD) totalUsd.format("$") else price.money.format("Bs. "),
                style = HudTheme.PriceBig,
            )
            Text("≈ $conversionLine", style = HudTheme.PriceMid.copy(color = HudTheme.Purple))

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("IVA ${ivaPercent}% incl.", style = HudTheme.Label)
                Text(
                    "${totalWithIva.format("$")}  (neto ${PriceMath.netOf(totalWithIva, ivaPercent).format("$")})",
                    style = HudTheme.LabelBright,
                )
            }
            Text(
                "IVA = ${iva.format("$")}",
                style = HudTheme.Label,
            )

            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).neonDivider())

            // Matrix de pago mixto: chips de billetes + línea de vuelto.
            val matrix = MixedPayment.matrix(totalWithIva.cents, rate.rateCents)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                matrix.filter { it.billCents > 0 }.take(5).forEach { opt ->
                    val selected = selectedBillCents == opt.billCents || opt.optimal && selectedBillCents == null
                    Surface(
                        onClick = { onSelectBill(opt.billCents) },
                        color = HudTheme.Black,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.neonFrame(
                            if (selected) HudTheme.Purple else HudTheme.Cyan.copy(alpha = 0.6f),
                            stroke = HudTheme.StrokeThin,
                            corner = 6.dp,
                            halo = false,
                        ),
                    ) {
                        Text(
                            "$${opt.billCents / 100}",
                            style = if (selected) HudTheme.LabelBright.copy(color = HudTheme.Purple) else HudTheme.LabelBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            val chosen = matrix.firstOrNull { it.billCents == (selectedBillCents ?: matrix.firstOrNull { o -> o.optimal }?.billCents ?: 0L) }
            if (chosen != null) {
                Text(
                    buildString {
                        append("EFECTIVO ${Money(chosen.cashCents).format("$")}")
                        if (chosen.bsDue > 0) append("  +  PAGO MÓVIL ${Money(chosen.bsDue).format("Bs. ")}")
                        if (chosen.changeUsd.cents > 0) append("  →  VUELTO ${chosen.changeUsd.format("$")}")
                        if (chosen.changeBs > 0) append(" (${Money(chosen.changeBs).format("Bs. ")})")
                    },
                    style = HudTheme.Label,
                    modifier = Modifier.padding(top = 6.dp),
                )
                // Impuestos a cobrar de ESTA fila de pago: IVA siempre; IGTF
                // solo si hay parte en efectivo $ (misma fórmula que el cobro).
                Text(
                    buildString {
                        val taxes = PriceMath.taxesToCollect(totalWithIva, ivaPercent, chosen.cashCents)
                        append("IMPUESTOS A COBRAR ${taxes.format("$")}")
                        append(if (chosen.cashCents > 0) "  (IVA + IGTF)" else "  (IVA)")
                    },
                    style = HudTheme.Label.copy(color = HudTheme.Warn),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val addInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onAddCart,
                    interactionSource = addInteraction,
                    color = HudTheme.Cyan,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).pressScale(addInteraction, 0.94f),
                ) {
                    Text(
                        "[ + CARRITO ]",
                        style = HudTheme.Button,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
                val editInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onEditPrice,
                    interactionSource = editInteraction,
                    color = HudTheme.Black,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(editInteraction, 0.94f)
                        .neonFrame(HudTheme.Purple, corner = 8.dp, halo = false),
                ) {
                    Text(
                        "EDITAR",
                        style = HudTheme.LabelBright.copy(color = HudTheme.Purple),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
                val chargeInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onCharge,
                    interactionSource = chargeInteraction,
                    color = HudTheme.Purple,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).pressScale(chargeInteraction, 0.94f),
                ) {
                    Text(
                        "COBRAR",
                        style = HudTheme.Button,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}
