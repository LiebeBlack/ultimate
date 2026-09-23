package com.paylensu

import android.app.Application
import android.content.Context
import com.paylensu.data.CartRepository
import com.paylensu.data.RateStore
import com.paylensu.data.TxRepository
import com.paylensu.data.WalletStore

/**
 * Application + contenedor DI manual (sin Hilt: menos clases generadas,
 * menos RAM en Android Go). Compartido por la actividad y la burbuja.
 */
class PayLensApp : Application() {

    object Graph {
        lateinit var rateStore: RateStore
            private set
        lateinit var cart: CartRepository
            private set
        lateinit var tx: TxRepository
            private set
        lateinit var wallet: WalletStore
            private set

        fun init(context: Context) {
            if (::rateStore.isInitialized) return
            val app = context.applicationContext
            rateStore = RateStore(app)
            cart = CartRepository(app)
            tx = TxRepository(app)
            wallet = WalletStore(app)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
