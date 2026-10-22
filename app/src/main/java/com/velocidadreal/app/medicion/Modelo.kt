package com.velocidadreal.app.medicion

/** NEUTRAL: servidores que el proveedor difícilmente prioriza. CONTROL: servidor de test popular. */
enum class TipoServidor { NEUTRAL, CONTROL }

data class MedicionServidor(
    val id: String,
    val nombre: String,
    val tipo: TipoServidor,
    val ubicacion: String? = null,
    val bajadaMbps: Double? = null,
    val subidaMbps: Double? = null,
    val latenciaMs: Double? = null,
    val error: String? = null,
)

data class ResultadoPrueba(
    val fecha: Long,
    val conexion: String,
    val proveedor: String?,
    val planBajadaMbps: Double,
    val planSubidaMbps: Double,
    val servidores: List<MedicionServidor>,
    val latenciaConCargaMs: Double?,
) {
    private val neutrales get() = servidores.filter { it.tipo == TipoServidor.NEUTRAL }
    private val control get() = servidores.firstOrNull { it.tipo == TipoServidor.CONTROL }

    /** Velocidad real = mediana de los servidores neutrales. */
    val bajadaReal: Double? get() = mediana(neutrales.mapNotNull { it.bajadaMbps })
    val subidaReal: Double? get() = mediana(neutrales.mapNotNull { it.subidaMbps })
    val latenciaMs: Double? get() = mediana(neutrales.mapNotNull { it.latenciaMs })
    val bajadaControl: Double? get() = control?.bajadaMbps

    val porcentajePlan: Double?
        get() = bajadaReal?.let { if (planBajadaMbps > 0) it / planBajadaMbps * 100 else null }

    /** Cuántas veces más rápido salió el servidor de test popular frente a los neutrales. */
    val factorPreferencia: Double?
        get() {
            val real = bajadaReal ?: return null
            val ctrl = bajadaControl ?: return null
            return if (real > 0) ctrl / real else null
        }

    /**
     * Sospecha de trato preferencial: el servidor popular da al menos 1,5 veces
     * lo de los neutrales y la diferencia es de al menos 20 Mbps
     * (para no alarmar por variaciones normales en conexiones lentas).
     */
    val sospechaTrampa: Boolean
        get() {
            val real = bajadaReal ?: return false
            val ctrl = bajadaControl ?: return false
            return ctrl >= real * 1.5 && ctrl - real >= 20
        }
}

fun mediana(valores: List<Double>): Double? {
    if (valores.isEmpty()) return null
    val s = valores.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
}

enum class Fase { PREPARANDO, LATENCIA, BAJADA, SUBIDA, TERMINADO }

data class Progreso(
    val fase: Fase,
    val servidor: String,
    val mbpsActual: Double = 0.0,
    val pasoActual: Int = 0,
    val pasosTotales: Int = 1,
    val parciales: List<MedicionServidor> = emptyList(),
)
