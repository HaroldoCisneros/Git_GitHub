package com.sighec.pedidos.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sighec.pedidos.Contenedor
import com.sighec.pedidos.data.Usuario
import com.sighec.pedidos.seguridad.NivelBiometria
import kotlinx.coroutines.launch

class LoginViewModel(private val app: Contenedor) : ViewModel() {

    var usuario by mutableStateOf(app.credenciales.usuario.orEmpty())
    var clave by mutableStateOf("")
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Tras un login con clave: preguntar si activa la huella. */
    var ofrecerHuella by mutableStateOf(false)
        private set
    /** Login completado: navegar al menú. */
    var ingresado by mutableStateOf(false)
        private set

    private var claveValidada: String? = null

    val nivelBiometria: NivelBiometria get() = app.biometria.nivelDisponible()

    val puedeUsarHuella: Boolean
        get() = app.credenciales.hayCredencial() && nivelBiometria != NivelBiometria.NINGUNO

    val usuarioHuella: String? get() = app.credenciales.usuario

    fun ingresarConClave() {
        if (usuario.isBlank() || clave.isBlank()) {
            error = "Ingrese usuario y contraseña"; return
        }
        validar(usuario, clave) { u ->
            val yaTieneHuella = app.credenciales.hayCredencial() &&
                app.credenciales.usuario.equals(u.usuario, ignoreCase = true)
            if (nivelBiometria != NivelBiometria.NINGUNO && !yaTieneHuella) {
                claveValidada = clave
                ofrecerHuella = true
            } else {
                ingresado = true
            }
        }
    }

    fun activarHuella(activity: FragmentActivity) {
        val nivel = nivelBiometria
        val u = app.usuarioActual ?: return
        val claveOk = claveValidada ?: return
        val cipher = app.biometria.cipherCifrar(nivel)
        if (cipher == null) {
            error = "No se pudo preparar la huella, intente de nuevo"; omitirHuella(); return
        }
        app.biometria.autenticar(
            activity, "Activar ingreso con huella", nivel, cipher,
            onExito = { c ->
                runCatching { app.credenciales.guardar(u.usuario, nivel, c, claveOk) }
                    .onFailure { error = "No se pudo guardar la huella" }
                omitirHuella()
            },
            onError = { omitirHuella() },
        )
    }

    fun omitirHuella() {
        ofrecerHuella = false
        claveValidada = null
        ingresado = true
    }

    fun ingresarConHuella(activity: FragmentActivity) {
        val nivel = app.credenciales.nivel ?: return
        val iv = app.credenciales.iv ?: return
        val usuarioGuardado = app.credenciales.usuario ?: return
        val cipher = app.biometria.cipherDescifrar(nivel, iv)
        if (cipher == null) {
            app.credenciales.borrar()
            error = "Las huellas del equipo cambiaron. Ingrese con su contraseña para activarla de nuevo."
            return
        }
        error = null
        app.biometria.autenticar(
            activity, "Ingresar con huella", nivel, cipher,
            onExito = { c ->
                val claveGuardada = runCatching { app.credenciales.descifrar(c) }.getOrNull()
                if (claveGuardada == null) {
                    app.credenciales.borrar()
                    error = "No se pudo leer la huella guardada. Ingrese con su contraseña."
                } else {
                    usuario = usuarioGuardado
                    validar(usuarioGuardado, claveGuardada, borrarHuellaSiFalla = true) { ingresado = true }
                }
            },
            onError = { msg -> error = msg.takeUnless { it.contains("contraseña", true) } },
        )
    }

    private fun validar(
        u: String,
        c: String,
        borrarHuellaSiFalla: Boolean = false,
        onOk: (Usuario) -> Unit,
    ) {
        cargando = true; error = null
        viewModelScope.launch {
            runCatching { app.usuarios.login(u, c) }
                .onSuccess { usr ->
                    app.usuarioActual = usr
                    clave = ""
                    onOk(usr)
                }
                .onFailure { e ->
                    // Si la clave cambió en SQL, la huella guardada ya no sirve.
                    if (borrarHuellaSiFalla && e is IllegalArgumentException) app.credenciales.borrar()
                    error = e.message ?: "Error al ingresar"
                }
            cargando = false
        }
    }
}
