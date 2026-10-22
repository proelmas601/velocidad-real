package com.velocidadreal.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Colores {
    val fondo = Color(0xFFF3F4F1)
    val tarjeta = Color(0xFFFFFFFF)
    val tinta = Color(0xFF15171A)
    val tenue = Color(0xFF5A5F66)
    val borde = Color(0xFFD5D8D2)
    val pista = Color(0xFFE4E7E1)
    val azul = Color(0xFF1F4FC9)
    val azulClaro = Color(0xFFDCE4F8)
    val naranja = Color(0xFFE8833A)
    val naranjaTexto = Color(0xFF9A3412)
    val naranjaFondo = Color(0xFFFFF4EC)
    val naranjaBorde = Color(0xFFF2C3A0)
    val chipAlerta = Color(0xFFFDEBDD)
    val chipOk = Color(0xFFE3F1E6)
    val verdeTexto = Color(0xFF1E6B35)
}

@Composable
fun Tema(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Colores.azul,
            onPrimary = Color.White,
            background = Colores.fondo,
            surface = Colores.tarjeta,
            onSurface = Colores.tinta,
            onBackground = Colores.tinta,
            outline = Colores.borde,
        ),
        content = contenido,
    )
}
