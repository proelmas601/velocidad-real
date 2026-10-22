package com.velocidadreal.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.velocidadreal.app.medicion.Fase
import com.velocidadreal.app.medicion.MedicionServidor
import com.velocidadreal.app.medicion.ResultadoPrueba
import com.velocidadreal.app.medicion.TipoServidor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mono = FontFamily.Monospace

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()

    BackHandler(enabled = e.pantalla != Pantalla.INICIO) {
        if (e.pantalla == Pantalla.PRUEBA) vm.cancelarPrueba() else vm.irA(Pantalla.INICIO)
    }

    Scaffold(
        containerColor = Colores.fondo,
        bottomBar = {
            if (e.pantalla == Pantalla.INICIO || e.pantalla == Pantalla.HISTORIAL) {
                NavigationBar(containerColor = Colores.tarjeta) {
                    NavigationBarItem(
                        selected = e.pantalla == Pantalla.INICIO,
                        onClick = { vm.irA(Pantalla.INICIO) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Medir") },
                    )
                    NavigationBarItem(
                        selected = e.pantalla == Pantalla.HISTORIAL,
                        onClick = { vm.irA(Pantalla.HISTORIAL) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Historial") },
                    )
                }
            }
        },
    ) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            when (e.pantalla) {
                Pantalla.INICIO -> Inicio(e, vm)
                Pantalla.PRUEBA -> Prueba(e, vm)
                Pantalla.RESULTADO -> e.resultado?.let { Resultado(it, vm) } ?: Inicio(e, vm)
                Pantalla.HISTORIAL -> HistorialPantalla(e, vm)
            }
        }
    }
}

// ---------- Inicio ----------

@Composable
private fun Inicio(e: EstadoUi, vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("VELOCIDAD REAL", fontSize = 13.sp, color = Colores.tenue, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)

        Tarjeta {
            Text("Tu plan contratado", fontSize = 13.sp, color = Colores.tenue)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = e.planBajada, onValueChange = vm::cambiarPlanBajada,
                    label = { Text("Bajada (Mbps)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = e.planSubida, onValueChange = vm::cambiarPlanSubida,
                    label = { Text("Subida (Mbps)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Conexión actual: ${e.conexion}", fontSize = 13.sp, color = Colores.tenue)
        }

        e.error?.let { Aviso(titulo = "Algo salió mal", texto = it) }

        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(220.dp).clip(CircleShape).background(Colores.azulClaro).padding(10.dp)
                    .clip(CircleShape).background(Colores.azul).clickable { vm.iniciarPrueba() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Medir", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("velocidad real", color = Colores.azulClaro, fontSize = 13.sp)
                }
            }
            Text(
                "Prueba contra servidores neutrales y contra un servidor de test popular, y compara ambos. Tarda cerca de un minuto.",
                fontSize = 13.sp, color = Colores.tenue, textAlign = TextAlign.Center,
                modifier = Modifier.width(290.dp),
            )
            if (e.conexion == "Wi-Fi") {
                Text(
                    "Consejo: para evidencia más sólida, mide cerca del router o por cable. Un Wi‑Fi débil también baja la velocidad.",
                    fontSize = 12.sp, color = Colores.tenue, textAlign = TextAlign.Center,
                    modifier = Modifier.width(290.dp),
                )
            }
        }

        e.historial.firstOrNull()?.let { ultima ->
            Tarjeta(Modifier.clickable { vm.verResultado(ultima) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Última prueba · ${fecha(ultima.fecha)}", fontSize = 12.sp, color = Colores.tenue)
                        Text("${mbps(ultima.bajadaReal)} Mbps real", fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ultima.porcentajePlan?.let { ChipPorcentaje(it) }
                }
            }
        }
    }
}

// ---------- Prueba en curso ----------

@Composable
private fun Prueba(e: EstadoUi, vm: AppViewModel) {
    val p = e.progreso
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::cancelarPrueba) { Icon(Icons.Filled.Close, contentDescription = "Cancelar prueba") }
            Text(
                when (p?.fase) {
                    Fase.LATENCIA -> "Midiendo latencia…"
                    Fase.BAJADA -> "Midiendo bajada…"
                    Fase.SUBIDA -> "Midiendo subida…"
                    else -> "Preparando…"
                },
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        val pasos = p?.pasosTotales ?: 1
        val hecho = p?.pasoActual ?: 0
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pasos) { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i < hecho) Colores.azul else Colores.borde)
                )
            }
        }

        Tarjeta(horizontal = Alignment.CenterHorizontally) {
            val actual = p?.mbpsActual ?: 0.0
            val plan = e.planBajada.toDoubleOrNull() ?: 0.0
            Velocimetro(actual, maxOf(plan, actual * 1.2, 100.0))
            Text(p?.servidor ?: "", fontSize = 13.sp, color = Colores.tenue)
        }

        Text("SERVIDORES MEDIDOS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Colores.tenue)
        p?.parciales?.forEach { FilaServidor(it) }
    }
}

