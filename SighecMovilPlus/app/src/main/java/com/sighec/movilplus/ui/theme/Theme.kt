package com.sighec.movilplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Azul = Color(0xFF0D47A1)
val AzulClaro = Color(0xFF5472D3)
val Verde = Color(0xFF2E7D32)
val Rojo = Color(0xFFC62828)

private val Claro = lightColorScheme(
    primary = Azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF00897B),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    error = Rojo
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFF4DB6AC),
    error = Color(0xFFFFB4AB)
)

@Composable
fun SighecTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}
