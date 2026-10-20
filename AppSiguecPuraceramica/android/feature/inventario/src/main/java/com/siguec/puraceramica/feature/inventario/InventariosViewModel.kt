// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Modelos, resultado y sesión
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.sesion.GestorSesion
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Prueba de conexión antes de enviar ("Enviar a Profit" queda apagado hasta que dé LISTA). */
enum class PruebaConexion { SIN_PROBAR, PROBANDO, LISTA, FALLO }

/** Estado de la pantalla de inventarios. */
data class EstadoInventarios(
    val inventarios: List<InventarioDto> = emptyList(), // Los del teléfono
    val cargando: Boolean = true,
    val trabajando: Boolean = false,                    // Enviando (con WiFi)
    val conexion: PruebaConexion = PruebaConexion.SIN_PROBAR,
    val mensajeConexion: String? = null,                // Por qué falló la prueba
    val aviso: String? = null
)

/**
 * Lógica de "Inventarios":
 *  - Lista: los inventarios ABIERTOS en el teléfono (sin WiFi). Abrir uno nuevo es
 *    otra pantalla (PantallaAbrirInventario).
 *  - Enviar a Profit (WiFi): lo contado en el teléfono va a la toma física de Profit
 *    (Profit la cierra) y aquí queda cerrado.
 *
 * @param gestor sesión: base del teléfono y conexión al SQL Server
 * @param coEmpresa empresa elegida
 */
class InventariosViewModel(private val gestor: GestorSesion, private val coEmpresa: String) : ViewModel() {

    private val local = gestor.local.inventarios

    private val _estado = MutableStateFlow(EstadoInventarios())
    val estado: StateFlow<EstadoInventarios> = _estado.asStateFlow()

    init {
        cargar()
    }

    /** Los inventarios del teléfono. */
    fun cargar() {
        viewModelScope.launch {
            val lista = local.lista(coEmpresa)
            _estado.update { it.copy(cargando = false, inventarios = lista) }
        }
    }

    /** Botón "Probar conexión": hasta que responda el servidor no se puede enviar. */
    fun probarConexion() {
        if (_estado.value.conexion == PruebaConexion.PROBANDO) return
        _estado.update { it.copy(conexion = PruebaConexion.PROBANDO, mensajeConexion = null) }
        viewModelScope.launch {
            val r = gestor.probarServidor(coEmpresa)
            _estado.update {
                when (r) {
                    is Resultado.Exito -> it.copy(conexion = PruebaConexion.LISTA)
                    is Resultado.Error -> it.copy(conexion = PruebaConexion.FALLO, mensajeConexion = r.mensaje)
                }
            }
        }
    }

    /**
     * "Enviar a Profit": manda TODOS los conteos de este teléfono en esa toma.
     * Si se corta el WiFi a mitad, se puede volver a intentar: no se duplica.
     */
    fun enviar(inventario: InventarioDto) {
        val usuario = gestor.usuario ?: return
        if (_estado.value.trabajando) return
        // Solo después de una prueba de conexión correcta; cada envío pide una prueba nueva
        if (_estado.value.conexion != PruebaConexion.LISTA) return
        _estado.update { it.copy(trabajando = true, conexion = PruebaConexion.SIN_PROBAR) }
        viewModelScope.launch {
            // Red de seguridad: un error inesperado se muestra como aviso, nunca cierra la App
            // (lo contado sigue en el teléfono y se puede volver a enviar)
            try {
                val (renglones, clave) = local.paraEnviar(inventario.id)
                when (val r = gestor.inventarioProfit().enviar(usuario, coEmpresa, inventario, renglones, clave)) {
                    is Resultado.Exito -> {
                        local.marcarEnviado(inventario.id, usuario.usuario)
                        if (gestor.ultimoInventario() == inventario.id) gestor.recordarInventario(null)
                        _estado.update {
                            it.copy(trabajando = false, aviso = "Inventario ${inventario.numero} enviado a Profit " +
                                "(resultado ${r.valor.numResinv}): ${r.valor.articulos} artículos, ${r.valor.conteos} conteos. " +
                                "Procéselo en Profit.")
                        }
                        cargar()
                        // Ya hay conexión: se aprovecha para traer los permisos actuales del usuario
                        gestor.actualizarSesion()
                    }
                    is Resultado.Error -> _estado.update { it.copy(trabajando = false, aviso = r.mensaje) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(trabajando = false,
                    aviso = "No se pudo enviar: ${e.message ?: e.javaClass.simpleName}. Lo contado sigue en el teléfono.") }
            }
        }
    }

    /** Quita del teléfono una toma ya enviada (para no acumular). */
    fun borrarEnviado(inventario: InventarioDto) {
        viewModelScope.launch {
            local.borrarEnviado(inventario.id)
            cargar()
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
