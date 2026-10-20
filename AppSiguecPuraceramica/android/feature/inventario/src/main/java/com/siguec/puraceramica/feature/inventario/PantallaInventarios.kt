// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
// Lista
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
// Modificadores
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Modelos, sesión y componentes compartidos
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio

/**
 * Pantalla "Inventarios": los inventarios físicos de Profit ABIERTOS en este teléfono.
 *  - "Abrir inventario" (WiFi): elegir uno de saInventarioFisico sin procesar.
 *  - Cada inventario: Contar (sin WiFi), Enviar a Profit (WiFi, permiso "cerrar") y,
 *    ya enviado, quitarlo del teléfono.
 *
 * @param puedeAbrir permiso "contar inventario"
 * @param puedeEnviar permiso "cerrar inventario"
 * @param alAbrirNuevo abre la pantalla "Abrir inventario"
 * @param alContar abre la pantalla de contar en ese inventario
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
fun PantallaInventarios(
    gestor: GestorSesion,
    coEmpresa: String,
    nombreEmpresa: String,
    puedeAbrir: Boolean,
    puedeEnviar: Boolean,
    alAbrirNuevo: () -> Unit,
    alContar: (Int) -> Unit,
    alVolver: () -> Unit
) {
    val vm: InventariosViewModel = viewModel(
        key = "inventarios-$coEmpresa",
        factory = viewModelFactory { initializer { InventariosViewModel(gestor, coEmpresa) } })
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var enviando by remember { mutableStateOf<InventarioDto?>(null) }
    var quitando by remember { mutableStateOf<InventarioDto?>(null) }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }
    // Al volver de contar se refresca el avance
    LaunchedEffect(Unit) { vm.cargar() }

    Scaffold(
        topBar = { BarraSuperior(titulo = "Inventarios", subtitulo = nombreEmpresa, alVolver = alVolver) },
        floatingActionButton = {
            if (puedeAbrir) {
                ExtendedFloatingActionButton(
                    onClick = { if (!estado.trabajando) alAbrirNuevo() },
                    icon = {
                        if (estado.trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.LockOpen, contentDescription = null)
                    },
                    text = { Text("Abrir inventario") }
                )
            }
        },
        snackbarHost = { SnackbarHost(avisos) }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                estado.cargando -> Cargando()
                estado.inventarios.isEmpty() -> MensajeVacio(
                    "No hay inventarios abiertos en este teléfono. Con el WiFi de la empresa toque " +
                        "\"Abrir inventario\" y elija uno de los creados en Profit.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(estado.inventarios, key = { it.id }) { inv ->
                        TarjetaInventario(inv, puedeEnviar, trabajando = estado.trabajando,
                            alContar = { alContar(inv.id) }, alEnviar = { enviando = inv }, alQuitar = { quitando = inv })
                    }
                }
            }
        }
    }

    enviando?.let { inv ->
        AlertDialog(
            onDismissRequest = { enviando = null },
            title = { Text("Enviar a Profit") },
            text = {
                Text("Conéctese al WiFi de la empresa. Se envían al inventario ${inv.numero} los ${inv.conteos} " +
                    "conteos de este teléfono (${inv.articulosContados} artículos). Después ya no se podrá contar " +
                    "aquí; el inventario se cierra en Profit.")
            },
            confirmButton = { TextButton(onClick = { enviando = null; vm.enviar(inv) }) { Text("Enviar") } },
            dismissButton = { TextButton(onClick = { enviando = null }) { Text("Cancelar") } }
        )
    }
    quitando?.let { inv ->
        AlertDialog(
            onDismissRequest = { quitando = null },
            title = { Text("Quitar del teléfono") },
            text = { Text("El inventario ${inv.numero} ya se envió a Profit. ¿Quitarlo de este teléfono con sus conteos?") },
            confirmButton = { TextButton(onClick = { quitando = null; vm.borrarEnviado(inv) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { quitando = null }) { Text("Cancelar") } }
        )
    }
}

/** Un inventario: almacén, quién lo abrió, avance y sus botones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaInventario(
    inv: InventarioDto,
    puedeEnviar: Boolean,
    trabajando: Boolean,
    alContar: () -> Unit,
    alEnviar: () -> Unit,
    alQuitar: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (inv.abierto) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Inventario.titulo(inv), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (inv.descripcion.isNotBlank()) Text(inv.descripcion, style = MaterialTheme.typography.bodyMedium)
            inv.fecha?.let { Text("Inicio en Profit: ${Formato.fecha(it)}", style = MaterialTheme.typography.bodySmall) }
            Text("Abierto en la App por ${inv.abiertoPor}" + (inv.fechaApertura?.let { " el " + Formato.fecha(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            if (inv.artCero) {
                Text("Los artículos no contados quedan en CERO al cerrarlo en Profit",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text("En este teléfono: ${inv.articulosContados} artículos contados · ${inv.conteos} conteos",
                style = MaterialTheme.typography.bodySmall)
            if (!inv.abierto) {
                Text("ENVIADO a Profit por ${inv.enviadoPor}" + (inv.fechaEnvio?.let { " el " + Formato.fecha(it) } ?: ""),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            // FlowRow: si los botones no caben en una fila, bajan a la siguiente
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inv.abierto) {
                    OutlinedButton(onClick = alContar) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Text(" Contar")
                    }
                    if (puedeEnviar) {
                        OutlinedButton(onClick = alEnviar, enabled = !trabajando && inv.conteos > 0) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Text(" Enviar a Profit")
                        }
                    }
                } else {
                    OutlinedButton(onClick = alQuitar) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(" Quitar del teléfono")
                    }
                }
            }
        }
    }
}
