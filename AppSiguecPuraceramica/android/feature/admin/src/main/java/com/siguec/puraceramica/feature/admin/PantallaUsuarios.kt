// MÓDULO :feature:admin
package com.siguec.puraceramica.feature.admin

// Herramientas de diseño
import androidx.compose.foundation.clickable
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
// Teclado
import androidx.compose.foundation.text.KeyboardOptions
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.UsuarioAdminDto
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.Cargando
import com.siguec.puraceramica.core.ui.componentes.MensajeError
import com.siguec.puraceramica.core.ui.componentes.MensajeVacio

/**
 * Usuarios de la App (solo administradores). Al tocar uno se abre su ficha
 * (empresas, permisos, clave...). Botón para crear uno nuevo.
 *
 * @param alAbrir abre la ficha del usuario
 */
@Composable
fun PantallaUsuarios(
    repositorio: RepositorioAdmin,
    alAbrir: (Int) -> Unit,
    alVolver: () -> Unit
) {
    val vm: UsuariosViewModel = viewModel(factory = viewModelFactory { initializer { UsuariosViewModel(repositorio) } })
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var nuevo by remember { mutableStateOf(false) }

    // Usuario recién creado: se abre su ficha para darle empresas
    LaunchedEffect(estado.abrirUsuario) {
        estado.abrirUsuario?.let { id ->
            nuevo = false
            vm.usuarioAbierto()
            alAbrir(id)
        }
    }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(titulo = "Usuarios y permisos", alVolver = alVolver, acciones = {
                IconButton(onClick = vm::cargar) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
            })
        },
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.limpiarErrorNuevo(); nuevo = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Nuevo usuario") }
            )
        }
    ) { margenes ->
        Box(Modifier.fillMaxSize().padding(margenes)) {
            when {
                estado.cargando && estado.usuarios.isEmpty() -> Cargando("Buscando usuarios...")
                estado.mensajeError != null && estado.usuarios.isEmpty() ->
                    MensajeError(estado.mensajeError!!, alReintentar = vm::cargar)
                estado.usuarios.isEmpty() -> MensajeVacio("No hay usuarios")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(estado.usuarios, key = { it.id }) { u -> RenglonUsuario(u, alTocar = { alAbrir(u.id) }) }
                }
            }
        }
    }

    if (nuevo) {
        DialogoUsuarioNuevo(
            creando = estado.creando,
            error = estado.errorNuevo,
            alGuardar = vm::crear,
            alCancelar = { nuevo = false }
        )
    }
}

/** Tarjeta de un usuario: código, nombre, empresas y avisos (inactivo, bloqueado, administrador). */
@Composable
private fun RenglonUsuario(u: UsuarioAdminDto, alTocar: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = alTocar)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.usuario, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val avisos = listOfNotNull(
                    "ADMINISTRADOR".takeIf { u.esAdministrador },
                    "CONFIGURACIÓN".takeIf { u.puedeConfigurar && !u.esAdministrador },
                    "BLOQUEADO".takeIf { u.bloqueado },
                    "INACTIVO".takeIf { !u.activo }
                ).joinToString(" · ")
                if (avisos.isNotEmpty()) {
                    Text(avisos, style = MaterialTheme.typography.labelSmall,
                        color = if (u.bloqueado || !u.activo) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(u.nombre, style = MaterialTheme.typography.bodyMedium)
            Text(
                (if (u.ambientes.isEmpty()) "Sin empresas" else u.ambientes.joinToString(", ") { it.coEmpresa }) +
                    (u.ultimoAcceso?.let { " · último acceso " + Formato.fecha(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Formulario de usuario nuevo. La clave se escribe dos veces y no se muestra. */
@Composable
private fun DialogoUsuarioNuevo(
    creando: Boolean,
    error: String?,
    alGuardar: (String, String, String, String, String, Boolean) -> Unit,
    alCancelar: () -> Unit
) {
    var usuario by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var profit by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Nuevo usuario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(usuario, { if (it.length <= 30) usuario = it.uppercase() }, singleLine = true,
                    label = { Text("Usuario (para entrar)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nombre, { if (it.length <= 100) nombre = it }, singleLine = true,
                    label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(profit, { if (it.length <= 10) profit = it.uppercase() }, singleLine = true,
                    label = { Text("Usuario de Profit (opcional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(clave, { clave = it }, singleLine = true, label = { Text("Clave") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repetida, { repetida = it }, singleLine = true, label = { Text("Repita la clave") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = admin, onCheckedChange = { admin = it })
                    Text("Es administrador")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(usuario, nombre, profit, clave, repetida, admin) }, enabled = !creando) {
                Text(if (creando) "Guardando..." else "Crear")
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}
