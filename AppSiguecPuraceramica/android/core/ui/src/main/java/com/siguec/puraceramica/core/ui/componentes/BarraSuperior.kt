// MÓDULO :core:ui
// Paquete de componentes visuales compartidos
package com.siguec.puraceramica.core.ui.componentes

// Columna (barra + franja de colores) y fila (botones de la derecha)
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
// Componentes de Material 3
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Una sola línea con "..." si el título es largo
import androidx.compose.ui.text.style.TextOverflow
// Colores de la marca
import com.siguec.puraceramica.core.ui.tema.ColoresMarca

/**
 * Barra superior de todas las pantallas: fondo negro, título (y subtítulo
 * opcional, por ejemplo la empresa), flecha de volver (opcional), botones a
 * la derecha (opcionales) y debajo la franja de colores de la marca.
 *
 * @param titulo texto principal
 * @param subtitulo texto pequeño debajo del título (null = no se muestra)
 * @param alVolver acción de la flecha; si es null no se muestra la flecha
 * @param acciones botones extra a la derecha
 */
// TopAppBar todavía se marca como "experimental" en Material 3; con esto aceptamos usarlo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    subtitulo: String? = null,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // "?.let" = solo si hay subtítulo
                    subtitulo?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = ColoresMarca.Blanco.copy(alpha = 0.8f), // Blanco suave sobre el rojo
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                alVolver?.let { volver ->
                    IconButton(onClick = volver) {
                        // AutoMirrored = la flecha se voltea sola en idiomas de derecha a izquierda
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            },
            actions = acciones,
            // Fondo rojo de la marca (el del logo horizontal) y textos/íconos blancos
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ColoresMarca.RojoBarra,
                titleContentColor = ColoresMarca.Blanco,
                navigationIconContentColor = ColoresMarca.Blanco,
                actionIconContentColor = ColoresMarca.Blanco
            )
        )
        // Franja roja debajo de la barra
        FranjaMarca()
    }
}
