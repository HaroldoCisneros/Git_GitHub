// MÓDULO :feature:conteo
package com.siguec.puraceramica.feature.conteo

// Herramientas de diseño
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Lista
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warehouse
// Componentes de Material 3
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio

/**
 * "¿En qué inventario va a contar?": las tomas físicas ABIERTAS guardadas en el
 * teléfono (traídas de Profit en "Inventarios"). Si hay una sola se entra directo.
 * Funciona sin WiFi.
 *
 * @param ultimo el último en que contó este teléfono (se marca con ✓)
 * @param alElegir abre la pantalla de contar en ese inventario
 * @param directo true = si hay un solo inventario abierto se entra sin preguntar
 */
@Composable
fun PantallaElegirInventario(
    gestor: GestorSesion,
    coEmpresa: String,
    nombreEmpresa: String,
    ultimo: Int?,
    directo: Boolean,
    alElegir: (Int) -> Unit,
    alVolver: () -> Unit
) {
    val vm: ElegirInventarioViewModel = viewModel(
        key = "elegir-$coEmpresa",
        factory = viewModelFactory { initializer { ElegirInventarioViewModel(gestor.local.inventarios, coEmpresa) } })
    val estado by vm.estado.collectAsStateWithLifecycle()

    // Un solo inventario abierto: se entra directo, UNA vez (al volver con la flecha
    // se queda aquí; rememberSaveable lo recuerda aunque la pantalla se recree)
    var yaEntro by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(estado.abiertos) {
        val unico = estado.abiertos.singleOrNull()
        if (directo && !yaEntro && unico != null) {
            yaEntro = true
            alElegir(unico.id)
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(titulo = "¿Qué inventario va a contar?", subtitulo = nombreEmpresa, alVolver = alVolver,
                acciones = {
                    IconButton(onClick = vm::cargar) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                })
        }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                estado.cargando -> Cargando()
                estado.abiertos.isEmpty() -> MensajeVacio(
                    "No hay tomas físicas en este teléfono. Vaya a Inventarios y, con el WiFi de la empresa, " +
                        "toque \"Traer de Profit\".")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(estado.abiertos, key = { it.id }) { inv ->
                        Card(Modifier.fillMaxWidth().clickable { alElegir(inv.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warehouse, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Inventario.titulo(inv), style = MaterialTheme.typography.titleMedium)
                                    Text(inv.descripcion, style = MaterialTheme.typography.bodySmall)
                                    Text("En este teléfono: ${inv.articulosContados} artículos · ${inv.conteos} conteos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (inv.id == ultimo) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Último en que contó",
                                        tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
