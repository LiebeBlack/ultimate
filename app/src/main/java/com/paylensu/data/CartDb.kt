package com.paylensu.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.paylensu.model.CartEntry
import com.paylensu.core.Money
import com.paylensu.model.CurrencySide

/**
 * SQLite directo (sin Room): cero generación de código, cero dependencias de
 * anotación. Tabla plana para el carrito acumulativo.
 */
class CartDb(context: Context) {

    private val helper: SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(DB_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE cart(" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "label TEXT NOT NULL," +
                                "cents INTEGER NOT NULL," +
                                "side INTEGER NOT NULL," +
                                "created_at INTEGER NOT NULL)"
                        )
                        db.execSQL("CREATE INDEX idx_cart_created ON cart(created_at)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // v1: nada que migrar.
                    }
                })
                .build()
        )

    fun insert(label: String, cents: Long, side: CurrencySide, createdAt: Long): Long {
        // SIN .use: cerrar writableDatabase aquí rompería el helper (reusa la conexión).
        val db = helper.writableDatabase
        return db.compileStatement(INSERT).apply {
            bindString(1, label.take(128)) // garantía de entrada <= 1200 B
            bindLong(2, cents)
            bindLong(3, if (side == CurrencySide.USD) 0L else 1L)
            bindLong(4, createdAt)
        }.executeInsert()
    }

    fun delete(id: Long) {
        helper.writableDatabase.execSQL("DELETE FROM cart WHERE id = ?", arrayOf(id))
    }

    fun clear() {
        helper.writableDatabase.execSQL("DELETE FROM cart")
    }

    fun all(): List<CartEntry> {
        val out = ArrayList<CartEntry>(32)
        helper.readableDatabase.query("SELECT id, label, cents, side, created_at FROM cart ORDER BY id DESC")
            .use { c ->
                while (c.moveToNext()) {
                    out += CartEntry(
                        id = c.getLong(0),
                        label = c.getString(1),
                        amount = Money(c.getLong(2)),
                        side = if (c.getLong(3) == 0L) CurrencySide.USD else CurrencySide.VES,
                        createdAt = c.getLong(4),
                    )
                }
            }
        return out
    }

    fun close() = helper.close()

    private companion object {
        const val DB_NAME = "paylensu_cart.db"
        const val DB_VERSION = 1
        const val INSERT = "INSERT INTO cart(label, cents, side, created_at) VALUES(?,?,?,?)"
    }
}
