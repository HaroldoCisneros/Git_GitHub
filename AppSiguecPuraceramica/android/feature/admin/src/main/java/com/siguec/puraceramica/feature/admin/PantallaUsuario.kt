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
// Listas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Teclado
import androidx.compose.foundation.text.KeyboardOptions
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Red y componentes compartidos
import com.siguec.puraceramica.core.red.Administracion
import com.siguec.puraceramica.core.red.AmbienteAdminDto
import com.siguec.puraceramica.core.red.EmpresaAppDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.PermisosDto
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.UsuarioAdminDto
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeError

/** Qué diálogo está abierto. */
private sealed interface Dialogo {
    data object Datos : Dialogo
    data object Clave : Dialogo
    data class Ambiente(val actual: AmbienteAdminDto?) : Dialogo      // null = empresa nueva
    data class Quitar(val coEmpresa: String) : Dialogo
}

/**
 * Ficha de un usuario para el administrador: datos, activo, administrador,
 * desbloquear, cambiar clave y sus empresas con permisos.
 *
 * @param usuarioPropio código del administrador que está usando la App (no se puede
 *                      quitar a sí mismo el administrador ni desactivarse)
 */
@Composable
fun PantallaUsuario(
    repositorio: RepositorioAdmin,
    id: Int,
    usuarioPropio: String,
    alVolver: () -> Unit
) {
    val vm: UsuarioViewModel = viewModel(
        key = "usuario-$id",
        factory = viewModelFactory { initializer { UsuarioViewModel(repositorio, id) } }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }

    val u = estado.usuario
    // ¿Es el administrador que está usando la App?
    val propio = u != null && u.usuario.equals(usuarioPropio, ignoreCase = true)

    Scaffold(
        topBar = { BarraSuperior(titulo = u?.usuario ?: "Usuario", subtitulo = u?.nombre, alVolver = alVolver) },
        snackbarHost = { SnackbarHost(avisos) }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                estado.cargando && u == null -> Cargando("Abriendo el usuario...")
                u == null -> MensajeError(estado.mensajeError ?: "No se pudo abrir el usuario", alReintentar = vm::cargar)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ---------- Datos y estado ----------
                    item {
                        TarjetaDatos(u, propio, estado.guardando,
                            alEditar = { dialogo = Dialogo.Datos },
                            alActivar = vm::activar,
                            alAdministrador = vm::administrador,
                            alConfigurar = vm::configurar,
                            alDesbloquear = vm::desbloquear,
                            alClave = { dialogo = Dialogo.Clave })
                    }
                    // ---------- Empresas ----------
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Empresas y permisos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { dialogo = Dialogo.Ambiente(null) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text("Agregar")
                            }
                        }
                    }
                    if (u.ambientes.isEmpty()) {
                        item { Text("No tiene empresas: no puede entrar a la App.", color = MaterialTheme.colorScheme.error) }
                    }
                    items(u.ambientes, key = { it.coEmpresa }) { a ->
                        TarjetaAmbiente(a, alEditar = { dialogo = Dialogo.Ambiente(a) },
                            alQuitar = { dialogo = Dialogo.Quitar(a.coEmpresa) })
                    }
                }
            }
        }
    }

    // ---------- Diálogos ----------
    when (val d = dialogo) {
        null -> Unit
        Dialogo.Datos -> if (u != null) {
            DialogoDatos(u, alGuardar = { nombre, profit -> dialogo = null; vm.cambiarDatos(nombre, profit) },
                alCancelar = { dialogo = null })
        }
        Dialogo.Clave -> DialogoClave(alGuardar = { clave -> dialogo = null; vm.cambiarClave(clave) },
            alCancelar = { dialogo = null })
        is Dialogo.Ambiente -> DialogoAmbiente(
            actual = d.actual,
            // Para una empresa nueva: solo las que todavía no tiene
            empresas = estado.empresas.filter { e -> u?.ambientes?.none { it.coEmpresa == e.coEmpresa } ?: true },
            alGuardar = { empresa, almacen, permisos ->
                dialogo = null
                vm.guardarAmbiente(empresa, almacen, permisos)
            },
            alCancelar = { dialogo = null }
        )
        is Dialogo.Quitar -> AlertDialog(
            onDismissRequest = { dialogo = null },
            title = { Text("Quitar empresa") },
            text = { Text("¿Quitarle el acceso a ${d.coEmpresa}?") },
            confirmButton = { TextButton(onClick = { dialogo = null; vm.quitarAmbiente(d.coEmpresa) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Cancelar") } }
        )
    }
}

/** Datos del usuario con los interruptores de activo, administrador y Configuración. */
@Composable
private fun TarjetaDatos(
    u: UsuarioAdminDto,
    propio: Boolean,
    guardando: Boolean,
    alEditar: () -> Unit,
    alActivar: (Boolean) -> Unit,
    alAdministrador: (Boolean) -> Unit,
    alConfigurar: (Boolean) -> Unit,
    alDesbloquear: () -> Unit,
    alClave: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.nombre, style = MaterialTheme.typography.titleMedium)
                    Text("Usuario de Profit: ${u.usuarioProfit.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Último acceso: ${u.ultimoAcceso?.let { Formato.fecha(it) } ?: "nunca"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = alEditar) { Icon(Icons.Filled.Edit, contentDescription = "Editar datos") }
            }
            HorizontalDivider()
            // Interruptores (uno mismo no se puede desactivar ni quitar el administrador)
            Interruptor("Activo", u.activo, enabled = !guardando && !propio, alCambiar = alActivar)
            Interruptor("Administrador", u.esAdministrador, enabled = !guardando && !propio, alCambiar = alAdministrador)
            // Cambiar la Configuración del teléfono (servidor, bases, moneda); el administrador siempre puede
            Interruptor("Puede cambiar la Configuración", u.esAdministrador || u.puedeConfigurar,
                enabled = !guardando && !u.esAdministrador, alCambiar = alConfigurar)
            if (propio) {
                Text("Usted mismo: no puede desactivarse ni quitarse el administrador.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (u.bloqueado) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bloqueado por claves erradas" + (u.bloqueadoHasta?.let { " hasta " + Formato.fecha(it) } ?: ""),
                            Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = alDesbloquear, enabled = !guardando) { Text("Desbloquear") }
                    }
                }
            }
            OutlinedButton(onClick = alClave, enabled = !guardando) { Text("Cambiar clave") }
        }
    }
}

