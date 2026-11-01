package com.sighec.movilplus.ui.usuarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.data.Usuario
import com.sighec.movilplus.seguridad.Modulo
import com.sighec.movilplus.ui.componentes.Campo
import com.sighec.movilplus.ui.componentes.ConfirmarDialogo
import com.sighec.movilplus.ui.componentes.Fila
import com.sighec.movilplus.ui.componentes.Formulario
import com.sighec.movilplus.ui.componentes.Interruptor
import com.sighec.movilplus.ui.componentes.PantallaBase
import com.sighec.movilplus.ui.componentes.Vacio
import com.sighec.movilplus.ui.componentes.aviso
import com.sighec.movilplus.ui.componentes.ejecutar
import com.sighec.movilplus.ui.componentes.repo

@Composable
fun UsuariosScreen(onAtras: () -> Unit, onAbrir: (Long) -> Unit) {
    val repo = repo()
    val usuarios by remember { repo.db.usuarios().observarTodos() }.collectAsState(emptyList())
    PantallaBase("Maestro de usuarios", onAtras, onAgregar = { onAbrir(0) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (usuarios.isEmpty()) item { Vacio("Sin usuarios") }
            items(usuarios, key = { it.id }) { u ->
                val opciones = if (u.esAdmin) "Todas las opciones" else "${Modulo.desdeTexto(u.permisos).size} opciones"
                Fila(
                    titulo = "${u.usuario} — ${u.nombre}",
                    subtitulo = listOfNotNull(
                        if (u.esAdmin) "Administrador" else null,
                        opciones,
                        if (u.permiteHuella) "Huella permitida" else null
                    ).joinToString(" · "),
                    derecha = if (u.activo) "Activo" else "Inactivo",
                    colorDerecha = if (u.activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    onClick = { onAbrir(u.id) }
                )
            }
        }
    }
}

@Composable
fun UsuarioFormScreen(id: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<Usuario?>(null) }
    var usuario by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var esAdmin by remember { mutableStateOf(false) }
    var activo by remember { mutableStateOf(true) }
    var permiteHuella by remember { mutableStateOf(true) }
    var permisos by remember { mutableStateOf(setOf<Modulo>()) }
    var confirmarBaja by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) repo.db.usuarios().porId(id)?.let { u ->
            original = u
            usuario = u.usuario; nombre = u.nombre; esAdmin = u.esAdmin; activo = u.activo
            permiteHuella = u.permiteHuella; permisos = Modulo.desdeTexto(u.permisos)
        }
    }

    PantallaBase(
        titulo = if (id == 0L) "Nuevo usuario" else "Editar usuario",
        onAtras = onAtras,
        acciones = {
            if (original != null) IconButton(onClick = { confirmarBaja = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
        }
    ) { padding ->
        Formulario(padding) {
            Campo(usuario, { usuario = it }, "Usuario")
            Campo(nombre, { nombre = it }, "Nombre")
            Campo(password, { password = it }, if (id == 0L) "Contraseña" else "Nueva contraseña (dejar vacío para no cambiar)", password = true)
            Campo(confirmar, { confirmar = it }, "Confirmar contraseña", password = true)
            Interruptor("Activo", activo, { activo = it })
            Interruptor("Permitir entrar con huella", permiteHuella, { permiteHuella = it })
            Interruptor("Administrador (ve todas las opciones)", esAdmin, { esAdmin = it })

            if (!esAdmin) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Opciones que puede ver", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { permisos = Modulo.entries.toSet() }) { Text("Todas") }
                    TextButton(onClick = { permisos = emptySet() }) { Text("Ninguna") }
                }
                Modulo.entries.groupBy { it.grupo }.forEach { (grupo, lista) ->
                    Text(grupo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    lista.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { permisos = if (m in permisos) permisos - m else permisos + m },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = m in permisos, onCheckedChange = { permisos = if (it) permisos + m else permisos - m })
                            Icon(m.icono, null, Modifier.padding(end = 8.dp))
                            Text(m.titulo)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (password != confirmar) { aviso(context, "Las contraseñas no coinciden"); return@Button }
                    val base = original ?: Usuario(usuario = "", nombre = "", passwordHash = "", salt = "")
                    val registro = base.copy(
                        usuario = usuario, nombre = nombre, esAdmin = esAdmin, activo = activo,
                        permiteHuella = permiteHuella,
                        permisos = Modulo.aTexto(if (esAdmin) Modulo.entries.toSet() else permisos)
                    )
                    scope.ejecutar(context, exito = "Usuario guardado", alTerminar = onAtras) {
                        repo.guardarUsuario(registro, password.ifBlank { null })
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }

    if (confirmarBaja) {
        ConfirmarDialogo("Eliminar usuario", "¿Eliminar al usuario '${original?.usuario}'?",
            onConfirmar = { original?.let { u -> scope.ejecutar(context, exito = "Usuario eliminado", alTerminar = onAtras) { repo.eliminarUsuario(u) } } },
            onCancelar = { confirmarBaja = false })
    }
}
