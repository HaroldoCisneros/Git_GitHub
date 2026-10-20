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
import com.siguec.puraceramica.core.sesion.HuellaGuardada
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
    val tieneHuella: Boolean = false,       // El usuario escrito activó la huella en este teléfono
    val ofrecerHuella: Boolean = false,     // Entró con la clave: ¿activar la huella?
    val listo: Boolean = false              // true = ya entró y tiene empresa: ir al menú
)

/**
 * Lógica de la pantalla de entrada.
 * @param gestor dueño de la sesión (módulo :core:sesion)
 */
class LoginViewModel(private val gestor: GestorSesion) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoLogin(usuario = gestor.ultimoUsuario,
        tieneHuella = gestor.huella(gestor.ultimoUsuario) != null))
    val estado: StateFlow<EstadoLogin> = _estado.asStateFlow()

    /** La clave recién verificada, SOLO mientras se pregunta si activar la huella. */
    private var claveParaHuella: String? = null

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
        _estado.update {
            val usuario = texto.trim().uppercase()
            it.copy(usuario = usuario, mensajeError = null, tieneHuella = gestor.huella(usuario) != null)
        }

    fun alCambiarClave(texto: String) = _estado.update { it.copy(clave = texto, mensajeError = null) }

    // ---------- Botones ----------

    /**
     * Botón "Entrar" (con la clave escrita).
     * @param puedeHuella el teléfono tiene huella: si entra bien, se le ofrece activarla
     */
    fun entrar(puedeHuella: Boolean = false) = entrarCon(_estado.value.clave, porHuella = false, puedeHuella = puedeHuella)

    /** La huella abrió la clave guardada: se entra con ella (el servidor la revisa como siempre). */
    fun entrarConHuella(clave: String) = entrarCon(clave, porHuella = true, puedeHuella = false)

    private fun entrarCon(clave: String, porHuella: Boolean, puedeHuella: Boolean) {
        val actual = _estado.value
        val error = when {
            actual.sinConfigurar -> "Primero configure la conexión con el servidor (botón Configuración)"
            actual.usuario.isBlank() -> "Escriba su usuario"
            clave.isEmpty() -> "Escriba su clave"
            else -> null
        }
        if (error != null) {
            _estado.update { it.copy(mensajeError = error) }
            return
        }
        _estado.update { it.copy(cargando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            when (val r = gestor.entrar(actual.usuario, clave)) {
                is Resultado.Exito -> {
                    // La clave no se queda en la pantalla
                    _estado.update {
                        it.copy(clave = "", mensajeAviso = if (r.valor.sinConexion)
                            "Entró sin WiFi: puede contar; para sincronizar o enviar necesita el WiFi." else null)
                    }
                    if (!porHuella && puedeHuella && gestor.ofrecerHuella(actual.usuario)) {
                        // Antes de ir al menú se pregunta si quiere entrar con huella la próxima vez
                        claveParaHuella = clave
                        _estado.update { it.copy(cargando = false, ofrecerHuella = true) }
                    } else {
                        despuesDeEntrar()
                    }
                }
                is Resultado.Error -> {
                    // Con huella y "clave incorrecta": la clave cambió desde que se activó la huella
                    val mensaje = if (porHuella && r.tipo == TipoError.CLAVE && r.mensaje.contains("incorrectos")) {
                        gestor.guardarHuella(actual.usuario, null)
                        "Su clave cambió: entre con la clave nueva y vuelva a activar la huella."
                    } else r.mensaje
                    _estado.update { it.copy(cargando = false, clave = "", mensajeError = mensaje,
                        tieneHuella = gestor.huella(actual.usuario) != null) }
                }
            }
        }
    }

    // ---------- Huella ----------

    /** La clave cifrada del usuario escrito (para que la pantalla la abra con la huella). */
    fun huellaGuardada(): HuellaGuardada? = gestor.huella(_estado.value.usuario)

    /** La clave que se va a cifrar al activar la huella. */
    fun claveParaActivar(): String? = claveParaHuella

    /** La huella quedó activada: se guarda y se sigue al menú. */
    fun huellaActivada(huella: HuellaGuardada) {
        gestor.guardarHuella(_estado.value.usuario, huella)
        _estado.update { it.copy(tieneHuella = true, mensajeAviso = "Huella activada: la próxima vez puede entrar con ella.") }
        terminarOferta()
    }

    /** "Ahora no" (o no se pudo activar): sigue al menú. */
    fun terminarOferta() {
        claveParaHuella = null
        _estado.update { it.copy(ofrecerHuella = false) }
        despuesDeEntrar()
    }

    /** "No volver a preguntar". */
    fun noActivarHuella() {
        gestor.noOfrecerHuella(_estado.value.usuario)
        terminarOferta()
    }

    /** Botón "Quitar huella" de la entrada. */
    fun quitarHuella() {
        gestor.guardarHuella(_estado.value.usuario, null)
        _estado.update { it.copy(tieneHuella = false, mensajeAviso = "Se quitó la huella de ${it.usuario} en este teléfono.") }
    }

    /** Se agregó o borró una huella en el teléfono: la guardada ya no sirve. */
    fun huellaInvalidada() {
        gestor.guardarHuella(_estado.value.usuario, null)
        _estado.update { it.copy(tieneHuella = false,
            mensajeError = "Cambiaron las huellas del teléfono: entre con la clave y vuelva a activar la huella.") }
    }

    fun errorHuella(mensaje: String) = _estado.update { it.copy(mensajeError = mensaje) }

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