@Composable
private fun Interruptor(texto: String, valor: Boolean, enabled: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = alCambiar, enabled = enabled)
    }
}

/** Una empresa del usuario: almacén por defecto y resumen de permisos. */
@Composable
private fun TarjetaAmbiente(a: AmbienteAdminDto, alEditar: () -> Unit, alQuitar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${a.coEmpresa} · ${a.nombreEmpresa}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Almacén por defecto ${a.almacen.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall)
                Text(Administracion.resumen(a.permisos), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = alEditar) { Icon(Icons.Filled.Edit, contentDescription = "Editar permisos") }
            IconButton(onClick = alQuitar) {
                Icon(Icons.Filled.Delete, contentDescription = "Quitar empresa", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Nombre y usuario de Profit. */
@Composable
private fun DialogoDatos(u: UsuarioAdminDto, alGuardar: (String, String) -> Unit, alCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf(u.nombre) }
    var profit by remember { mutableStateOf(u.usuarioProfit) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Datos de ${u.usuario}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { if (it.length <= 100) nombre = it }, singleLine = true,
                    label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(profit, { if (it.length <= 10) profit = it.uppercase() }, singleLine = true,
                    label = { Text("Usuario de Profit") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { alGuardar(nombre, profit) }, enabled = nombre.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}

/** Clave nueva (dos veces). */
@Composable
private fun DialogoClave(alGuardar: (String) -> Unit, alCancelar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    val error = when {
        clave.isEmpty() -> null
        clave.length < 6 -> "Mínimo 6 caracteres"
        repetida.isNotEmpty() && clave != repetida -> "Las claves no coinciden"
        else -> null
    }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Cambiar clave") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(clave, { clave = it }, singleLine = true, label = { Text("Clave nueva") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repetida, { repetida = it }, singleLine = true, label = { Text("Repita la clave") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Se cerrarán sus sesiones abiertas en los teléfonos.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(clave) }, enabled = clave.length >= 6 && clave == repetida) { Text("Cambiar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}

/**
 * Empresa con sus permisos. Si [actual] es null se elige la empresa (de las que
 * todavía no tiene); si no, se editan sus permisos.
 */
@OptIn(ExperimentalMaterial3Api::class) // FilterChip
@Composable
private fun DialogoAmbiente(
    actual: AmbienteAdminDto?,
    empresas: List<EmpresaAppDto>,
    alGuardar: (String, String, Map<String, Boolean>) -> Unit,
    alCancelar: () -> Unit
) {
    var empresa by remember { mutableStateOf(actual?.coEmpresa.orEmpty()) }
    var almacen by remember { mutableStateOf(actual?.almacen.orEmpty()) }
    // Permisos marcados (empresa nueva: consultar y contar)
    val permisos = remember {
        mutableStateMapOf<String, Boolean>().apply {
            putAll(Administracion.mapa(actual?.permisos ?: PermisosDto(consultarArticulos = true, contarInventario = true)))
        }
    }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (actual == null) "Dar acceso a una empresa" else "Permisos en ${actual.coEmpresa}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (actual == null) {
                    if (empresas.isEmpty()) {
                        Text("Ya tiene todas las empresas registradas.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            empresas.forEach { e ->
                                FilterChip(selected = empresa == e.coEmpresa, onClick = { empresa = e.coEmpresa },
                                    label = { Text(e.coEmpresa) })
                            }
                        }
                    }
                }
                OutlinedTextField(almacen, { if (it.length <= 6) almacen = it.uppercase() }, singleLine = true,
                    label = { Text("Almacén por defecto") }, modifier = Modifier.fillMaxWidth())
                Administracion.PERMISOS.forEach { (clave, texto) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permisos[clave] == true, onCheckedChange = { permisos[clave] = it })
                        Text(texto, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(empresa, almacen, permisos.toMap()) }, enabled = empresa.isNotEmpty()) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}
