package com.paylensu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.paylensu.model.WalletState

private val Context.walletDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    by androidx.datastore.preferences.preferencesDataStore(name = "paylensu_wallet")

/**
 * Saldo de la cuenta (cuánto hay en efectivo $ y en Pago Móvil Bs) en
 * DataStore: dos claves Long, cero JSON. El botón "—" permite resetear y
 * el campo editable de WalletSheet fija el saldo real contado.
 */
class WalletStore(private val context: Context) {

    private object K {
        val CASH = longPreferencesKey("wallet_cash_usd_cents")
        val MOBILE = longPreferencesKey("wallet_mobile_bs_cents")
    }

    val state: Flow<WalletState> = context.walletDataStore.data.map { p ->
        WalletState(
            cashUsdCents = p[K.CASH] ?: 0L,
            mobileBsCents = p[K.MOBILE] ?: 0L,
        )
    }

    suspend fun current(): WalletState = state.first()

    /** Suma (o resta con negativos, con piso en 0) al saldo. */
    suspend fun applyDelta(cashUsdCents: Long, mobileBsCents: Long) {
        context.walletDataStore.edit { p ->
            val c = p[K.CASH] ?: 0L
            val m = p[K.MOBILE] ?: 0L
            p[K.CASH] = (c + cashUsdCents).coerceAtLeast(0L)
            p[K.MOBILE] = (m + mobileBsCents).coerceAtLeast(0L)
        }
    }

    /** Fija el saldo exacto (conteo físico). */
    suspend fun set(cashUsdCents: Long, mobileBsCents: Long) {
        context.walletDataStore.edit { p ->
            p[K.CASH] = cashUsdCents.coerceAtLeast(0L)
            p[K.MOBILE] = mobileBsCents.coerceAtLeast(0L)
        }
    }

    suspend fun reset() = set(0L, 0L)
}
