// MÓDULO :core:ui
// Paquete de los estilos visuales
package com.siguec.puraceramica.core.ui.tema

// Tema de Material Design 3
import androidx.compose.material3.MaterialTheme
// Paleta de colores para modo claro
import androidx.compose.material3.lightColorScheme
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Tipo de dato para colores
import androidx.compose.ui.graphics.Color

/**
 * Colores de la marca Puracerámica (tomados de su logo).
 * Formato 0xAARRGGBB: transparencia, rojo, verde, azul.
 */
object ColoresMarca {
    val Rojo = Color(0xFFCE2916)      // Rojo del círculo del logo: botones y franja
    val RojoOscuro = Color(0xFF8D1516) // Rojo oscuro de la "ola" del logo
    val RojoBarra = Color(0xFF9A0410) // Fondo del logo horizontal: barras superiores
    val Azul = Color(0xFF6FA3D8)      // Acento secundario
    val Naranja = Color(0xFFF19B1C)   // Avisos
    val Negro = Color(0xFF000000)     // Letras
    val Blanco = Color(0xFFFFFFFF)    // Fondo
    val GrisClaro = Color(0xFFF4F5F7) // Fondo suave de tarjetas
    val Gris = Color(0xFF6B7280)      // Textos secundarios (etiquetas)
    val Verde = Color(0xFF2E7D32)     // Mensajes de éxito ("Cambios guardados")
}

// Paleta de la app basada en la marca (solo modo claro, como Control de Visitas)
private val ColoresApp = lightColorScheme(
    primary = ColoresMarca.Rojo,             // Botones principales
    onPrimary = ColoresMarca.Blanco,         // Texto encima del rojo
    secondary = ColoresMarca.RojoOscuro,     // Elementos secundarios
    onSecondary = ColoresMarca.Blanco,
    tertiary = ColoresMarca.Naranja,         // Acentos (avisos)
    onTertiary = ColoresMarca.Negro,
    background = ColoresMarca.Blanco,        // Fondo de las pantallas
    onBackground = ColoresMarca.Negro,
    surface = ColoresMarca.Blanco,
    onSurface = ColoresMarca.Negro,
    surfaceVariant = ColoresMarca.GrisClaro, // Fondo de tarjetas
    onSurfaceVariant = ColoresMarca.Gris,
    error = ColoresMarca.Rojo                // Mensajes de error
)

/**
 * Aplica el tema de la app a todo lo que se dibuje dentro.
 * @param contenido las pantallas que usarán este tema
 */
@Composable
fun TemaSiguec(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColoresApp, content = contenido)
}
