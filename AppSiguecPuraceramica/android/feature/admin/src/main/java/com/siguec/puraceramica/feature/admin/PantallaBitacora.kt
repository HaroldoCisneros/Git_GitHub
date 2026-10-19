// MÓDULO :feature:admin
package com.siguec.puraceramica.feature.admin

// Herramientas de diseño
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
// Listas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
// Componentes de Material 3
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.Administracion
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.RenglonBitacoraDto
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeError
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio

/**
 * Bitácora de la App: quién entró, qué consultó o modificó y cuándo. Lo más
 * reciente primero, con filtros por usuario, acción y periodo.
 */
@OptIn(ExperimentalMaterial3Api::class) // FilterChip
@Composable
fun PantallaBitacora(repositorio: RepositorioAdmin, alVolver: () -> Unit) {
    val vm: BitacoraViewModel = viewModel(factory = viewModelFactory { initializer { BitacoraViewModel(repositorio) } })
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lista = rememberLazyListState()

    val alFinal by remember {
        derivedStateOf {
            val ultimo = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimo >= lista.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(alFinal, estado.hayMas, estado.renglones.size) { if (alFinal) vm.cargarMas() }

    Scaffold(topBar = { BarraSuperior(titulo = "Bitácora", alVolver = alVolver) }) { margenes ->
        Column(Modifier.fillMaxSize().padding(margenes)) {

            // ---------- Usuario ----------
            OutlinedTextField(
                value = estado.usuario,
                onValueChange = { vm.alCambiarUsuario(it.uppercase()) },
                placeholder = { Text("Usuario (vacío = todos)") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                trailingIcon = {
                    if (estado.usuario.isNotEmpty()) {
                        IconButton(onClick = { vm.alCambiarUsuario("") }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ---------- Periodo ----------
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodoBitacora.entries.forEach { p ->
                    FilterChip(selected = estado.periodo == p, onClick = { vm.alElegirPeriodo(p) }, label = { Text(p.etiqueta) })
                }
            }
            // ---------- Acción ----------
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = estado.accion.isEmpty(), onClick = { vm.alElegirAccion("") }, label = { Text("Todo") })
                Administracion.ACCIONES.forEach { (codigo, texto) ->
                    FilterChip(selected = estado.accion == codigo, onClick = { vm.alElegirAccion(codigo) }, label = { Text(texto) })
                }
            }

            // ---------- Renglones ----------
            Box(Modifier.fillMaxSize()) {
                when {
                    estado.cargando && estado.renglones.isEmpty() -> Cargando("Leyendo la bitácora...")
                    estado.mensajeError != null && estado.renglones.isEmpty() ->
                        MensajeError(estado.mensajeError!!, alReintentar = { vm.buscar() })
                    estado.renglones.isEmpty() -> MensajeVacio("No hay movimientos con estos filtros")
                    else -> LazyColumn(
                        state = lista,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(estado.renglones, key = { it.id }) { RenglonMovimiento(it) }
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

/** Un movimiento: fecha, usuario, acción, empresa y detalle. */
@Composable
private fun RenglonMovimiento(b: RenglonBitacoraDto) {
    val alerta = b.accion == "CLAVE_ERRADA" || b.accion == "BLOQUEO"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.usuario.ifBlank { "—" }} · ${Administracion.accion(b.accion)}", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = if (alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(b.fecha?.let { Formato.fecha(it) } ?: "", style = MaterialTheme.typography.labelSmall)
            }
            val lugar = listOf(b.coEmpresa, b.dispositivo).filter { it.isNotBlank() }.joinToString(" · ")
            if (lugar.isNotEmpty()) {
                Text(lugar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // El detalle de las modificaciones trae saltos de línea (formato de PISTAS)
            if (b.detalle.isNotBlank()) Text(b.detalle.replace("\r", "\n"), style = MaterialTheme.typography.bodySmall)
        }
    }
}
