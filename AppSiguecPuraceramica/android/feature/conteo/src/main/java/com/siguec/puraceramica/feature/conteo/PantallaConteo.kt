// MÓDULO :feature:conteo
package com.siguec.puraceramica.feature.conteo

// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Lista
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// Teclado
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
// Alineación, foco y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.ConteoDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.RepositorioArticulos
import com.siguec.puraceramica.core.red.RepositorioConteos
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.EscanerCodigo
import com.siguec.puraceramica.core.ui.componentes.MensajeError

/**
 * Pantalla de contar (un inventario abierto de un almacén):
 *  - Botón grande "Escanear": la cámara lee el código de barras y se muestra el
 *    artículo con lo ya contado; se escribe la cantidad y "Anotar".
 *  - Modo rápido: cada escaneo anota 1 y la cámara se abre otra vez.
 *  - Abajo, MIS últimos conteos (cada quien cuenta por su lado) para borrar un error.
 *
 * @param id número del inventario
 */
@Composable
fun PantallaConteo(
    inventarios: RepositorioInventarios,
    articulos: RepositorioArticulos,
    conteos: RepositorioConteos,
    nombreEmpresa: String,
    id: Int,
    alVolver: () -> Unit
) {
    val vm: ConteoViewModel = viewModel(
        key = "conteo-$id",
        factory = viewModelFactory { initializer { ConteoViewModel(inventarios, articulos, conteos, id) } }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val foco = LocalFocusManager.current
    val focoCantidad = remember { FocusRequester() }
    val avisos = remember { SnackbarHostState() }

    // Abre el escáner (lo usan el botón y el modo rápido)
    val escanear = {
        foco.clearFocus()
        EscanerCodigo.escanear(contexto, alLeer = vm::alLeerCodigo, alFallar = vm::alFallarEscaner)
    }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }
    // Se encontró el artículo: el cursor pasa a la cantidad
    LaunchedEffect(estado.pedirCantidad) {
        if (estado.pedirCantidad) {
            runCatching { focoCantidad.requestFocus() }
            vm.cantidadPedida()
        }
    }
    // Modo rápido: después de anotar se vuelve a abrir la cámara
    LaunchedEffect(estado.abrirCamara) {
        if (estado.abrirCamara) {
            vm.camaraAbierta()
            escanear()
        }
    }

    val detalle = estado.detalle
    val inventario = detalle?.inventario

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = inventario?.let { "Contar · ${Inventario.titulo(it)}" } ?: "Contar N° $id",
                subtitulo = nombreEmpresa,
                alVolver = alVolver,
                acciones = {
                    IconButton(onClick = vm::cargar) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(avisos) }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                estado.cargando && detalle == null -> Cargando("Abriendo el inventario...")
                detalle == null || inventario == null ->
                    MensajeError(estado.mensajeError ?: "No se pudo abrir el inventario", alReintentar = vm::cargar)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Encabezado(inventario, detalle.contadoPorMi) }

                    if (inventario.abierto) {
                        // ---------- Escanear y anotar ----------
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = escanear, enabled = !estado.anotando,
                                        modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Escanear código de barras")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = estado.modoRapido, onCheckedChange = { vm.alternarModoRapido() })
                                        Spacer(Modifier.width(8.dp))
                                        Text("Modo rápido: cada escaneo anota 1", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    OutlinedTextField(
                                        value = estado.codigo,
                                        onValueChange = vm::alCambiarCodigo,
                                        label = { Text("O escriba el código") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { vm.buscarArticulo() }),
                                        trailingIcon = {
                                            if (estado.buscando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    estado.articulo?.let { ArticuloEncontrado(it) }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = estado.cantidad,
                                            onValueChange = vm::alCambiarCantidad,
                                            label = { Text("Cantidad contada") },
                                            isError = estado.errorCantidad != null,
                                            supportingText = if (estado.errorCantidad != null) {
                                                { Text(estado.errorCantidad.orEmpty()) }
                                            } else null,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { foco.clearFocus(); vm.anotar() }),
                                            modifier = Modifier.weight(1f).focusRequester(focoCantidad)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { foco.clearFocus(); vm.anotar() }, enabled = !estado.anotando) {
                                            if (estado.anotando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            else Text("Anotar")
                                        }
                                    }
                                    Text("Cada conteo queda a su nombre y se suma. Para corregir, borre su conteo o anote en negativo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // ---------- Mis últimos conteos ----------
                    item {
                        Text("Mis últimos conteos", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (detalle.misConteos.isEmpty()) {
                        item { Text("Todavía no ha contado nada aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(detalle.misConteos, key = { it.id }) { c -> RenglonConteo(c, alBorrar = { vm.borrar(c) }) }
                }
            }
        }
    }
}

/** Almacén, estado y cuánto lleva contado este usuario. */
@Composable
private fun Encabezado(inventario: InventarioDto, contadoPorMi: Int) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (inventario.abierto) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(inventario.descripcion, style = MaterialTheme.typography.titleMedium)
            Text("Usted lleva $contadoPorMi conteos · entre todos ${inventario.conteos}",
                style = MaterialTheme.typography.bodySmall)
            if (!inventario.abierto) {
                Text("INVENTARIO CERRADO: ya no se puede contar", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** El artículo del código leído, con lo que lleva contado (para confirmar antes de anotar). */
@Composable
private fun ArticuloEncontrado(a: ArticuloDto) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(a.coArt, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(a.artDes, style = MaterialTheme.typography.bodyMedium)
            Text(Inventario.contado(a.contado), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Un conteo propio: artículo, cantidad y hora; papelera si se puede borrar. */
@Composable
private fun RenglonConteo(c: ConteoDto, alBorrar: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${c.coArt} · ${Formato.cantidad(c.cantidad)}", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                Text(c.artDes, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Leído: ${c.codigoLeido}" + (c.fecha?.let { " · " + Formato.fecha(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.puedeBorrar) {
                IconButton(onClick = { confirmar = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Borrar conteo", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Borrar conteo") },
            text = { Text("¿Borrar ${Formato.cantidad(c.cantidad)} de ${c.coArt}?") },
            confirmButton = { TextButton(onClick = { confirmar = false; alBorrar() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
