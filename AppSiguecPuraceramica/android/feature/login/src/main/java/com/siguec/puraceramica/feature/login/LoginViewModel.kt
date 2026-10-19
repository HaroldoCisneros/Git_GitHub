// MÓDULO :feature:login
// Paquete de la pantalla de entrada
package com.siguec.puraceramica.feature.login

// Clase base de los ViewModel (guardan el estado de una pantalla)
import androidx.lifecycle.ViewModel
// Corrutinas atadas al ViewModel (se cancelan solas al cerrar la pantalla)
import androidx.lifecycle.viewModelScope
// Piezas de la red y de la sesión
import com.siguec.puraceramica.core.red.AmbienteDto
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.TipoError
import com.siguec.puraceramica.core.sesion.GestorSesion
// Estado observable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué parte de la entrada está el usuario. */
enum class PasoLogin {
    INICIANDO,    // Revisando si el token guardado sigue vigente
    CREDENCIALES, // Escribiendo servidor, usuario y clave
    EMPRESA       // Eligiendo la empresa (tiene acceso a varias)
}

/**
 * Todo lo que la pantalla necesita para dibujarse.
 * Cada cambio crea una copia nueva y la pantalla se redibuja.
 */
data class EstadoLogin(
    val paso: PasoLogin = PasoLogin.INICIANDO,
    val servidor: String = "",              // Dirección del servidor
    val verServidor: Boolean = false,       // true = se muestra el campo del servidor
    val usuario: String = "",
    val clave: String = "",
    val cargando: Boolean = false,          // true = esperando al servidor
    val mensajeError: String? = null,       // Error en rojo
    val mensajeAviso: String? = null,       // Aviso (sesión vencida, servidor probado...)
    val ambientes: List<AmbienteDto> = emptyList(), // Empresas para elegir
    val listo: Boolean = false              // true = ya entró y tiene empresa: ir al menú
)

/**
 * Lógica de la pantalla de entrada.
 * @param gestor dueño de la sesión (módulo :core:sesion)
 */
class LoginViewModel(private val gestor: GestorSesion) : ViewModel() {

    private val _estado = MutableStateFlow(
        EstadoLogin(
            servidor = gestor.urlServidor,
            usuario = gestor.ultimoUsuario,
            // La primera vez no hay servidor guardado: se muestra el campo
            verServidor = gestor.urlServidor.isBlank()
        )
    )
    val estado: StateFlow<EstadoLogin> = _estado.asStateFlow()

    init {
        // Si se llegó aquí porque el servidor rechazó el token, se avisa
        if (gestor.sesionVencida.value) {
            _estado.update { it.copy(mensajeAviso = "Su sesión venció. Entre de nuevo con su clave.") }
            gestor.avisoVencidaMostrado()
        }
        restaurar()
    }

    /** Si hay token guardado y sigue vigente, entra sin pedir la clave. */
    private fun restaurar() {
        viewModelScope.launch {
            when (val r = gestor.restaurar()) {
                null -> _estado.update { it.copy(paso = PasoLogin.CREDENCIALES) } // No había sesión
                is Resultado.Exito -> despuesDeEntrar()
                is Resultado.Error -> _estado.update {
                    it.copy(
                        paso = PasoLogin.CREDENCIALES,
                        // Sin internet se explica; si el token venció, solo se pide la clave
                        mensajeError = if (r.tipo == TipoError.RED) r.mensaje else null
                    )
                }
            }
        }
    }

    // ---------- Lo que escribe el usuario ----------

    fun alCambiarServidor(texto: String) =
        _estado.update { it.copy(servidor = texto.trim(), mensajeError = null, mensajeAviso = null) }

    fun alCambiarUsuario(texto: String) =
        _estado.update { it.copy(usuario = texto.trim().uppercase(), mensajeError = null) }

    fun alCambiarClave(texto: String) = _estado.update { it.copy(clave = texto, mensajeError = null) }

    fun alternarServidor() = _estado.update { it.copy(verServidor = !it.verServidor) }

    // ---------- Botones ----------

    /** Botón "Probar": ¿responde el servidor en esa dirección? */
    fun probarServidor() {
        val servidor = _estado.value.servidor
        if (servidor.isBlank()) {
            _estado.update { it.copy(mensajeError = "Escriba la dirección del servidor") }
            return
        }
        _estado.update { it.copy(cargando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            val r = gestor.probarServidor(servidor)
            _estado.update {
                when (r) {
                    is Resultado.Exito -> it.copy(cargando = false,
                        mensajeAviso = "Conexión correcta con ${r.valor.aplicacion} ${r.valor.version}")
                    is Resultado.Error -> it.copy(cargando = false, mensajeError = r.mensaje)
                }
            }
        }
    }

    /** Botón "Entrar". */
    fun entrar() {
        val actual = _estado.value
        val error = when {
            actual.servidor.isBlank() -> "Escriba la dirección del servidor"
            actual.usuario.isBlank() -> "Escriba su usuario"
            actual.clave.isEmpty() -> "Escriba su clave"
            else -> null
        }
        if (error != null) {
            _estado.update { it.copy(mensajeError = error, verServidor = it.verServidor || it.servidor.isBlank()) }
            return
        }
        _estado.update { it.copy(cargando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            when (val r = gestor.entrar(actual.servidor, actual.usuario, actual.clave)) {
                is Resultado.Exito -> {
                    // La clave no se queda en memoria
                    _estado.update { it.copy(clave = "") }
                    despuesDeEntrar()
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, clave = "", mensajeError = r.mensaje)
                }
            }
        }
    }

    /** Con una sola empresa va directo al menú; con varias, las muestra para elegir. */
    private fun despuesDeEntrar() {
        val sesion = gestor.sesion.value ?: return
        _estado.update {
            if (gestor.ambiente.value != null) {
                it.copy(cargando = false, listo = true)
            } else {
                it.copy(cargando = false, paso = PasoLogin.EMPRESA, ambientes = sesion.ambientes)
            }
        }
    }

    /** Tocó una empresa de la lista. */
    fun elegirEmpresa(ambiente: AmbienteDto) {
        gestor.elegirEmpresa(ambiente)
        _estado.update { it.copy(listo = true) }
    }

    /** "Otro usuario" en el paso de elegir empresa: vuelve a pedir usuario y clave. */
    fun volverACredenciales() {
        viewModelScope.launch { gestor.salir() }
        _estado.update { it.copy(paso = PasoLogin.CREDENCIALES, ambientes = emptyList()) }
    }
}
