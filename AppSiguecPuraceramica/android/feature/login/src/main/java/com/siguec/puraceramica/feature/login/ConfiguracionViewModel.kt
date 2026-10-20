// MÓDULO :feature:login
package com.siguec.puraceramica.feature.login

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Conexión y sesión
import com.siguec.puraceramica.core.red.ConfigConexion
import com.siguec.puraceramica.core.red.MonedaDto
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.sesion.GestorSesion
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla Configuración. */
data class EstadoConfiguracion(
    val servidor: String = "",
    val puerto: String = "",
    val instancia: String = "",
    val usuario: String = "",
    val claveNueva: String = "",            // Vacío = se deja la clave guardada (nunca se muestra)
    val tieneClave: Boolean = false,        // Ya hay una clave de SQL guardada
    val baseApp: String = "",
    val baseProfit: String = "",            // Base de la empresa en Profit (ej. SAPURA_A)
    val coMone: String = "",                // Moneda del resultado de inventario (saMoneda de Profit)
    val monedas: List<MonedaDto> = emptyList(), // Las de Profit, después de "Buscar monedas"
    val probando: Boolean = false,
    val mensajeError: String? = null,
    val mensajeAviso: String? = null,
    val guardado: Boolean = false           // true = se guardó: volver
)

/**
 * Configuración de la conexión DIRECTA al SQL Server (por el WiFi de la empresa).
 * La clave de SQL guardada NUNCA se muestra: solo se puede escribir una nueva.
 */
class ConfiguracionViewModel(private val gestor: GestorSesion) : ViewModel() {

    private val _estado = MutableStateFlow(gestor.conexion.let {
        EstadoConfiguracion(servidor = it.servidor, puerto = it.puerto, instancia = it.instancia, usuario = it.usuario,
            tieneClave = it.clave.isNotEmpty(), baseApp = it.baseApp,
            baseProfit = it.baseProfit, coMone = it.coMone)
    })
    val estado: StateFlow<EstadoConfiguracion> = _estado.asStateFlow()

    fun alCambiarServidor(t: String) = _estado.update { it.copy(servidor = t.trim(), mensajeError = null, mensajeAviso = null) }
    fun alCambiarPuerto(t: String) = _estado.update { it.copy(puerto = t.filter(Char::isDigit).take(5), mensajeError = null) }
    fun alCambiarInstancia(t: String) = _estado.update { it.copy(instancia = t.trim().uppercase(), mensajeError = null) }
    fun alCambiarUsuario(t: String) = _estado.update { it.copy(usuario = t.trim(), mensajeError = null) }
    fun alCambiarClave(t: String) = _estado.update { it.copy(claveNueva = t, mensajeError = null) }
    fun alCambiarBase(t: String) = _estado.update { it.copy(baseApp = t.trim().uppercase(), mensajeError = null) }
    // Otra base de Profit: la lista de monedas era de la anterior
    fun alCambiarBaseProfit(t: String) =
        _estado.update { it.copy(baseProfit = t.trim().uppercase(), monedas = emptyList(), mensajeError = null) }
    fun elegirMoneda(m: MonedaDto) = _estado.update { it.copy(coMone = m.coMone, mensajeError = null) }

    /** Lo escrito como [ConfigConexion] (sin clave nueva = la guardada). */
    private fun armar(): ConfigConexion {
        val e = _estado.value
        return ConfigConexion(servidor = e.servidor, puerto = e.puerto, instancia = e.instancia, usuario = e.usuario,
            clave = e.claveNueva.ifEmpty { gestor.conexion.clave }, baseApp = e.baseApp,
            baseProfit = e.baseProfit, coMone = e.coMone)
    }

    /** Botón "Probar conexión": se conecta con lo escrito (sin guardarlo). */
    fun probar() {
        val config = armar()
        config.validar()?.let { error -> _estado.update { it.copy(mensajeError = error) }; return }
        _estado.update { it.copy(probando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            val r = gestor.probarConexion(config)
            _estado.update {
                when (r) {
                    is Resultado.Exito -> it.copy(probando = false, mensajeAviso = r.valor)
                    is Resultado.Error -> it.copy(probando = false, mensajeError = r.mensaje)
                }
            }
        }
    }

    /** Botón "Buscar monedas en Profit": lista saMoneda (con lo escrito, sin guardarlo). */
    fun buscarMonedas() {
        val config = armar()
        config.validar()?.let { error -> _estado.update { it.copy(mensajeError = error) }; return }
        _estado.update { it.copy(probando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            val r = gestor.monedas(config)
            _estado.update {
                when (r) {
                    is Resultado.Exito -> it.copy(probando = false, monedas = r.valor)
                    is Resultado.Error -> it.copy(probando = false, mensajeError = r.mensaje)
                }
            }
        }
    }

    /**
     * Botón "Guardar": primero verifica la conexión con las dos bases (y registra la empresa
     * en la App si falta); solo si todo está bien, guarda.
     */
    fun guardar() {
        val config = armar()
        config.validar()?.let { error -> _estado.update { it.copy(mensajeError = error) }; return }
        _estado.update { it.copy(probando = true, mensajeError = null, mensajeAviso = null) }
        viewModelScope.launch {
            when (val r = gestor.probarConexion(config)) {
                is Resultado.Exito -> {
                    // Otra empresa con la sesión abierta: el gestor cierra la sesión (se vuelve a la entrada)
                    gestor.guardarConexion(config)
                    _estado.update { it.copy(probando = false, guardado = true) }
                }
                is Resultado.Error -> _estado.update { it.copy(probando = false, mensajeError = "No se guardó: ${r.mensaje}") }
            }
        }
    }
}
