package com.velocidadreal.app.medicion

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Cliente del protocolo NDT7 de M-Lab (Measurement Lab), una red de servidores
 * abierta y neutral usada en investigación. Especificación:
 * https://github.com/m-lab/ndt-server/blob/main/spec/ndt7-protocol.md
 */
class Ndt7(private val client: OkHttpClient) {

    data class Servidor(val ciudad: String?, val urlBajada: String, val urlSubida: String)

    data class Medida(val mbps: Double, val minRttMs: Double?)

    private val protocolo = "net.measurementlab.ndt.v7"
    private val duracionMs = 10_000L

    /** Pide a M-Lab el servidor más cercano (las URLs traen un token de acceso temporal). */
    suspend fun buscarServidor(): Servidor = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://locate.measurementlab.net/v2/nearest/ndt/ndt7").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("M-Lab no respondió (HTTP ${r.code})")
            val json = JSONObject(r.body?.string() ?: "")
            val primero = json.getJSONArray("results").getJSONObject(0)
            val urls = primero.getJSONObject("urls")
            val loc = primero.optJSONObject("location")
            val ciudad = loc?.let {
                listOfNotNull(it.optString("city").ifBlank { null }, it.optString("country").ifBlank { null })
                    .joinToString(", ").ifBlank { null }
            }
            Servidor(
                ciudad = ciudad,
                urlBajada = urls.getString("wss:///ndt/v7/download"),
                urlSubida = urls.getString("wss:///ndt/v7/upload"),
            )
        }
    }

    suspend fun bajada(servidor: Servidor, alProgresar: (Double) -> Unit): Medida {
        val bytes = AtomicLong(0)
        var minRttUs: Double? = null
        val terminado = CompletableDeferred<Throwable?>()
        val inicio = System.nanoTime()

        val ws = client.newWebSocket(peticion(servidor.urlBajada), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytesMsg: ByteString) {
                bytes.addAndGet(bytesMsg.size.toLong())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                bytes.addAndGet(text.length.toLong())
                runCatching {
                    JSONObject(text).optJSONObject("TCPInfo")?.let { tcp ->
                        if (tcp.has("MinRTT")) minRttUs = tcp.getDouble("MinRTT")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                terminado.complete(null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                terminado.complete(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                terminado.complete(t)
            }
        })

        val fallo = vigilar(terminado, inicio, { bytes.get() }, alProgresar)
        ws.cancel()
        val seg = (System.nanoTime() - inicio) / 1e9
        if (bytes.get() == 0L) throw IOException("M-Lab: sin datos", fallo)
        val mbps = bytes.get() * 8 / seg / 1e6
        return Medida(mbps, minRttUs?.let { it / 1000 })
    }

    suspend fun subida(servidor: Servidor, alProgresar: (Double) -> Unit): Medida {
        val enviados = AtomicLong(0)
        // Medidas que reporta el propio servidor: lo más fiable para la subida.
        var bytesServidor = -1L
        var tiempoServidorUs = -1L
        val terminado = CompletableDeferred<Throwable?>()
        val abierto = CompletableDeferred<Unit>()
        val inicio = System.nanoTime()

        val ws = client.newWebSocket(peticion(servidor.urlSubida), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                abierto.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    JSONObject(text).optJSONObject("TCPInfo")?.let { tcp ->
                        if (tcp.has("BytesReceived")) bytesServidor = tcp.getLong("BytesReceived")
                        if (tcp.has("ElapsedTime")) tiempoServidorUs = tcp.getLong("ElapsedTime")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                terminado.complete(null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                terminado.complete(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                abierto.complete(Unit)
                terminado.complete(t)
            }
        })

        withTimeoutOrNull(5_000) { abierto.await() } ?: run {
            ws.cancel()
            throw IOException("M-Lab: no se pudo abrir la subida")
        }

        val bloque = Random.nextBytes(64 * 1024).toByteString()
        val maxCola = 4L * bloque.size
        val fin = inicio + duracionMs * 1_000_000
        var ultimoReporte = 0L
        withContext(Dispatchers.IO) {
            while (System.nanoTime() < fin && !terminado.isCompleted) {
                if (ws.queueSize() < maxCola) {
                    if (ws.send(bloque)) enviados.addAndGet(bloque.size.toLong())
                } else {
                    delay(2)
                }
                val ahora = System.nanoTime()
                if (ahora - ultimoReporte > 250_000_000) {
                    ultimoReporte = ahora
                    val seg = (ahora - inicio) / 1e9
                    alProgresar((enviados.get() - ws.queueSize()) * 8 / seg / 1e6)
                }
            }
        }
        val segCliente = (System.nanoTime() - inicio) / 1e9
        val bytesCliente = enviados.get() - ws.queueSize()
        ws.close(1000, null)
        withTimeoutOrNull(2_000) { terminado.await() }
        ws.cancel()

        val mbps = if (bytesServidor > 0 && tiempoServidorUs > 0) {
            bytesServidor * 8 / (tiempoServidorUs / 1e6) / 1e6
        } else {
            if (bytesCliente <= 0) throw IOException("M-Lab: no se pudo subir datos")
            bytesCliente * 8 / segCliente / 1e6
        }
        alProgresar(mbps)
        return Medida(mbps, null)
    }

    private fun peticion(url: String) = Request.Builder()
        .url(url)
        .header("Sec-WebSocket-Protocol", protocolo)
        .build()

    private suspend fun vigilar(
        terminado: CompletableDeferred<Throwable?>,
        inicio: Long,
        bytes: () -> Long,
        alProgresar: (Double) -> Unit,
    ): Throwable? {
        val limite = inicio + (duracionMs + 5_000) * 1_000_000
        while (!terminado.isCompleted && System.nanoTime() < limite) {
            delay(250)
            val seg = (System.nanoTime() - inicio) / 1e9
            if (seg > 0) alProgresar(bytes() * 8 / seg / 1e6)
        }
        return if (terminado.isCompleted) terminado.await() else null
    }
}
