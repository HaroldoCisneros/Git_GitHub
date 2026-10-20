// MÓDULO :core:ui
// Paquete de componentes visuales compartidos
package com.siguec.puraceramica.core.ui.componentes

// Imagen y fondo de color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
// Herramientas de diseño
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// Esquinas redondeadas
import androidx.compose.foundation.shape.RoundedCornerShape
// Textos y estilos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
// Cómo se ajusta la imagen a su espacio
import androidx.compose.ui.layout.ContentScale
// Imágenes de la carpeta res/drawable-nodpi
import androidx.compose.ui.res.painterResource
// Grosor y alineación de la letra
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
// Unidades de medida
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
// Imágenes del módulo (logo_puraceramica.png y logo_puraceramica_barra.png)
import com.siguec.puraceramica.core.ui.R
// Colores de la marca
import com.siguec.puraceramica.core.ui.tema.ColoresMarca

/** Nombre de la App que se muestra en las pantallas (el mismo de res/values/strings.xml). */
const val NOMBRE_APP = "Conteo de Inventario Puraceramica"

/**
 * Franja de la marca: rojo del logo y rojo oscuro de la "ola".
 * Va debajo de la barra superior.
 *
 * @param alto grosor de la franja
 */
@Composable
fun FranjaMarca(alto: Dp = 4.dp) {
    // Fila de ancho completo: dos partes de rojo y una de rojo oscuro ("weight" = proporción)
    Row(modifier = Modifier.fillMaxWidth().height(alto)) {
        Box(Modifier.weight(2f).height(alto).background(ColoresMarca.Rojo))
        Box(Modifier.weight(1f).height(alto).background(ColoresMarca.RojoOscuro))
    }
}

/**
 * Logo completo de Puracerámica (símbolo, nombre y "Mucho más que Cerámicas")
 * con el nombre de la App debajo. Va en la pantalla de entrada.
 *
 * @param alto alto del logo
 */
@Composable
fun LogoPuraceramica(modifier: Modifier = Modifier, alto: Dp = 180.dp) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.logo_puraceramica),
            contentDescription = "Logo de Puracerámica",
            modifier = Modifier.height(alto),
            contentScale = ContentScale.Fit // Se ve completo, sin recortar
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = NOMBRE_APP,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ColoresMarca.RojoOscuro,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Logo horizontal de Puracerámica (blanco sobre rojo) en una tarjeta del mismo rojo,
 * con el nombre de la App debajo. Va arriba del menú principal.
 */
@Composable
fun LogoBarraPuraceramica(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ColoresMarca.RojoBarra) // Mismo rojo del fondo de la imagen: no se nota el borde
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.logo_puraceramica_barra),
            contentDescription = "Logo de Puracerámica",
            modifier = Modifier.height(56.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Conteo de Inventario",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ColoresMarca.Blanco
        )
    }
}
