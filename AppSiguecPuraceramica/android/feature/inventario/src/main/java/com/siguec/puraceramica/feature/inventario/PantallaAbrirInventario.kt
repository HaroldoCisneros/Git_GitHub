// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Lista
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warehouse
// Componentes de Material 3
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
// Modelos, sesión y componentes compartidos
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.TomaProfitDto
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeError
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio

/**
 * "Abrir inventario" (necesita el WiFi de la empresa):
 *  - Lista los inventarios físicos creados en Profit y sin procesar (saInventarioFisico).
 *  - Al tocar uno se muestran sus datos; "Abrir" lo guarda en el teléfono con el usuario
 *    y la hora, y pasa directo a contar.
 *
 * @param alAbierto se abrió (o ya estaba abierto en el teléfono): ir a contar ese inventario
 */
@Composable
fun PantallaAbrirInventario(
    gestor: GestorSesion,
    coEmpresa: String,
    nombreEmpresa: String,
    alAbierto: (Int) -> Unit,
    alVolver: () -> Unit
) {
    val vm: AbrirInventarioViewModel = viewModel(
        key = "abrir-$coEmpresa",
        factory = viewModelFactory { initializer { AbrirInventarioViewModel(gestor, coEmpresa) } })
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }
    LaunchedEffect(estado.abierto) {
        estado.abierto?.let { id ->
            vm.aperturaMostrada()
            alAbierto(id)
        }
    }

    val elegido = estado.elegido
    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = if (elegido == null) "Abrir inventario" else "Inventario ${elegido.numero}",
                subtitulo = nombreEmpresa,
                // Con un inventario elegido, la flecha vuelve a la lista
                alVolver = if (elegido == null) alVolver else ({ vm.elegir(null) }),
                acciones = {
                    if (elegido == null) {
                        IconButton(onClick = vm::cargar) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(avisos) }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                elegido != null -> DatosInventario(elegido, yaAbierto = elegido.numero in estado.yaAbiertos,
                    abriendo = estado.abriendo, alAbrir = vm::abrir, alCancelar = { vm.elegir(null) })
                estado.cargando -> Cargando("Consultando los inventarios en Profit...")
                estado.mensajeError != null -> MensajeError(estado.mensajeError.orEmpty(), alReintentar = vm::cargar)
                estado.inventarios.isEmpty() -> MensajeVacio(
                    "No hay inventarios físicos sin procesar en Profit. Créelo primero en Profit.")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(estado.inventarios, key = { it.numero }) { t ->
                        Card(Modifier.fillMaxWidth().clickable { vm.elegir(t) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warehouse, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.numero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (t.descripcion.isNotBlank()) Text(t.descripcion, style = MaterialTheme.typography.bodyMedium)
                                    Text(Inventario.almacen(t.coAlma, t.desAlma), style = MaterialTheme.typography.bodySmall)
                                }
                                if (t.numero in estado.yaAbiertos) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Ya abierto en este teléfono",
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

/** Los datos del inventario elegido y el botón "Abrir". */
@Composable
private fun DatosInventario(
    t: TomaProfitDto,
    yaAbierto: Boolean,
    abriendo: Boolean,
    alAbrir: () -> Unit,
    alCancelar: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dato("Inventario", t.numero)
                Dato("Descripción", t.descripcion.ifBlank { "—" })
                Dato("Almacén", Inventario.almacen(t.coAlma, t.desAlma))
                Dato("Inicio en Profit", t.fecha?.let(Formato::fecha) ?: "—")
                Dato("No contados", if (t.artCero) "Quedan en CERO al cerrarlo en Profit" else "Conservan su existencia")
                Dato("Registrado en Profit por", "Usuario ${t.coUsProfit.ifBlank { "—" }} · Sucursal ${t.coSucuProfit.ifBlank { "—" }}")
            }
        }
        Text(
            if (yaAbierto) "Este inventario ya está abierto en este teléfono: se sigue contando en el mismo."
            else "Al abrirlo queda guardado con su usuario y la hora, y se puede contar sin WiFi escaneando los artículos.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = alAbrir, enabled = !abriendo, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (abriendo) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.LockOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (yaAbierto) "Contar" else "Abrir")
            }
        }
        OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth()) { Text("Elegir otro") }
    }
}

/** Una línea "Etiqueta: valor". */
@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}
