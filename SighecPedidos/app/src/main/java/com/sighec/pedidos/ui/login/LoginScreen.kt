package com.sighec.pedidos.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sighec.pedidos.contenedor
import com.sighec.pedidos.ui.buscarActivity

@Composable
fun LoginScreen(onConfig: () -> Unit, onIngreso: () -> Unit) {
    val context = LocalContext.current
    val app = context.contenedor
    val vm: LoginViewModel = viewModel { LoginViewModel(app) }
    val activity = context.buscarActivity()

    LaunchedEffect(vm.ingresado) { if (vm.ingresado) onIngreso() }

    // Pantalla horizontal: marca a la izquierda, formulario a la derecha.
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0D47A1), Color(0xFF1E88E5)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(16.dp))
                Text("Sighec Pedidos", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Toma de pedidos conectada a Profit", color = Color.White.copy(alpha = 0.85f))
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            IconButton(onClick = onConfig, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Configuración")
            }
            Card(
                Modifier.width(420.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = vm.usuario,
                        onValueChange = { vm.usuario = it },
                        label = { Text("Usuario") },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        singleLine = true,
                        enabled = !vm.cargando,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = vm.clave,
                        onValueChange = { vm.clave = it },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        singleLine = true,
                        enabled = !vm.cargando,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.ingresarConClave() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Button(
                        onClick = { vm.ingresarConClave() },
                        enabled = !vm.cargando,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (vm.cargando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Entrar")
                    }

                    if (vm.puedeUsarHuella && activity != null) {
                        FilledTonalButton(
                            onClick = { vm.ingresarConHuella(activity) },
                            enabled = !vm.cargando,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.Fingerprint, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Entrar con huella (${vm.usuarioHuella})")
                        }
                    }
                }
            }
        }
    }

    if (vm.ofrecerHuella && activity != null) {
        AlertDialog(
            onDismissRequest = { vm.omitirHuella() },
            icon = { Icon(Icons.Filled.Fingerprint, null) },
            title = { Text("¿Activar ingreso con huella?") },
            text = { Text("La próxima vez podrá entrar solo con su huella digital en esta tablet.") },
            confirmButton = { Button(onClick = { vm.activarHuella(activity) }) { Text("Activar") } },
            dismissButton = { TextButton(onClick = { vm.omitirHuella() }) { Text("Ahora no") } },
        )
    }
}
