package com.velocidadreal.app.medicion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Medición por HTTP con varias conexiones en paralelo, como hacen los tests de velocidad serios.
 * Se descarta el primer tramo ("calentamiento") para que el arranque lento de TCP no baje el resultado.
 */
class HttpMedidor(private val client: OkHttpClient) {

    private val bloqueAleatorio = Random.nextBytes(64 * 1024)

    /** Latencia: mediana de varias peticiones pequeñas, descartando la primera (incluye el handshake). */
    suspend fun medirLatencia(url: () -> String, intentos: Int = 6): Double = withContext(Dispatchers.IO) {
        val tiempos = mutableListOf<Double>()
        repeat(intentos) { i ->
            val t0 = System.nanoTime()
            client.newCall(Request.Builder().url(url()).header("Cache-Control", "no-cache").build())
                .execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    r.body?.bytes()
                }
            val ms = (System.nanoTime() - t0) / 1e6
            if (i > 0) tiempos += ms
        }
        mediana(tiempos) ?: throw IOException("Sin respuesta de latencia")
    }

    suspend fun medirBajada(
        url: (Int) -> String,
        duracionMs: Long = 8_000,
        calentamientoMs: Long = 1_500,
        conexiones: Int = 4,
        alProgresar: (Double) -> Unit,
    ): Double = medir(duracionMs, calentamientoMs, alProgresar) { fin, contador, error ->
        coroutineScope {
            (0 until conexiones).map { i ->
                launch(Dispatchers.IO) {
                    val buf = ByteArray(64 * 1024)
                    while (isActive && System.nanoTime() < fin) {
                        try {
                            val req = Request.Builder().url(url(i)).header("Cache-Control", "no-cache").build()
                            client.newCall(req).execute().use { r ->
                                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                                val entrada = r.body?.byteStream() ?: throw IOException("Respuesta vacía")
                                while (System.nanoTime() < fin) {
                                    val n = entrada.read(buf)
                                    if (n < 0) break
                                    contador.addAndGet(n.toLong())
                                }
                            }
                        } catch (e: IOException) {
                            error.set(e)
                            break
                        }
                    }
                }
            }.joinAll()
        }
    }

    suspend fun medirSubida(
        url: (Int) -> String,
        duracionMs: Long = 8_000,
        calentamientoMs: Long = 1_500,
        conexiones: Int = 4,
        bytesPorPeticion: Long = 8L * 1024 * 1024,
        alProgresar: (Double) -> Unit,
    ): Double = medir(duracionMs, calentamientoMs, alProgresar) { fin, contador, error ->
        coroutineScope {
            (0 until conexiones).map { i ->
                launch(Dispatchers.IO) {
                    while (isActive && System.nanoTime() < fin) {
                        try {
                            val cuerpo = CuerpoSubida(bytesPorPeticion, fin, contador)
                            val req = Request.Builder().url(url(i)).post(cuerpo).build()
                            client.newCall(req).execute().use { r ->
                                if (!r.isSuccessful && r.code != 413) throw IOException("HTTP ${r.code}")
                            }
                        } catch (e: IOException) {
                            // Al cortar la subida por tiempo, el servidor puede cerrar: no es un error.
                            if (System.nanoTime() < fin) error.set(e)
                            break
                        }
                    }
                }
            }.joinAll()
        }
    }

    /** Esquema común: arranca el trabajo, muestrea bytes cada 250 ms y calcula Mbps tras el calentamiento. */
    private suspend fun medir(
        duracionMs: Long,
        calentamientoMs: Long,
        alProgresar: (Double) -> Unit,
        trabajo: suspend (fin: Long, contador: AtomicLong, error: AtomicReference<IOException?>) -> Unit,
    ): Double = coroutineScope {
        val contador = AtomicLong(0)
        val error = AtomicReference<IOException?>(null)
        val inicio = System.nanoTime()
        val fin = inicio + duracionMs * 1_000_000
        val marcaCalentamiento = inicio + calentamientoMs * 1_000_000
        var bytesAlCalentar = -1L
        var tiempoAlCalentar = 0L

        val muestreo = launch {
            while (isActive) {
                delay(250)
                val ahora = System.nanoTime()
                val bytes = contador.get()
                if (bytesAlCalentar < 0 && ahora >= marcaCalentamiento) {
                    bytesAlCalentar = bytes
                    tiempoAlCalentar = ahora
                }
                val seg = (ahora - inicio) / 1e9
                if (seg > 0) alProgresar(bytes * 8 / seg / 1e6)
            }
        }
        trabajo(fin, contador, error)
        muestreo.cancelAndJoin()

        val ahora = System.nanoTime()
        val total = contador.get()
        if (total == 0L) throw error.get() ?: IOException("No se transfirieron datos")

        val mbps = if (bytesAlCalentar >= 0 && ahora > tiempoAlCalentar) {
            (total - bytesAlCalentar) * 8 / ((ahora - tiempoAlCalentar) / 1e9) / 1e6
        } else {
            total * 8 / ((ahora - inicio) / 1e9) / 1e6
        }
        alProgresar(mbps)
        mbps
    }

    private inner class CuerpoSubida(
        private val largo: Long,
        private val fin: Long,
        private val contador: AtomicLong,
    ) : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = largo
        override fun writeTo(sink: BufferedSink) {
            var restante = largo
            while (restante > 0) {
                if (System.nanoTime() >= fin) throw IOException("fin de la prueba")
                val n = minOf(restante, bloqueAleatorio.size.toLong()).toInt()
                sink.write(bloqueAleatorio, 0, n)
                sink.flush()
                contador.addAndGet(n.toLong())
                restante -= n
            }
        }
    }
}
