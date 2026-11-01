package com.sighec.movilplus.ui.acceso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.SighecApp
import com.sighec.movilplus.seguridad.Biometria
import com.sighec.movilplus.seguridad.buscarActivity
import com.sighec.movilplus.ui.componentes.Campo
import com.sighec.movilplus.ui.componentes.aviso
import com.sighec.movilplus.ui.componentes.ejecutar
import com.sighec.movilplus.ui.componentes.repo

@Composable
private fun Encabezado(subtitulo: String) {
    Icon(Icons.Filled.Storefront, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Text("SighecMovilPlus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColumnaAcceso(contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) { contenido() }
}

@Composable
fun LoginScreen(onEntrar: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SighecApp
    val repo = repo()
    val scope = rememberCoroutineScope()
    var usuario by remember { mutableStateOf(app.prefs.ultimoUsuario) }
    var password by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }
    val usuarioHuella = remember { app.prefs.usuarioHuella }
    val huellaDisponible = remember { usuarioHuella != null && Biometria.disponible(context) }

    ColumnaAcceso {
        Encabezado("Administración de tu negocio")
        Campo(usuario, { usuario = it }, "Usuario")
        Campo(password, { password = it }, "Contraseña", password = true)
        Button(
            onClick = {
                procesando = true
                scope.ejecutar(context, alTerminar = { app.prefs.ultimoUsuario = usuario.trim(); onEntrar() }) {
                    try { repo.login(usuario, password) } finally { procesando = false }
                }
            },
            enabled = !procesando && usuario.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (procesando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Entrar")
        }
        if (huellaDisponible) {
            OutlinedButton(
                onClick = {
                    val activity = context.buscarActivity() ?: return@OutlinedButton
                    Biometria.autenticar(
                        activity, "Entrar como $usuarioHuella",
                        onExito = { scope.ejecutar(context, alTerminar = onEntrar) { repo.loginHuella(usuarioHuella!!) } },
                        onError = { aviso(context, it) }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Entrar con huella ($usuarioHuella)")
            }
        }
    }
}

/** Se muestra sólo la primera vez: crea el usuario administrador. */
@Composable
fun ConfiguracionInicialScreen(onListo: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }

    ColumnaAcceso {
        Encabezado("Configuración inicial")
        Text("Crea el usuario administrador. Toda la información se guarda únicamente en este teléfono.",
            style = MaterialTheme.typography.bodyMedium)
        Campo(nombre, { nombre = it }, "Nombre completo")
        Campo(usuario, { usuario = it }, "Usuario")
        Campo(password, { password = it }, "Contraseña", password = true)
        Campo(confirmar, { confirmar = it }, "Confirmar contraseña", password = true)
        Button(
            onClick = {
                if (password != confirmar) { aviso(context, "Las contraseñas no coinciden"); return@Button }
                scope.ejecutar(context, exito = "Administrador creado", alTerminar = onListo) {
                    repo.configuracionInicial(nombre, usuario, password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Crear administrador") }
    }
}
