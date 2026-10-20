// MÓDULO :feature:login
// Paquete de la pantalla de entrada
package com.siguec.puraceramica.feature.login

// Herramientas de diseño
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Desplazamiento si no cabe (teclado abierto)
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Opciones del teclado
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
// Componentes de Material 3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// Estado en Compose
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
// Alineación, modificadores y foco
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
// Teclado y contraseñas
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
// ViewModel y estado
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
// Sesión y componentes compartidos
import androidx.fragment.app.FragmentActivity
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.sesion.HuellaGuardada
import com.siguec.puraceramica.core.ui.huella.Huella
import com.siguec.puraceramica.core.ui.huella.ResultadoHuella
import com.siguec.puraceramica.core.ui.huella.SecretoHuella
import kotlinx.coroutines.launch
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.LogoPuraceramica
import com.siguec.puraceramica.core.ui.tema.ColoresMarca

/**
 * Pantalla de entrada de Conteo de Inventario Puraceramica.
 *  1. Al abrir: si quedó una sesión abierta, entra sola (aunque no haya WiFi).
 *  2. Si no: pide usuario y clave. Con WiFi se verifica en el SQL Server; sin WiFi,
 *     con la clave de la última vez que ese usuario entró en este teléfono.
 *  3. Si el usuario tiene varias empresas: las muestra para elegir.
 *  El botón Configuración (arriba y abajo) cambia los datos de la conexión.
 *
 * @param gestor dueño de la sesión (para crear el ViewModel)
 * @param alEntrar ya entró y eligió empresa: ir al menú
 * @param alConfigurar abre la Configuración de la conexión
 * @param alSalir ícono "Salir": cierra la App
 */
@Composable
fun PantallaLogin(gestor: GestorSesion, alEntrar: () -> Unit, alConfigurar: () -> Unit, alSalir: () -> Unit) {
    // Creamos (o recuperamos) el ViewModel pasándole el gestor
    val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(gestor) } })
    // Cada cambio de estado redibuja la pantalla
    val estado by vm.estado.collectAsStateWithLifecycle()

    // Cuando ya está listo, vamos al menú
    LaunchedEffect(estado.listo) { if (estado.listo) alEntrar() }

    // ---------- Entró con la clave: ¿activar la huella para la próxima vez? ----------
    if (estado.ofrecerHuella) {
        val actividad = LocalContext.current as FragmentActivity
        val alcance = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = vm::terminarOferta,
            icon = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
            title = { Text("¿Entrar con huella?") },
            text = { Text("La próxima vez, ${estado.usuario} puede entrar en este teléfono con su huella en vez de " +
                "escribir la clave. Actívela solo si el teléfono es suyo: sirve cualquier huella registrada en él.") },
            confirmButton = {
                TextButton(onClick = {
                    val clave = vm.claveParaActivar() ?: return@TextButton vm.terminarOferta()
                    alcance.launch {
                        when (val r = Huella.cifrar(actividad, estado.usuario, clave)) {
                            is ResultadoHuella.Exito -> vm.huellaActivada(HuellaGuardada(r.valor.cifrado, r.valor.iv))
                            is ResultadoHuella.Error -> { vm.errorHuella(r.mensaje); vm.terminarOferta() }
                            else -> vm.terminarOferta()
                        }
                    }
                }) { Text("Activar huella") }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = vm::terminarOferta) { Text("Ahora no") }
                    TextButton(onClick = vm::noActivarHuella) { Text("No volver a preguntar") }
                }
            }
        )
    }
    // Al volver de Configuración se refresca el servidor que se muestra
    LaunchedEffect(Unit) { vm.alVolverDeConfiguracion() }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = if (estado.paso == PasoLogin.EMPRESA) "Elija la empresa" else "Conteo de Inventario",
                subtitulo = "Puraceramica",
                acciones = {
                    // Configuración: datos de la conexión con el SQL Server
                    IconButton(onClick = alConfigurar) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configuración")
                    }
                    IconButton(onClick = alSalir) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir de la app")
                    }
                }
            )
        }
    ) { margenes ->
        // "when" = según el paso se dibuja una cosa u otra
        when (estado.paso) {
            PasoLogin.EMPRESA -> Column(Modifier.fillMaxSize().padding(margenes).padding(16.dp)) {
                Text("Hola, ${estado.usuario}. ¿Con qué empresa va a trabajar?",
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                ListaEmpresas(estado.ambientes, actual = null, alElegir = vm::elegirEmpresa,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = vm::volverACredenciales, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Entrar con otro usuario")
                }
            }
            PasoLogin.CREDENCIALES -> Credenciales(estado, vm, alConfigurar, Modifier.padding(margenes))
        }
    }
}

