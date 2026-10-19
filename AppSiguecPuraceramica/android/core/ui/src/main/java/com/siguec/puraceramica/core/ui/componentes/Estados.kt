// MÓDULO :core:ui
// Paquete de componentes visuales compartidos
package com.siguec.puraceramica.core.ui.componentes

// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
// Componentes de Material 3
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Ruedita de carga centrada (mientras responde el servidor).
 * @param texto mensaje debajo de la ruedita
 */
@Composable
fun Cargando(texto: String = "Consultando...", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Mensaje de error centrado con botón "Reintentar" (sin internet, servidor apagado...).
 * @param mensaje texto del error (viene del servidor o de :core:red)
 * @param alReintentar acción del botón; si es null no se muestra el botón
 */
@Composable
fun MensajeError(mensaje: String, alReintentar: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(mensaje, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        alReintentar?.let {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = it) { Text("Reintentar") }
        }
    }
}

/**
 * Texto centrado para cuando no hay nada que mostrar (búsqueda sin resultados).
 */
@Composable
fun MensajeVacio(mensaje: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(mensaje, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
