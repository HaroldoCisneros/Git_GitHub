// MÓDULO :feature:login
package com.siguec.puraceramica.feature.login

// Herramientas de diseño
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Lista con desplazamiento
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
// Componentes de Material 3
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// Datos de la empresa
import com.siguec.puraceramica.core.red.AmbienteDto
// Barra superior compartida
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior

/**
 * Lista de empresas (ambientes) para elegir. Se usa al entrar (si tiene
 * varias) y desde el menú con "Cambiar empresa".
 *
 * @param ambientes empresas a las que el usuario tiene acceso
 * @param actual co_empresa de la empresa elegida ahora (se marca con ✓)
 * @param alElegir acción al tocar una empresa
 */
@Composable
fun ListaEmpresas(
    ambientes: List<AmbienteDto>,
    actual: String?,
    alElegir: (AmbienteDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(ambientes, key = { it.coEmpresa }) { ambiente ->
            Card(modifier = Modifier.fillMaxWidth().clickable { alElegir(ambiente) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Business, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ambiente.nombre, style = MaterialTheme.typography.titleMedium)
                        Text("Base ${ambiente.coEmpresa}" +
                            (if (ambiente.almacen.isNotBlank()) " · Almacén ${ambiente.almacen}" else ""),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (ambiente.coEmpresa == actual) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Empresa actual",
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

/**
 * Pantalla "Cambiar empresa" (desde el menú).
 */
@Composable
fun PantallaCambiarEmpresa(
    ambientes: List<AmbienteDto>,
    actual: String?,
    alElegir: (AmbienteDto) -> Unit,
    alVolver: () -> Unit
) {
    Scaffold(topBar = { BarraSuperior(titulo = "Cambiar empresa", alVolver = alVolver) }) { margenes ->
        Column(Modifier.fillMaxSize().padding(margenes).padding(16.dp)) {
            ListaEmpresas(ambientes, actual, alElegir)
        }
    }
}