/** Formulario de usuario y clave, y el botón de Configuración de la conexión. */
@Composable
private fun Credenciales(estado: EstadoLogin, vm: LoginViewModel, alConfigurar: () -> Unit, modifier: Modifier) {
    // Controlador del foco (saltar de campo y cerrar el teclado)
    val foco = LocalFocusManager.current
    // ¿Se muestra la clave? (sobrevive a girar el teléfono)
    var verClave by rememberSaveable { mutableStateOf(false) }
    // Huella: la ventana de Android necesita la Activity; ¿el teléfono tiene huella registrada?
    val contexto = LocalContext.current
    val actividad = contexto as FragmentActivity
    val puedeHuella = remember { Huella.disponible(contexto) }
    val alcance = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()                          // Espacio para el teclado
            .verticalScroll(rememberScrollState()) // Se desplaza si no cabe
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo de Puracerámica con el nombre de la App
        LogoPuraceramica(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Toma de inventario con Profit Plus 2K12", style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        // ---------- Sin configurar: lo primero es la conexión ----------
        if (estado.sinConfigurar) {
            Text("Antes de entrar, configure la conexión con el servidor SQL de la empresa.",
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Usuario ----------
        OutlinedTextField(
            value = estado.usuario,
            onValueChange = vm::alCambiarUsuario,
            label = { Text("Usuario") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            enabled = !estado.cargando,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters, // Los usuarios van en mayúsculas
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { foco.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        // ---------- Clave ----------
        OutlinedTextField(
            value = estado.clave,
            onValueChange = vm::alCambiarClave,
            label = { Text("Clave") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                // El "ojo" muestra u oculta la clave
                IconButton(onClick = { verClave = !verClave }) {
                    Icon(
                        if (verClave) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (verClave) "Ocultar clave" else "Mostrar clave"
                    )
                }
            },
            visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !estado.cargando,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { foco.clearFocus(); vm.entrar(puedeHuella) }),
            modifier = Modifier.fillMaxWidth()
        )

        // ---------- Mensajes ----------
        estado.mensajeError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center)
        }
        estado.mensajeAviso?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = ColoresMarca.Verde, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))

        // ---------- Botón Entrar ----------
        Button(
            onClick = { foco.clearFocus(); vm.entrar(puedeHuella) },
            enabled = !estado.cargando,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (estado.cargando) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("Entrar")
            }
        }

        // ---------- Entrar con huella (si este usuario la activó en este teléfono) ----------
        if (estado.tieneHuella && puedeHuella) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    foco.clearFocus()
                    val guardada = vm.huellaGuardada() ?: return@OutlinedButton
                    alcance.launch {
                        when (val r = Huella.descifrar(actividad, estado.usuario,
                            SecretoHuella(guardada.cifrado, guardada.iv))) {
                            is ResultadoHuella.Exito -> vm.entrarConHuella(r.valor)
                            ResultadoHuella.Invalidada -> vm.huellaInvalidada()
                            is ResultadoHuella.Error -> vm.errorHuella(r.mensaje)
                            ResultadoHuella.Cancelada -> Unit // Escribirá la clave
                        }
                    }
                },
                enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Entrar con huella")
            }
            TextButton(onClick = { Huella.borrar(estado.usuario); vm.quitarHuella() }, enabled = !estado.cargando) {
                Text("Quitar la huella de ${estado.usuario}")
            }
        }

        // ---------- Configuración de la conexión (siempre a la vista) ----------
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = alConfigurar, enabled = !estado.cargando) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (estado.sinConfigurar) "Configuración" else "Configuración · ${estado.servidor}")
        }
    }
}
