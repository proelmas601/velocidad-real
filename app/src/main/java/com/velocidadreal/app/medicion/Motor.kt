package com.velocidadreal.app.medicion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Ejecuta la prueba completa: mide contra servidores neutrales (M-Lab y Cloudflare)
 * y contra un servidor de test popular, en la misma sesión, para poder compararlos.
 */
class Motor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val http = HttpMedidor(client)
    private val ndt7 = Ndt7(client)

    suspend fun ejecutar(
        conexion: String,
        planBajada: Double,
        planSubida: Double,
        alProgresar: (Progreso) -> Unit,
    ): ResultadoPrueba = coroutineScope {
        alProgresar(Progreso(Fase.PREPARANDO, "Buscando servidores…"))

        val metaD = async { intentar { Cloudflare.meta(client) } }
        val mlabD = async { intentar { ndt7.buscarServidor() } }
        val popularesD = async { intentar { ServidorPopular.cercanos(client) } }
        val meta = metaD.await()
        val mlab = mlabD.await()
        val populares = popularesD.await().orEmpty()

        val pasosTotales = 6
        var paso = 0
        val hechos = mutableListOf<MedicionServidor>()
        var latenciaCarga: Double? = null

        fun prog(fase: Fase, nombre: String, mbps: Double = 0.0) =
            alProgresar(Progreso(fase, nombre, mbps, paso, pasosTotales, hechos.toList()))

        // 1) M-Lab (neutral)
        run {
            val nombre = "M-Lab (NDT7)"
            if (mlab == null) {
                hechos += MedicionServidor("mlab", nombre, TipoServidor.NEUTRAL, error = "No disponible")
                paso += 2
                return@run
            }
            prog(Fase.BAJADA, nombre)
            val baj = intentar { ndt7.bajada(mlab) { prog(Fase.BAJADA, nombre, it) } }
            paso++
            prog(Fase.SUBIDA, nombre)
            val sub = intentar { ndt7.subida(mlab) { prog(Fase.SUBIDA, nombre, it) } }
            paso++
            hechos += MedicionServidor(
                "mlab", nombre, TipoServidor.NEUTRAL, mlab.ciudad,
                bajadaMbps = baj?.mbps, subidaMbps = sub?.mbps, latenciaMs = baj?.minRttMs,
                error = if (baj == null && sub == null) "Falló la medición" else null,
            )
        }

        // 2) Cloudflare (neutral) + latencia con carga
        run {
            val nombre = "Cloudflare"
            prog(Fase.LATENCIA, nombre)
            val lat = intentar { http.medirLatencia({ Cloudflare.urlLatencia() }) }
            prog(Fase.BAJADA, nombre)
            val muestrasCarga = mutableListOf<Double>()
            val baj = intentar {
                coroutineScope {
                    val sonda = launch {
                        delay(2_000)
                        while (isActive) {
                            intentar { http.medirLatencia({ Cloudflare.urlLatencia() }, intentos = 2) }
                                ?.let { synchronized(muestrasCarga) { muestrasCarga += it } }
                            delay(400)
                        }
                    }
                    val r = http.medirBajada({ Cloudflare.urlBajada() }) { prog(Fase.BAJADA, nombre, it) }
                    sonda.cancel()
                    r
                }
            }
            latenciaCarga = synchronized(muestrasCarga) { mediana(muestrasCarga) }
            paso++
            prog(Fase.SUBIDA, nombre)
            val sub = intentar { http.medirSubida({ Cloudflare.urlSubida() }) { prog(Fase.SUBIDA, nombre, it) } }
            paso++
            hechos += MedicionServidor(
                "cloudflare", nombre, TipoServidor.NEUTRAL, meta?.ciudad,
                bajadaMbps = baj, subidaMbps = sub, latenciaMs = lat,
                error = if (baj == null && sub == null) "Falló la medición" else null,
            )
        }

        // 3) Servidor de test popular (control): prueba hasta 3 servidores cercanos
        run {
            val nombre = "Servidor de test popular"
            var medido: MedicionServidor? = null
            for (s in populares.take(3)) {
                prog(Fase.LATENCIA, nombre)
                val lat = intentar { http.medirLatencia({ s.urlLatencia() }) } ?: continue
                prog(Fase.BAJADA, nombre)
                val baj = intentar { http.medirBajada({ s.urlBajada() }) { prog(Fase.BAJADA, nombre, it) } } ?: continue
                paso++
                prog(Fase.SUBIDA, nombre)
                val sub = intentar { http.medirSubida({ s.urlSubida() }) { prog(Fase.SUBIDA, nombre, it) } }
                paso++
                medido = MedicionServidor("popular", nombre, TipoServidor.CONTROL, s.nombre, baj, sub, lat)
                break
            }
            hechos += medido ?: MedicionServidor("popular", nombre, TipoServidor.CONTROL, error = "No disponible")
            paso = pasosTotales
        }

        val resultado = ResultadoPrueba(
            fecha = System.currentTimeMillis(),
            conexion = conexion,
            proveedor = meta?.proveedor,
            planBajadaMbps = planBajada,
            planSubidaMbps = planSubida,
            servidores = hechos.toList(),
            latenciaConCargaMs = latenciaCarga,
        )
        if (resultado.bajadaReal == null) throw IOException("No se pudo medir contra ningún servidor neutral")
        alProgresar(Progreso(Fase.TERMINADO, "", resultado.bajadaReal ?: 0.0, pasosTotales, pasosTotales, hechos))
        resultado
    }

    /** Ejecuta y devuelve null si falla, sin tumbar la prueba completa (salvo cancelación). */
    private suspend fun <T> intentar(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
