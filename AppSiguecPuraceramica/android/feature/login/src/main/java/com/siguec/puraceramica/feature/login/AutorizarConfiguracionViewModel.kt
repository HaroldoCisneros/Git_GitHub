// MÓDULO :feature:login
package com.siguec.puraceramica.feature.login

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Sesión
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.sesion.GestorSesion
import com.siguec.puraceramica.core.sesion.HuellaGuardada
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla "Autorizar Configuración". */
data class EstadoAutorizar(
    val usuario: String = "",
    val clave: String = "",
    val tieneHuella: Boolean = false,      // Ese usuario activó la huella en este teléfono
    val verificando: Boolean = false,
    val mensajeError: String? = null,
    val autorizado: Boolean = false        // true = abrir Configuración
)

/**
 * Antes de abrir Configuración (ya configurada): un usuario con permiso "Puede cambiar la
 * Configuración" (o administrador) se identifica con su huella o su clave.
 *
 * @param usuarioInicial el usuario que está usando la App, o el último que entró
 */
class AutorizarConfiguracionViewModel(private val gestor: GestorSesion, usuarioInicial: String) : ViewModel() {

    private val _estado = MutableStateFlow(
        EstadoAutorizar(usuario = usuarioInicial, tieneHuella = gestor.huella(usuarioInicial) != null)
    )
    val estado: StateFlow<EstadoAutorizar> = _estado.asStateFlow()

    fun alCambiarUsuario(t: String) = _estado.update {
        val usuario = t.trim().uppercase()
        it.copy(usuario = usuario, tieneHuella = gestor.huella(usuario) != null, mensajeError = null)
    }

    fun alCambiarClave(t: String) = _estado.update { it.copy(clave = t, mensajeError = null) }

    /** La clave cifrada con la huella del usuario escrito. */
    fun huellaGuardada(): HuellaGuardada? = gestor.huella(_estado.value.usuario)

    /** Botón "Autorizar" (clave escrita). */
    fun autorizar() = autorizarCon(_estado.value.clave)

    /** La huella abrió la clave guardada. */
    fun autorizarConHuella(clave: String) = autorizarCon(clave)

    private fun autorizarCon(clave: String) {
        if (_estado.value.verificando) return
        _estado.update { it.copy(verificando = true, mensajeError = null) }
        viewModelScope.launch {
            when (val r = gestor.autorizarConfiguracion(_estado.value.usuario, clave)) {
                is Resultado.Exito -> _estado.update { it.copy(verificando = false, clave = "", autorizado = true) }
                is Resultado.Error -> _estado.update { it.copy(verificando = false, clave = "", mensajeError = r.mensaje) }
            }
        }
    }

    fun errorHuella(mensaje: String) = _estado.update { it.copy(mensajeError = mensaje) }

    /** Cambiaron las huellas del teléfono: la guardada ya no sirve. */
    fun huellaInvalidada() {
        gestor.guardarHuella(_estado.value.usuario, null)
        _estado.update { it.copy(tieneHuella = false,
            mensajeError = "Cambiaron las huellas del teléfono: use la clave (y vuelva a activar la huella al entrar).") }
    }
}
