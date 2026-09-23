package com.paylensu.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.paylensu.model.TxEntry
import com.paylensu.model.TxKind

/**
 * SQLite directo para el historial de transacciones cobradas (misma técnica
 * que CartDb: sin Room, cero generación de código).
 */
class TxDb(context: Context) {

    private val helper: SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(DB_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE tx(" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "kind INTEGER NOT NULL," +
                                "total_usd INTEGER NOT NULL," +
                                "cash_usd INTEGER NOT NULL," +
                                "mobile_bs INTEGER NOT NULL," +
                                "change_usd INTEGER NOT NULL," +
                                "taxes_usd INTEGER NOT NULL," +
                                "created_at INTEGER NOT NULL)"
                        )
                        db.execSQL("CREATE INDEX idx_tx_created ON tx(created_at)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // v1: nada que migrar.
                    }
                })
                .build()
        )

    fun insert(e: TxEntry): Long {
        // SIN .use: cerrar writableDatabase rompería el helper (reusa la conexión).
        val db = helper.writableDatabase
        return db.compileStatement(INSERT).apply {
            bindLong(1, e.kind.ordinal.toLong())
            bindLong(2, e.totalUsd.cents)
            bindLong(3, e.cashUsd.cents)
            bindLong(4, e.mobileBs)
            bindLong(5, e.changeUsd.cents)
            bindLong(6, e.taxesUsd.cents)
            bindLong(7, e.createdAt)
        }.executeInsert()
    }

    fun all(): List<TxEntry> {
        val out = ArrayList<TxEntry>(32)
        helper.readableDatabase.query(
            "SELECT id, kind, total_usd, cash_usd, mobile_bs, change_usd, taxes_usd, created_at " +
                "FROM tx ORDER BY id DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out += TxEntry(
                    id = c.getLong(0),
                    kind = TxKind.entries[c.getInt(1).coerceIn(0, TxKind.entries.size - 1)],
                    totalUsd = com.paylensu.core.Money(c.getLong(2)),
                    cashUsd = com.paylensu.core.Money(c.getLong(3)),
                    mobileBs = c.getLong(4),
                    changeUsd = com.paylensu.core.Money(c.getLong(5)),
                    taxesUsd = com.paylensu.core.Money(c.getLong(6)),
                    createdAt = c.getLong(7),
                )
            }
        }
        return out
    }

    fun clear() {
        helper.writableDatabase.execSQL("DELETE FROM tx")
    }

    fun close() = helper.close()

    private companion object {
        const val DB_NAME = "paylensu_tx.db"
        const val DB_VERSION = 1
        const val INSERT =
            "INSERT INTO tx(kind, total_usd, cash_usd, mobile_bs, change_usd, taxes_usd, created_at) " +
                "VALUES(?,?,?,?,?,?,?)"
    }
}
