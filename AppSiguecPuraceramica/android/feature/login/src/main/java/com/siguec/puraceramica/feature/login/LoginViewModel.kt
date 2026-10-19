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
import com.siguec.puraceramica.core.sesion.GestorSesion
// Estado observable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué parte de la entrada está el usuario. */
enum class PasoLogin {
    CREDENCIALES, // Escribiendo usuario y clave
    EMPRESA       // Eligiendo la empresa (tiene acceso a varias)
}

/**
 * Todo lo que la pantalla necesita para dibujarse.
 * Cada cambio crea una copia nueva y la pantalla se redibuja.
 */
data class EstadoLogin(
    val paso: PasoLogin = PasoLogin.CREDENCIALES,
    val servidor: String = "",              // Servidor configurado (solo se muestra)
    val sinConfigurar: Boolean = false,     // true = hay que llenar Configuración primero
    val usuario: String = "",
    val clave: String = "",
    val cargando: Boolean = false,          // true = verificando la clave
    val mensajeError: String? = null,       // Error en rojo
    val mensajeAviso: String? = null,       // Aviso (entró sin WiFi...)
    val ambientes: List<AmbienteDto> = emptyList(), // Empresas para elegir
    val listo: Boolean = false              // true = ya entró y tiene empresa: ir al menú
)

/**
 * Lógica de la pantalla de entrada.
 * @param gestor dueño de la sesión (módulo :core:sesion)
 */
class LoginViewModel(private val gestor: GestorSesion) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoLogin(usuario = gestor.ultimoUsuario))
    val estado: StateFlow<EstadoLogin> = _estado.asStateFlow()

    init {
        // Si quedó una sesión abierta (aunque no haya WiFi) se entra directo
        if (gestor.restaurar() != null) despuesDeEntrar() else alVolverDeConfiguracion()
    }

    /** Al abrir la pantalla o volver de Configuración: se refresca el servidor configurado. */
    fun alVolverDeConfiguracion() = _estado.update {
        it.copy(servidor = gestor.conexion.servidor, sinConfigurar = !gestor.conexionConfigurada)
    }

    // ---------- Lo que escribe el usuario ----------

    fun alCambiarUsuario(texto: String) =
        _estado.update { it.copy(usuario = texto.trim().uppercase(), mensajeError = null) }

    fun alCambiarClave(texto: String) = _estado.update { it.copy(clave = texto, mensajeError = null) }

    // ---------- Botones ----------

    /** Botón "Entrar". */
    fun entrar() {
        val actual = _estado.value
        val error = when {
            actual.sinConfigurar -> "Primero configure la conexión con el servidor (botón Configuración)"
            actual.usuario.isBlank() -> "Escriba su usuario"
            actual.clave.isEmpty() -> "Escriba su clave"
            else -> null
        }
        if (error != null) {
            _estado.update { it.copy(mensajeError = error) }
            return
        }
        _estado.update { it.copy(cargando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            when (val r = gestor.entrar(actual.usuario, actual.clave)) {
                is Resultado.Exito -> {
                    // La clave no se queda en memoria
                    _estado.update {
                        it.copy(clave = "", mensajeAviso = if (r.valor.sinConexion)
                            "Entró sin WiFi: puede contar; para sincronizar o enviar necesita el WiFi." else null)
                    }
                    despuesDeEntrar()
                }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, clave = "", mensajeError = r.mensaje) }
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
        gestor.salir()
        _estado.update { it.copy(paso = PasoLogin.CREDENCIALES, ambientes = emptyList()) }
    }
}
