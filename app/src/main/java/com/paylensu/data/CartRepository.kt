package com.paylensu.data

import android.content.Context
import com.paylensu.core.ExchangeRate
import com.paylensu.core.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.paylensu.model.CartEntry
import com.paylensu.model.CurrencySide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Repositorio del Carrito Flotante Express. Lista en memoria como fuente de
 * UI (StateFlow: recomposición mínima) + persistencia SQLite. Todas las
 * escrituras son O(1) y las lecturas no tocan disco salvo en el arranque.
 */
class CartRepository(context: Context) {

    private val db = CartDb(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<CartEntry>>(emptyList())
    val entries: StateFlow<List<CartEntry>> = _entries

    init {
        // Carga inicial fuera del hilo principal (1 GB de RAM: nada de IO en main).
        scope.launch { _entries.value = db.all() }
    }

    /** Añade en 1-tap. Devuelve el id generado. */
    suspend fun add(label: String, amount: Money, side: CurrencySide): Long =
        mutex.withLock {
            val id = db.insert(
                label = label.ifBlank { "Item" },
                cents = amount.cents,
                side = side,
                createdAt = System.currentTimeMillis(),
            )
            _entries.value = buildList(_entries.value.size + 1) {
                add(CartEntry(id, label.ifBlank { "Item" }, amount, side, System.currentTimeMillis()))
                addAll(_entries.value)
            }
            id
        }

    suspend fun remove(id: Long): CartEntry? = mutex.withLock {
        val victim = _entries.value.firstOrNull { it.id == id }
        if (victim != null) {
            db.delete(id)
            _entries.value = _entries.value.filterNot { it.id == id }
        }
        victim
    }

    suspend fun clear(): List<CartEntry> = mutex.withLock {
        val snapshot = _entries.value
        db.clear()
        _entries.value = emptyList()
        snapshot
    }

    suspend fun restore(entries: List<CartEntry>) = mutex.withLock {
        db.clear()
        entries.forEach { e ->
            db.insert(e.label, e.amount.cents, e.side, e.createdAt)
        }
        _entries.value = db.all()
    }

    fun close() = db.close()

    companion object {
        /** Totales: suma USD directa; VES se convierte con la tasa para el HUD. */
        fun totals(entries: List<CartEntry>, rate: ExchangeRate?): Pair<Money, Money> {
            var usd = 0L
            var bs = 0L
            for (e in entries) {
                if (e.side == CurrencySide.USD) usd += e.amount.cents else bs += e.amount.cents
            }
            val totalUsd = usd + (if (rate != null) Money.fromBs(bs, rate.rateCents).cents else 0L)
            val totalBs = bs + (if (rate != null) rate.toBs(Money(usd)) else 0L)
            return Money(totalUsd) to Money(totalBs)
        }
    }
}
