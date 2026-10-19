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
// Listas y desplazamiento
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.AlmacenDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeError
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio

/**
 * Pantalla "Inventarios por almacén" (permiso "preparar inventario").
 *  - Botón "Preparar inventario": se elige el almacén de Profit y se abre su
 *    inventario (el servidor toma la foto del stock de ese almacén).
 *  - Cada inventario: contar, ver las diferencias y cerrarlo.
 *
 * @param almacenInicial almacén por defecto del ambiente del usuario
 * @param alContar abre la pantalla de contar en ese inventario
 * @param alComparar abre las diferencias (contado vs. stock)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaInventarios(
    repositorio: RepositorioInventarios,
    nombreEmpresa: String,
    almacenInicial: String,
    alContar: (Int) -> Unit,
    alComparar: (Int) -> Unit,
    alVolver: () -> Unit
) {
    val vm: InventariosViewModel = viewModel(factory = viewModelFactory { initializer { InventariosViewModel(repositorio) } })
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var preparando by remember { mutableStateOf(false) }
    var cerrando by remember { mutableStateOf<InventarioDto?>(null) }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(titulo = "Inventarios por almacén", subtitulo = nombreEmpresa, alVolver = alVolver,
                acciones = {
                    IconButton(onClick = vm::cargar) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { preparando = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Preparar inventario") }
            )
        },
        snackbarHost = { SnackbarHost(avisos) }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                estado.cargando && estado.inventarios.isEmpty() -> Cargando()
                estado.mensajeError != null -> MensajeError(estado.mensajeError.orEmpty(), alReintentar = vm::cargar)
                estado.inventarios.isEmpty() ->
                    MensajeVacio("No hay inventarios. Toque \"Preparar inventario\" y elija el almacén.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(estado.inventarios, key = { it.id }) { inv ->
                        TarjetaInventario(inv, alContar = { alContar(inv.id) }, alComparar = { alComparar(inv.id) },
                            alCerrar = { cerrando = inv })
                    }
                }
            }
        }
    }

    if (preparando) {
        DialogoPreparar(
            almacenes = estado.almacenes,
            almacenInicial = almacenInicial,
            alPreparar = { coAlma, descripcion ->
                preparando = false
                vm.preparar(coAlma, descripcion)
            },
            alCancelar = { preparando = false }
        )
    }
    cerrando?.let { inv ->
        AlertDialog(
            onDismissRequest = { cerrando = null },
            title = { Text("Cerrar inventario N° ${inv.id}") },
            text = { Text("Ya no se podrá contar en el almacén ${inv.coAlma}. Las diferencias se podrán seguir viendo.") },
            confirmButton = { TextButton(onClick = { cerrando = null; vm.cerrar(inv) }) { Text("Cerrar inventario") } },
            dismissButton = { TextButton(onClick = { cerrando = null }) { Text("Cancelar") } }
        )
    }
}

/** Un inventario: almacén, estado, avance y sus botones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaInventario(inv: InventarioDto, alContar: () -> Unit, alComparar: () -> Unit, alCerrar: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (inv.abierto) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Inventario.titulo(inv), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(inv.descripcion, style = MaterialTheme.typography.bodyMedium)
            Text("Preparado por ${inv.creadoPor}" + (inv.fechaCreacion?.let { " el " + Formato.fecha(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            if (!inv.abierto) {
                Text("CERRADO por ${inv.cerradoPor}" + (inv.fechaCierre?.let { " el " + Formato.fecha(it) } ?: ""),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text("${inv.articulosContados} de ${inv.articulosStock} artículos con stock contados · ${inv.conteos} conteos",
                style = MaterialTheme.typography.bodySmall)
            // FlowRow: si los botones no caben en una fila, bajan a la siguiente
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inv.abierto) {
                    OutlinedButton(onClick = alContar) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Text(" Contar")
                    }
                }
                OutlinedButton(onClick = alComparar) {
                    Icon(Icons.Filled.Assessment, contentDescription = null)
                    Text(" Diferencias")
                }
                if (inv.abierto) {
                    OutlinedButton(onClick = alCerrar) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text(" Cerrar")
                    }
                }
            }
        }
    }
}

/**
 * Elegir el almacén (de Profit) y preparar su inventario. Los almacenes que ya
 * tienen uno abierto se muestran pero no se pueden elegir.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DialogoPreparar(
    almacenes: List<AlmacenDto>,
    almacenInicial: String,
    alPreparar: (String, String) -> Unit,
    alCancelar: () -> Unit
) {
    val libres = almacenes.filter { it.inventarioAbierto == null }
    var elegido by remember { mutableStateOf(libres.firstOrNull { it.coAlma == almacenInicial }?.coAlma.orEmpty()) }
    var descripcion by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Preparar inventario") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Elija el almacén. Se toma la foto del stock de Profit de ese almacén para comparar al final.",
                    style = MaterialTheme.typography.bodySmall)
                if (almacenes.isEmpty()) {
                    Text("No se pudieron leer los almacenes de Profit.", color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    almacenes.forEach { a ->
                        val libre = a.inventarioAbierto == null
                        FilterChip(
                            selected = elegido == a.coAlma,
                            onClick = { elegido = a.coAlma },
                            enabled = libre,
                            label = {
                                Text("${a.coAlma} ${a.desAlma}".trim() + if (libre) "" else " (abierto N° ${a.inventarioAbierto})")
                            }
                        )
                    }
                }
                OutlinedTextField(descripcion, { if (it.length <= 100) descripcion = it }, singleLine = true,
                    label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { alPreparar(elegido, descripcion) }, enabled = elegido.isNotEmpty()) { Text("Preparar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}