@Composable
private fun Velocimetro(valor: Double, maximo: Double) {
    Box(Modifier.size(width = 280.dp, height = 170.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.size(width = 280.dp, height = 170.dp)) {
            val grosor = 18.dp.toPx()
            val diam = size.width - grosor
            val tl = Offset(grosor / 2, grosor / 2)
            val sz = Size(diam, diam)
            drawArc(Colores.pista, 180f, 180f, false, tl, sz, style = Stroke(grosor, cap = StrokeCap.Round))
            val frac = (valor / maximo).coerceIn(0.0, 1.0).toFloat()
            if (frac > 0f) {
                drawArc(Colores.azul, 180f, 180f * frac, false, tl, sz, style = Stroke(grosor, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(mbps(valor), fontFamily = Mono, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
            Text("Mbps", fontSize = 15.sp, color = Colores.tenue)
        }
    }
}

// ---------- Resultado ----------

@Composable
private fun Resultado(r: ResultadoPrueba, vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.irA(Pantalla.INICIO) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column {
                Text("Resultado", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${fecha(r.fecha)} · ${r.conexion}${r.proveedor?.let { " · $it" } ?: ""}", fontSize = 12.sp, color = Colores.tenue)
            }
        }

        if (r.sospechaTrampa) {
            Aviso(
                titulo = "Posible trato preferencial",
                texto = "El servidor de test popular dio ${String.format(Locale.US, "%.1f", r.factorPreferencia ?: 0.0)} veces " +
                    "más velocidad que los servidores neutrales en esta misma prueba.",
            )
        }

        Tarjeta {
            Row(Modifier.fillMaxWidth()) {
                Text("Bajada real", fontSize = 13.sp, color = Colores.tenue, modifier = Modifier.weight(1f))
                if (r.planBajadaMbps > 0) Text("Plan: ${mbps(r.planBajadaMbps)} Mbps", fontSize = 12.sp, color = Colores.tenue)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(mbps(r.bajadaReal), fontFamily = Mono, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
                Text(" Mbps", fontSize = 16.sp, color = Colores.tenue, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                "Mediana de servidores neutrales" + (r.porcentajePlan?.let { " · ${it.toInt()}% del plan" } ?: ""),
                fontSize = 12.sp, color = Colores.tenue,
            )
            val escala = maxOf(r.planBajadaMbps, r.servidores.maxOfOrNull { it.bajadaMbps ?: 0.0 } ?: 0.0, 1.0)
            Spacer(Modifier.height(4.dp))
            r.servidores.forEach { BarraServidor(it, escala) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato("Subida", "${mbps(r.subidaReal)}", Modifier.weight(1f))
            Dato("Latencia", "${mbps(r.latenciaMs)} ms", Modifier.weight(1f))
            Dato("Con carga", "${mbps(r.latenciaConCargaMs)} ms", Modifier.weight(1f))
        }

        Text(
            "La latencia con carga mide cuánto empeora la respuesta mientras usas toda la conexión. Si sube mucho, la red está saturada.",
            fontSize = 12.sp, color = Colores.tenue,
        )

        Button(
            onClick = { vm.irA(Pantalla.HISTORIAL) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Ver historial y evidencia", fontSize = 16.sp) }
        OutlinedButton(
            onClick = vm::iniciarPrueba,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Repetir prueba", color = Colores.tinta) }
    }
}

@Composable
private fun BarraServidor(s: MedicionServidor, escala: Double) {
    val control = s.tipo == TipoServidor.CONTROL
    Column(Modifier.padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                s.nombre + if (control) " (control)" else " (neutral)",
                fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Text(
                s.bajadaMbps?.let { mbps(it) } ?: (s.error ?: "—"),
                fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (control) Colores.naranjaTexto else Colores.tinta,
            )
        }
        val frac = ((s.bajadaMbps ?: 0.0) / escala).coerceIn(0.0, 1.0).toFloat()
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Colores.pista)) {
            if (frac > 0f) {
                Box(
                    Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (control) Colores.naranja else Colores.azul)
                )
            }
        }
    }
}

// ---------- Historial ----------

@Composable
private fun HistorialPantalla(e: EstadoUi, vm: AppViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text("Historial", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (e.historial.isEmpty()) {
            item { Text("Aún no hay pruebas guardadas.", color = Colores.tenue) }
        } else {
            item {
                val prom = e.historial.mapNotNull { it.porcentajePlan }.takeIf { it.isNotEmpty() }?.average()
                val alertas = e.historial.count { it.sospechaTrampa }
                Tarjeta {
                    Text("${e.historial.size} pruebas guardadas", fontWeight = FontWeight.SemiBold)
                    prom?.let { Text("En promedio recibes el ${it.toInt()}% de tu plan.", fontSize = 14.sp) }
                    if (alertas > 0) {
                        Text("$alertas con señales de trato preferencial.", fontSize = 14.sp, color = Colores.naranjaTexto)
                    }
                }
            }
            item {
                Button(
                    onClick = vm::exportar,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar reporte PDF para reclamo")
                }
            }
            e.error?.let { item { Aviso("Algo salió mal", it) } }
            items(e.historial, key = { it.fecha }) { r ->
                Tarjeta(Modifier.clickable { vm.verResultado(r) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fecha(r.fecha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${r.conexion} · neutral ${mbps(r.bajadaReal)} / popular ${mbps(r.bajadaControl)}",
                                fontSize = 12.sp, color = Colores.tenue,
                            )
                        }
                        if (r.sospechaTrampa) Chip("Alerta", Colores.chipAlerta, Colores.naranjaTexto)
                        else r.porcentajePlan?.let { ChipPorcentaje(it) }
                    }
                }
            }
            item {
                TextButton(onClick = vm::borrarHistorial) { Text("Borrar historial", color = Colores.tenue) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ---------- Piezas comunes ----------

@Composable
private fun Tarjeta(
    modifier: Modifier = Modifier,
    horizontal: Alignment.Horizontal = Alignment.Start,
    contenido: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.tarjeta)
            .border(1.dp, Colores.borde, RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalAlignment = horizontal,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { contenido() }
}

@Composable
private fun Aviso(titulo: String, texto: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.naranjaFondo)
            .border(1.dp, Colores.naranjaBorde, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Colores.naranjaTexto)
        Column {
            Text(titulo, fontWeight = FontWeight.Bold, color = Colores.naranjaTexto)
            Text(texto, fontSize = 13.sp, color = Colores.naranjaTexto)
        }
    }
}

@Composable
private fun FilaServidor(s: MedicionServidor) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Colores.tarjeta)
            .border(1.dp, Colores.borde, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.nombre, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(s.ubicacion ?: if (s.tipo == TipoServidor.NEUTRAL) "Neutral" else "Control", fontSize = 12.sp, color = Colores.tenue)
        }
        Text(s.bajadaMbps?.let { mbps(it) } ?: (s.error ?: "—"), fontFamily = Mono, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Colores.tarjeta)
            .border(1.dp, Colores.borde, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text(etiqueta, fontSize = 12.sp, color = Colores.tenue)
        Text(valor, fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChipPorcentaje(p: Double) {
    if (p < 80) Chip("${p.toInt()}% del plan", Colores.chipAlerta, Colores.naranjaTexto)
    else Chip("${p.toInt()}% del plan", Colores.chipOk, Colores.verdeTexto)
}

@Composable
private fun Chip(texto: String, fondo: Color, color: Color) {
    Text(
        texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(fondo).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun mbps(v: Double?): String = when {
    v == null -> "—"
    v >= 100 -> v.toInt().toString()
    else -> String.format(Locale.US, "%.1f", v)
}

private fun fecha(ms: Long): String = SimpleDateFormat("d MMM HH:mm", Locale("es")).format(Date(ms))
