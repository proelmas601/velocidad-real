package com.velocidadreal.app.medicion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Cloudflare: su tráfico de prueba viaja por la misma red que sirve millones de webs. */
object Cloudflare {
    private const val BASE = "https://speed.cloudflare.com"
    fun urlLatencia() = "$BASE/__down?bytes=0&r=${UUID.randomUUID()}"
    fun urlBajada() = "$BASE/__down?bytes=25000000&r=${UUID.randomUUID()}"
    fun urlSubida() = "$BASE/__up?r=${UUID.randomUUID()}"

    data class Meta(val proveedor: String?, val ciudad: String?)

    /** Devuelve el nombre del proveedor de internet (según la IP pública) y la ciudad. */
    suspend fun meta(client: OkHttpClient): Meta = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("$BASE/meta").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val j = JSONObject(r.body?.string() ?: "{}")
            Meta(
                proveedor = j.optString("asOrganization").ifBlank { null },
                ciudad = j.optString("city").ifBlank { null },
            )
        }
    }
}

/**
 * Servidor de la red de test más popular. Se usa como CONTROL: si este da mucho más
 * que los neutrales, es probable que el proveedor le esté dando prioridad.
 */
class ServidorPopular(val host: String, val nombre: String) {
    fun urlLatencia() = "https://$host/download?nocache=${UUID.randomUUID()}&size=100"
    fun urlBajada() = "https://$host/download?nocache=${UUID.randomUUID()}&size=25000000"
    fun urlSubida() = "https://$host/upload?nocache=${UUID.randomUUID()}"

    companion object {
        /** Lista de servidores cercanos según la ubicación de la IP. */
        suspend fun cercanos(client: OkHttpClient): List<ServidorPopular> = withContext(Dispatchers.IO) {
            val url = "https://www.speedtest.net/api/js/servers?engine=js&https_functional=true&limit=5"
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val arr = JSONArray(r.body?.string() ?: "[]")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val lugar = listOf(o.optString("sponsor"), o.optString("name"))
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    ServidorPopular(o.getString("host"), lugar)
                }
            }
        }
    }
}
