package com.velocidadreal.app.datos

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.velocidadreal.app.medicion.ResultadoPrueba
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Genera un PDF con todas las pruebas guardadas, pensado como evidencia para un reclamo. */
object Reporte {

    fun generarYCompartir(context: Context, pruebas: List<ResultadoPrueba>) {
        val archivo = generar(context, pruebas)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de velocidad de internet")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun generar(context: Context, pruebas: List<ResultadoPrueba>): File {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val doc = PdfDocument()
        val ancho = 595
        val alto = 842
        val margen = 40f
        val titulo = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val negrita = Paint().apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val normal = Paint().apply { textSize = 10f }
        val alerta = Paint().apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF9A3412.toInt() }

        var numPagina = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numPagina).create())
        var y = margen + 10

        fun linea(texto: String, p: Paint = normal, salto: Float = 15f) {
            if (y > alto - margen) {
                doc.finishPage(pagina)
                numPagina++
                pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numPagina).create())
                y = margen + 10
            }
            pagina.canvas.drawText(texto, margen, y, p)
            y += salto
        }

        fun f(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"

        linea("Reporte de velocidad de internet", titulo, 24f)
        linea("Generado: ${fmt.format(Date())}  ·  Pruebas: ${pruebas.size}")
        pruebas.firstOrNull()?.let {
            linea("Proveedor detectado: ${it.proveedor ?: "—"}  ·  Plan: ${f(it.planBajadaMbps)} / ${f(it.planSubidaMbps)} Mbps")
        }
        val reales = pruebas.mapNotNull { it.porcentajePlan }
        if (reales.isNotEmpty()) {
            linea("Promedio de velocidad real: ${f(reales.average())}% del plan contratado", negrita)
        }
        val sospechosas = pruebas.count { it.sospechaTrampa }
        if (sospechosas > 0) {
            linea("En $sospechosas pruebas el servidor de test popular dio 1,5x o más que los servidores neutrales.", alerta)
        }
        linea(
            "Metodología: cada prueba mide contra M-Lab y Cloudflare (neutrales) y un servidor de test popular (control).",
            normal, 13f,
        )
        linea("La velocidad real es la mediana de los servidores neutrales.", normal, 22f)

        for (r in pruebas) {
            linea("${fmt.format(Date(r.fecha))}  ·  ${r.conexion}", negrita)
            linea(
                "Real: ${f(r.bajadaReal)} bajada / ${f(r.subidaReal)} subida Mbps  ·  " +
                    "Latencia ${f(r.latenciaMs)} ms (con carga ${f(r.latenciaConCargaMs)} ms)",
            )
            for (s in r.servidores) {
                val det = s.error ?: "${f(s.bajadaMbps)} / ${f(s.subidaMbps)} Mbps"
                linea("   ${s.nombre}${s.ubicacion?.let { " ($it)" } ?: ""}: $det", normal, 13f)
            }
            if (r.sospechaTrampa) linea("   Posible trato preferencial al servidor de test popular", alerta, 13f)
            y += 8
        }
        doc.finishPage(pagina)

        val dir = File(context.cacheDir, "reportes").apply { mkdirs() }
        val archivo = File(dir, "reporte-velocidad.pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }
}
