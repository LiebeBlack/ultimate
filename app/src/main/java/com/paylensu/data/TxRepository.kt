package com.paylensu.data

import android.content.Context
import com.paylensu.model.TxEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Historial de transacciones cobradas: StateFlow en memoria (la UI solo
 * recompone lo que cambia) + persistencia SQLite. Todas las escrituras son
 * O(1) bajo mutex; lectura de disco solo en el arranque.
 */
class TxRepository(context: Context) {

    private val db = TxDb(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<TxEntry>>(emptyList())
    val entries: StateFlow<List<TxEntry>> = _entries.asStateFlow()

    init {
        scope.launch { _entries.value = db.all() }
    }

    suspend fun add(entry: TxEntry) = mutex.withLock {
        val id = db.insert(entry.copy(createdAt = entry.createdAt.takeIf { it > 0 }
            ?: System.currentTimeMillis()))
        // Lista nueva O(n) de solo-referencias: en Android Go, sin edits en frío.
        _entries.value = buildList(_entries.value.size + 1) {
            add(entry.copy(id = id))
            addAll(_entries.value)
        }
        id
    }

    suspend fun clear() = mutex.withLock {
        db.clear()
        _entries.value = emptyList()
    }

    fun close() = db.close()
}
