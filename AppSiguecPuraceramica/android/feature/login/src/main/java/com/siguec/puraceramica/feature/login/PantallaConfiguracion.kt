// MÓDULO :feature:login
package com.siguec.puraceramica.feature.login

// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
// Componentes de Material 3
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
// Modificadores y teclado
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Sesión y componentes compartidos
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.tema.ColoresMarca

/**
 * Pantalla "Configuración": datos de la conexión DIRECTA al SQL Server de la
 * empresa por el WiFi. Se abre con el botón Configuración de la entrada (y del menú).
 *
 *  - Servidor: IP o nombre del equipo (ej. 192.168.1.10).
 *  - Puerto (1433) o instancia (ej. SQLEXPRESS).
 *  - Usuario y clave de SQL Server de la App (sql/03_permisos_usuario_sql.sql).
 *  - Base de la App (SIGUEC_PURACERAMICA).
 * "Probar conexión" se conecta con lo escrito sin guardarlo.
 */
@Composable
fun PantallaConfiguracion(gestor: GestorSesion, alVolver: () -> Unit) {
    val vm: ConfiguracionViewModel = viewModel(factory = viewModelFactory { initializer { ConfiguracionViewModel(gestor) } })
    val estado by vm.estado.collectAsStateWithLifecycle()

    // Guardado: se vuelve a la pantalla anterior
    LaunchedEffect(estado.guardado) { if (estado.guardado) alVolver() }

    Scaffold(topBar = { BarraSuperior(titulo = "Configuración", subtitulo = "Conexión al SQL Server", alVolver = alVolver) }) { m ->
        Column(
            Modifier.fillMaxSize().padding(m).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("El teléfono se conecta directo al SQL Server por el WiFi de la empresa. Solo se usa para entrar, " +
                "sincronizar artículos, traer los inventarios abiertos y enviar el conteo.",
                style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(estado.servidor, vm::alCambiarServidor, label = { Text("Servidor (IP o nombre)") },
                placeholder = { Text("192.168.1.10") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(estado.puerto, vm::alCambiarPuerto, label = { Text("Puerto") }, placeholder = { Text("1433") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f))
                OutlinedTextField(estado.instancia, vm::alCambiarInstancia, label = { Text("Instancia (opcional)") },
                    placeholder = { Text("SQLEXPRESS") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f))
            }
            OutlinedTextField(estado.usuario, vm::alCambiarUsuario, label = { Text("Usuario de SQL Server") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            // La clave guardada nunca se muestra: vacío = se deja la que está
            OutlinedTextField(estado.claveNueva, vm::alCambiarClave, label = { Text("Clave de SQL Server") },
                placeholder = { Text(if (estado.tieneClave) "•••••• (déjela vacía para no cambiarla)" else "") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(estado.baseApp, vm::alCambiarBase, label = { Text("Base de la App") },
                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth())

            estado.mensajeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            estado.mensajeAviso?.let { Text(it, color = ColoresMarca.Verde) }

            OutlinedButton(onClick = vm::probar, enabled = !estado.probando, modifier = Modifier.fillMaxWidth()) {
                if (estado.probando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Probar conexión")
            }
            Button(onClick = vm::guardar, enabled = !estado.probando, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Guardar")
            }
        }
    }
}
