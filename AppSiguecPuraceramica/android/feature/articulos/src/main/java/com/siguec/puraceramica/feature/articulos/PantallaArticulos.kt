// MÓDULO :feature:articulos
package com.siguec.puraceramica.feature.articulos

// Herramientas de diseño
import androidx.compose.foundation.horizontalScroll
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
// Listas y desplazamiento
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
// Teclado
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
// Alineación, foco y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.SincronizacionDto
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.EscanerCodigo
import com.siguec.puraceramica.core.ui.componentes.MensajeError
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio
import com.siguec.puraceramica.core.ui.tema.ColoresMarca

/**
 * Pantalla "Artículos": consulta de la copia del TELÉFONO (funciona sin WiFi).
 * De cada artículo SOLO se muestra el código, la descripción y el stock contado
 * (si ya se contó en el inventario elegido).
 * Con el permiso "sincronizar", el botón de arriba baja los artículos de Profit (con WiFi).
 *
 * @param puedeSincronizar permiso "sincronizar artículos" del ambiente
 * @param inventarioInicial último inventario en que se contó (se propone)
 */
@OptIn(ExperimentalMaterial3Api::class) // FilterChip
@Composable
fun PantallaArticulos(
    gestor: GestorSesion,
    coEmpresa: String,
    nombreEmpresa: String,
    puedeSincronizar: Boolean,
    inventarioInicial: Int?,
    alVolver: () -> Unit
) {
    val vm: ArticulosViewModel = viewModel(
        key = "articulos-$coEmpresa",
        factory = viewModelFactory { initializer { ArticulosViewModel(gestor, coEmpresa, inventarioInicial) } }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val foco = LocalFocusManager.current
    val avisos = remember { SnackbarHostState() }
    var confirmarSincronizar by remember { mutableStateOf(false) }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = "Artículos",
                subtitulo = nombreEmpresa,
                alVolver = alVolver,
                acciones = {
                    if (puedeSincronizar) {
                        IconButton(onClick = { confirmarSincronizar = true }, enabled = !estado.sincronizando) {
                            Icon(Icons.Filled.Sync, contentDescription = "Sincronizar con Profit")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(avisos) }
    ) { margenes ->
        Column(Modifier.fillMaxSize().padding(margenes)) {
            // ---------- Última sincronización ----------
            TarjetaSincronizacion(estado.ultima, estado.sincronizando)

            // ---------- Buscar ----------
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = estado.texto,
                    onValueChange = vm::alCambiarTexto,
                    label = { Text("Código, descripción o código de barra") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { foco.clearFocus(); vm.buscar() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = {
                    foco.clearFocus()
                    EscanerCodigo.escanear(contexto, alLeer = vm::alLeerCodigo, alFallar = vm::alFallarEscaner)
                }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear")
                }
            }

            // ---------- De qué inventario se ve el stock contado ----------
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = estado.inventarioId == null, onClick = { vm.elegirInventario(null) },
                    label = { Text("Sin inventario") })
                estado.inventarios.forEach { inv ->
                    FilterChip(selected = estado.inventarioId == inv.id, onClick = { vm.elegirInventario(inv.id) },
                        label = { Text("Toma ${inv.numero} · Alm. ${inv.coAlma}") })
                }
                if (estado.inventarioId != null) {
                    FilterChip(selected = estado.soloContados, onClick = vm::alternarSoloContados,
                        label = { Text("Solo contados") })
                }
            }

            // ---------- Resultados ----------
            Box(Modifier.weight(1f)) {
                when {
                    estado.cargando && estado.articulos.isEmpty() -> Cargando()
                    estado.mensajeError != null && estado.articulos.isEmpty() ->
                        MensajeError(estado.mensajeError.orEmpty(), alReintentar = vm::buscar)
                    estado.articulos.isEmpty() -> MensajeVacio(
                        if (estado.ultima == null) "Este teléfono todavía no tiene los artículos: sincronice con Profit (con WiFi)."
                        else "No hay artículos que coincidan.")
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(estado.articulos, key = { it.coArt }) { a ->
                            RenglonArticulo(a, conInventario = estado.inventarioId != null)
                        }
                        // Al llegar al final se pide la página siguiente
                        if (estado.hayMas) {
                            item {
                                LaunchedEffect(estado.articulos.size) { vm.masResultados() }
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmarSincronizar) {
        AlertDialog(
            onDismissRequest = { confirmarSincronizar = false },
            title = { Text("Sincronizar con Profit") },
            text = {
                Text("Conéctese al WiFi de la empresa. Se bajan todos los artículos de Profit (código, " +
                    "descripción y códigos para escanear) a este teléfono. Lo contado no se pierde.")
            },
            confirmButton = {
                TextButton(onClick = { confirmarSincronizar = false; vm.sincronizar() }) { Text("Sincronizar") }
            },
            dismissButton = { TextButton(onClick = { confirmarSincronizar = false }) { Text("Cancelar") } }
        )
    }
}

/** Cuándo se sincronizó por última vez (o la ruedita mientras sincroniza). */
@Composable
private fun TarjetaSincronizacion(ultima: SincronizacionDto?, sincronizando: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sincronizando) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Sincronizando con Profit...", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                ultima?.let {
                    "${Formato.cantidad(it.total.toDouble())} artículos · sincronizado " +
                        (it.fecha?.let(Formato::fecha) ?: "") + " por ${it.usuario}"
                } ?: "Sin sincronizar con Profit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Un artículo: código, descripción y stock contado (lo único que se muestra). */
@Composable
private fun RenglonArticulo(a: ArticuloDto, conInventario: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.coArt, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text(a.artDes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (conInventario) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (a.contado != null) {
                        Text(Formato.cantidad(a.contado), style = MaterialTheme.typography.titleMedium,
                            color = ColoresMarca.Verde)
                        Text("contado", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(Inventario.contado(null), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
