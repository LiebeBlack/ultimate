package com.paylensu.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paylensu.core.ExchangeRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.paylensuDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(
    name = "paylensu"
)

/** Snapshot de configuración para la UI. */
data class RateState(
    val rate: ExchangeRate?,
    val ivaPercent: Long,
    val thresholdUsdCents: Long,
    val ocrEngineError: String?,
)

/**
 * DataStore Preferences: tasa BCV, fuente, fecha, IVA, umbral heurístico y
 * estado del motor OCR. Cero JSON, cero schema.
 */
class RateStore(private val context: Context) {

    private object K {
        val RATE_CENTS = longPreferencesKey("rate_bcv_cents")
        val RATE_SOURCE = stringPreferencesKey("rate_source")
        val RATE_UPDATED = longPreferencesKey("rate_updated_at")
        val IVA = intPreferencesKey("iva_percent")
        val THRESHOLD = intPreferencesKey("threshold_usd")
        val OCR_ERROR = stringPreferencesKey("ocr_engine_error")
    }

    val state: Flow<RateState> = context.paylensuDataStore.data.map { p ->
        RateState(
            rate = p[K.RATE_CENTS]?.let { cents ->
                ExchangeRate(
                    rateCents = cents,
                    source = enumValueOf(p[K.RATE_SOURCE] ?: "AUTO"),
                    updatedAtMillis = p[K.RATE_UPDATED] ?: 0L,
                )
            },
            ivaPercent = (p[K.IVA] ?: 16).toLong(),
            thresholdUsdCents = (p[K.THRESHOLD] ?: 20_00).toLong(),
            ocrEngineError = p[K.OCR_ERROR],
        )
    }

    suspend fun current(): RateState = state.first()

    /** Guarda la tasa descargada (AUTO) o tecleada (MANUAL). */
    suspend fun saveRate(rateCents: Long, source: ExchangeRate.Source) {
        if (rateCents <= 0) return
        context.paylensuDataStore.edit { p ->
            p[K.RATE_CENTS] = rateCents
            p[K.RATE_SOURCE] = source.name
            p[K.RATE_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun saveIva(percent: Long) = context.paylensuDataStore.edit { it[K.IVA] = percent.toInt() }

    suspend fun saveThresholdUsd(cents: Long) =
        context.paylensuDataStore.edit { it[K.THRESHOLD] = cents.toInt() }

    suspend fun setOcrError(message: String?) = context.paylensuDataStore.edit { p ->
        if (message == null) p.remove(K.OCR_ERROR) else p[K.OCR_ERROR] = message.take(120)
    }

    suspend fun isRateFresh(maxAgeMs: Long = 6 * 60 * 60 * 1000L): Boolean {
        val p = context.paylensuDataStore.data.first()
        val updated = p[K.RATE_UPDATED] ?: 0L
        return updated > 0 && System.currentTimeMillis() - updated < maxAgeMs
    }
}
