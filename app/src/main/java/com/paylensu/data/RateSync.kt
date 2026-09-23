package com.paylensu.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Auto-sync de la tasa BCV con red mínima: HttpURLConnection (0 dependencias),
 * timeout 3 s, 2 fuentes en cascada. Solo se invoca al abrir la app; si falla,
 * se conserva la última tasa guardada y el HUD muestra el badge offline.
 */
object RateSync {

    private const val TIMEOUT_MS = 3_000
    private const val DOLARAPI = "https://dolapi.dolarapi.com/v1/dolares/oficial"
    private const val PYDOLARVE = "https://pydolarve.org/api/v1/dollar?page=bcv"

    /** Devuelve la tasa oficial (Bs por USD) o null si falla todo. */
    suspend fun fetchRate(): Double? {
        return fetch(DOLARAPI) { json ->
            // {"compra":0.0,"venta":36.88,...}
            val venta = json.optDouble("venta", Double.NaN)
            if (venta.isNaN() || venta <= 0) json.optDouble("promedio", 0.0) else venta
        } ?: fetch(PYDOLARVE) { json ->
            // {"monitors":{"usd":{"price":36.88}}}
            json.optJSONObject("monitors")?.optJSONObject("usd")?.optDouble("price", 0.0) ?: 0.0
        }
    }

    private inline fun fetch(url: String, parse: (JSONObject) -> Double): Double? {
        return try {
            val conn = (URL(url).openConnection() as HttpsURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }
            conn.responseCode
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val value = parse(JSONObject(body))
            if (value > 0 && value < 1_000_000) value else null
        } catch (_: Exception) {
            null
        }
    }
}
