// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

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
// Listas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
// Componentes de Material 3
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.ComparacionDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.DiferenciaDto
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeError

/**
 * Contado vs. la foto del stock del almacén en Profit (tomada al preparar): lo que sobra (+) y lo que falta (-),
 * de la diferencia más grande a la más chica. La oficina decide el ajuste en Profit.
 */
@OptIn(ExperimentalMaterial3Api::class) // FilterChip
@Composable
fun PantallaComparacion(
    repositorio: RepositorioInventarios,
    nombreEmpresa: String,
    id: Int,
    alVolver: () -> Unit
) {
    val vm: ComparacionViewModel = viewModel(
        key = "comparacion-$id",
        factory = viewModelFactory { initializer { ComparacionViewModel(repositorio, id) } }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lista = rememberLazyListState()

    val alFinal by remember {
        derivedStateOf {
            val ultimo = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimo >= lista.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(alFinal, estado.hayMas, estado.diferencias.size) { if (alFinal) vm.cargarMas() }

    Scaffold(
        topBar = {
            BarraSuperior(titulo = "Diferencias inventario N° $id", subtitulo = nombreEmpresa, alVolver = alVolver,
                acciones = {
                    IconButton(onClick = vm::cargar) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                })
        }
    ) { margenes ->
        Column(Modifier.fillMaxSize().padding(margenes)) {
            // ---------- Filtros ----------
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = estado.soloDiferencias, onClick = vm::alternarSoloDiferencias,
                    label = { Text("Solo diferencias") })
                FilterChip(selected = estado.incluirNoContados, onClick = vm::alternarNoContados,
                    label = { Text("Incluir no contados") })
            }

            val resumen = estado.resumen
            Box(Modifier.fillMaxSize()) {
                when {
                    estado.cargando && resumen == null -> Cargando("Comparando con el stock...")
                    resumen == null -> MensajeError(estado.mensajeError ?: "No se pudo comparar", alReintentar = vm::cargar)
                    else -> LazyColumn(
                        state = lista,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Resumen(resumen) }
                        if (estado.diferencias.isEmpty()) {
                            item {
                                Text(if (estado.soloDiferencias) "Todo lo contado cuadra con el stock." else "No hay artículos.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(estado.diferencias, key = { it.coArt }) { RenglonDiferencia(it) }
                        if (estado.cargandoMas) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Totales: contados, con diferencia, sobrantes, faltantes y sin contar. */
@Composable
private fun Resumen(r: ComparacionDto) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Almacén ${r.inventario.coAlma} ${r.inventario.desAlma} · ${if (r.inventario.abierto) "abierto" else "cerrado"}",
                style = MaterialTheme.typography.titleMedium)
            Text("${r.articulosContados} artículos contados · ${r.conDiferencia} con diferencia")
            Text("Sobran: ${r.sobrantes} · Faltan: ${r.faltantes}", fontWeight = FontWeight.Bold)
            if (r.noContados > 0) {
                Text("${r.noContados} artículos tienen stock en el almacén y nadie los contó",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("Solo comparación: el ajuste se hace en Profit.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Un artículo: contado, stock y diferencia (verde sobra, rojo falta). */
@Composable
private fun RenglonDiferencia(d: DiferenciaDto) {
    val signo = Inventario.signo(d.diferencia)
    val color = when {
        signo < 0 -> MaterialTheme.colorScheme.error
        signo > 0 -> Color(0xFF2E7D32)          // Verde: sobra
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.coArt, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text(d.artDes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (d.contadoSi) "Contado ${Formato.cantidad(d.contado)}" else "NO CONTADO") +
                        " · Stock ${Formato.cantidad(d.stock)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(Inventario.diferencia(d.diferencia), style = MaterialTheme.typography.titleMedium, color = color,
                fontWeight = FontWeight.Bold)
        }
    }
}
