package com.velocidadreal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velocidadreal.app.datos.Historial
import com.velocidadreal.app.datos.Reporte
import com.velocidadreal.app.datos.tipoConexion
import com.velocidadreal.app.medicion.Motor
import com.velocidadreal.app.medicion.Progreso
import com.velocidadreal.app.medicion.ResultadoPrueba
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Pantalla { INICIO, PRUEBA, RESULTADO, HISTORIAL }

data class EstadoUi(
    val pantalla: Pantalla = Pantalla.INICIO,
    val planBajada: String = "",
    val planSubida: String = "",
    val conexion: String = "",
    val progreso: Progreso? = null,
    val resultado: ResultadoPrueba? = null,
    val historial: List<ResultadoPrueba> = emptyList(),
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val datos = Historial(app)
    private val motor = Motor()
    private var trabajo: Job? = null

    private val _estado = MutableStateFlow(
        EstadoUi(
            planBajada = datos.planBajada.formatoPlan(),
            planSubida = datos.planSubida.formatoPlan(),
            conexion = tipoConexion(app),
            historial = datos.cargar(),
        )
    )
    val estado: StateFlow<EstadoUi> = _estado.asStateFlow()

    fun irA(p: Pantalla) {
        _estado.value = _estado.value.copy(pantalla = p, error = null, conexion = tipoConexion(getApplication()))
    }

    fun cambiarPlanBajada(v: String) {
        _estado.value = _estado.value.copy(planBajada = v.filtrarNumero())
        datos.planBajada = _estado.value.planBajada.toDoubleOrNull() ?: 0.0
    }

    fun cambiarPlanSubida(v: String) {
        _estado.value = _estado.value.copy(planSubida = v.filtrarNumero())
        datos.planSubida = _estado.value.planSubida.toDoubleOrNull() ?: 0.0
    }

    fun iniciarPrueba() {
        if (trabajo?.isActive == true) return
        val conexion = tipoConexion(getApplication())
        _estado.value = _estado.value.copy(pantalla = Pantalla.PRUEBA, progreso = null, error = null, conexion = conexion)
        trabajo = viewModelScope.launch(Dispatchers.Default) {
            try {
                val r = motor.ejecutar(
                    conexion = conexion,
                    planBajada = _estado.value.planBajada.toDoubleOrNull() ?: 0.0,
                    planSubida = _estado.value.planSubida.toDoubleOrNull() ?: 0.0,
                ) { p -> _estado.value = _estado.value.copy(progreso = p) }
                datos.agregar(r)
                _estado.value = _estado.value.copy(
                    pantalla = Pantalla.RESULTADO, resultado = r, historial = datos.cargar(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    pantalla = Pantalla.INICIO,
                    error = "No se pudo completar la prueba: ${e.message ?: "error de red"}",
                )
            }
        }
    }

    fun cancelarPrueba() {
        trabajo?.cancel()
        irA(Pantalla.INICIO)
    }

    fun verResultado(r: ResultadoPrueba) {
        _estado.value = _estado.value.copy(pantalla = Pantalla.RESULTADO, resultado = r)
    }

    fun exportar() {
        val h = _estado.value.historial
        if (h.isEmpty()) return
        runCatching { Reporte.generarYCompartir(getApplication(), h) }
            .onFailure { _estado.value = _estado.value.copy(error = "No se pudo generar el PDF") }
    }

    fun borrarHistorial() {
        datos.borrarTodo()
        _estado.value = _estado.value.copy(historial = emptyList())
    }
}

private fun Double.formatoPlan() = if (this <= 0) "" else if (this % 1.0 == 0.0) toLong().toString() else toString()
private fun String.filtrarNumero() = filter { it.isDigit() || it == '.' }.take(6)
