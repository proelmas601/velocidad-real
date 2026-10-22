package com.velocidadreal.app.datos

import android.content.Context
import com.velocidadreal.app.medicion.MedicionServidor
import com.velocidadreal.app.medicion.ResultadoPrueba
import com.velocidadreal.app.medicion.TipoServidor
import org.json.JSONArray
import org.json.JSONObject

/** Guarda el plan contratado y el historial de pruebas en el teléfono (nada sale del dispositivo). */
class Historial(context: Context) {
    private val prefs = context.getSharedPreferences("velocidad_real", Context.MODE_PRIVATE)

    var planBajada: Double
        get() = prefs.getFloat("plan_bajada", 0f).toDouble()
        set(v) = prefs.edit().putFloat("plan_bajada", v.toFloat()).apply()

    var planSubida: Double
        get() = prefs.getFloat("plan_subida", 0f).toDouble()
        set(v) = prefs.edit().putFloat("plan_subida", v.toFloat()).apply()

    fun cargar(): List<ResultadoPrueba> {
        val arr = JSONArray(prefs.getString("pruebas", "[]"))
        return (0 until arr.length()).mapNotNull { runCatching { aResultado(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun agregar(r: ResultadoPrueba) {
        val todos = (listOf(r) + cargar()).take(300)
        val arr = JSONArray()
        todos.forEach { arr.put(aJson(it)) }
        prefs.edit().putString("pruebas", arr.toString()).apply()
    }

    fun borrarTodo() = prefs.edit().remove("pruebas").apply()

    private fun aJson(r: ResultadoPrueba) = JSONObject().apply {
        put("fecha", r.fecha)
        put("conexion", r.conexion)
        put("proveedor", r.proveedor ?: JSONObject.NULL)
        put("planBajada", r.planBajadaMbps)
        put("planSubida", r.planSubidaMbps)
        put("latenciaCarga", r.latenciaConCargaMs ?: JSONObject.NULL)
        put("servidores", JSONArray().apply {
            r.servidores.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("nombre", s.nombre)
                    put("tipo", s.tipo.name)
                    put("ubicacion", s.ubicacion ?: JSONObject.NULL)
                    put("bajada", s.bajadaMbps ?: JSONObject.NULL)
                    put("subida", s.subidaMbps ?: JSONObject.NULL)
                    put("latencia", s.latenciaMs ?: JSONObject.NULL)
                    put("error", s.error ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun aResultado(j: JSONObject): ResultadoPrueba {
        val arr = j.getJSONArray("servidores")
        val servidores = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            MedicionServidor(
                id = s.getString("id"),
                nombre = s.getString("nombre"),
                tipo = TipoServidor.valueOf(s.getString("tipo")),
                ubicacion = s.optStr("ubicacion"),
                bajadaMbps = s.optNum("bajada"),
                subidaMbps = s.optNum("subida"),
                latenciaMs = s.optNum("latencia"),
                error = s.optStr("error"),
            )
        }
        return ResultadoPrueba(
            fecha = j.getLong("fecha"),
            conexion = j.getString("conexion"),
            proveedor = j.optStr("proveedor"),
            planBajadaMbps = j.getDouble("planBajada"),
            planSubidaMbps = j.getDouble("planSubida"),
            servidores = servidores,
            latenciaConCargaMs = j.optNum("latenciaCarga"),
        )
    }

    private fun JSONObject.optNum(k: String): Double? = if (isNull(k)) null else optDouble(k).takeUnless { it.isNaN() }
    private fun JSONObject.optStr(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }
}
