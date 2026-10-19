// MÓDULO :core:ui
// Paquete de componentes visuales compartidos
package com.siguec.puraceramica.core.ui.componentes

// Fondo de color
import androidx.compose.foundation.background
// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
// Textos y estilos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// Grosor de la letra
import androidx.compose.ui.text.font.FontWeight
// Unidades de medida
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
// Colores de la marca
import com.siguec.puraceramica.core.ui.tema.ColoresMarca

/**
 * Franja de tres colores (rojo, azul, naranja) de la marca SIGUEC.
 * Va debajo de la barra superior y del logo.
 *
 * @param alto grosor de la franja
 */
@Composable
fun FranjaMarca(alto: Dp = 4.dp) {
    // Fila de ancho completo con los tres colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(alto),
        horizontalArrangement = Arrangement.Start
    ) {
        // Cada color ocupa un tercio del ancho ("weight(1f)" = partes iguales)
        Box(Modifier.weight(1f).height(alto).background(ColoresMarca.Rojo))
        Box(Modifier.weight(1f).height(alto).background(ColoresMarca.Azul))
        Box(Modifier.weight(1f).height(alto).background(ColoresMarca.Naranja))
    }
}

/**
 * Logo de texto "SIGUEC Puracerámica" con la franja de colores debajo.
 * (Si luego hay una imagen del logo, se cambia solo esta función.)
 */
@Composable
fun LogoSiguec(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            // "SIGUEC" grande y negro
            Text(
                text = "SIGUEC",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = ColoresMarca.Negro
            )
            Spacer(Modifier.width(8.dp))
            // "Puracerámica" en rojo
            Text(
                text = "Puracerámica",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ColoresMarca.Rojo
            )
        }
        Spacer(Modifier.height(8.dp))
        FranjaMarca(alto = 6.dp)
    }
}
