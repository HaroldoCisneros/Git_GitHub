// MÓDULO :feature:login
package com.siguec.puraceramica.feature.login

// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
// Íconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
// Componentes de Material 3
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// Estado
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
// Modificadores, teclado y contexto
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
// ViewModel
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Sesión, huella y componentes compartidos
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.huella.Huella
import com.siguec.puraceramica.core.ui.huella.ResultadoHuella
import com.siguec.puraceramica.core.ui.huella.SecretoHuella
import kotlinx.coroutines.launch

/**
 * "Autorizar Configuración": cuando la conexión ya está configurada, solo la cambian los
 * usuarios con permiso "Puede cambiar la Configuración" (o administradores), con su huella
 * o su clave. La primera vez (sin configurar) se entra directo a Configuración.
 *
 * @param usuarioInicial quien está usando la App (o el último que entró)
 * @param alAutorizado abrir Configuración
 */
@Composable
fun PantallaAutorizarConfiguracion(
    gestor: GestorSesion,
    usuarioInicial: String,
    alAutorizado: () -> Unit,
    alVolver: () -> Unit
) {
    val vm: AutorizarConfiguracionViewModel = viewModel(
        factory = viewModelFactory { initializer { AutorizarConfiguracionViewModel(gestor, usuarioInicial) } }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val actividad = contexto as FragmentActivity
    val puedeHuella = remember { Huella.disponible(contexto) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(estado.autorizado) { if (estado.autorizado) alAutorizado() }

    /** Pide la huella del usuario escrito y, si la pone, autoriza con su clave guardada. */
    fun usarHuella() {
        val guardada = vm.huellaGuardada() ?: return
        alcance.launch {
            when (val r = Huella.descifrar(actividad, estado.usuario, SecretoHuella(guardada.cifrado, guardada.iv),
                titulo = "Autorizar Configuración")) {
                is ResultadoHuella.Exito -> vm.autorizarConHuella(r.valor)
                ResultadoHuella.Invalidada -> vm.huellaInvalidada()
                is ResultadoHuella.Error -> vm.errorHuella(r.mensaje)
                ResultadoHuella.Cancelada -> Unit
            }
        }
    }

    Scaffold(topBar = { BarraSuperior(titulo = "Configuración", subtitulo = "Autorización", alVolver = alVolver) }) { m ->
        Column(
            Modifier.fillMaxSize().padding(m).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Solo los usuarios autorizados pueden cambiar la Configuración (servidor, bases de datos y " +
                "moneda). Identifíquese con su huella o su clave.", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(estado.usuario, vm::alCambiarUsuario, label = { Text("Usuario") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }, singleLine = true,
                enabled = !estado.verificando,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth())

            // ---------- Con huella (si ese usuario la activó en este teléfono) ----------
            if (estado.tieneHuella && puedeHuella) {
                Button(onClick = ::usarHuella, enabled = !estado.verificando, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Autorizar con huella")
                }
                Text("o con la clave:", style = MaterialTheme.typography.bodySmall)
            }

            // ---------- Con la clave ----------
            OutlinedTextField(estado.clave, vm::alCambiarClave, label = { Text("Clave") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }, singleLine = true,
                enabled = !estado.verificando, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())

            estado.mensajeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedButton(onClick = vm::autorizar, enabled = !estado.verificando, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (estado.verificando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Autorizar con la clave")
            }
            if (!puedeHuella) {
                Text("Este teléfono no tiene huellas registradas: se autoriza con la clave.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!estado.tieneHuella) {
                Text("Para autorizar con huella, el usuario debe activarla al entrar a la App en este teléfono.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
