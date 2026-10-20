// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Modelos, resultado y sesión
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.TomaProfitDto
import com.siguec.puraceramica.core.sesion.GestorSesion
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "Abrir inventario". */
data class EstadoAbrirInventario(
    val inventarios: List<TomaProfitDto> = emptyList(), // Sin procesar en Profit
    val yaAbiertos: Set<String> = emptySet(),           // co_invfisico ya abiertos en este teléfono
    val elegido: TomaProfitDto? = null,                 // El que se está viendo
    val cargando: Boolean = true,
    val mensajeError: String? = null,                   // No se pudo consultar Profit (sin WiFi...)
    val abriendo: Boolean = false,
    val abierto: Int? = null,                           // Id (en el teléfono) del que se acaba de abrir
    val aviso: String? = null
)

/**
 * "Abrir inventario" (con WiFi):
 *  1. Consulta en Profit los inventarios físicos sin procesar (saInventarioFisico, procesado = 0).
 *  2. El usuario elige uno y ve sus datos.
 *  3. "Abrir": se verifica en Profit que siga sin procesar, se guarda quién y cuándo
 *     (SIGUEC_PURACERAMICA.dbo.aperturas) y queda en el teléfono para contar sin WiFi.
 */
class AbrirInventarioViewModel(private val gestor: GestorSesion, private val coEmpresa: String) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoAbrirInventario())
    val estado: StateFlow<EstadoAbrirInventario> = _estado.asStateFlow()

    init {
        cargar()
    }

    /** Consulta los inventarios sin procesar en Profit. */
    fun cargar() {
        val usuario = gestor.usuario ?: return
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            val yaAbiertos = gestor.local.inventarios.lista(coEmpresa).map { it.numero }.toSet()
            when (val r = gestor.inventarioProfit().traerAbiertas(usuario, coEmpresa)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false, inventarios = r.valor, yaAbiertos = yaAbiertos) }
                    // Ya hay conexión: se aprovecha para traer los permisos actuales del usuario
                    gestor.actualizarSesion()
                }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }

    /** Tocó un inventario: se muestran sus datos. */
    fun elegir(toma: TomaProfitDto?) = _estado.update { it.copy(elegido = toma) }

    /** Botón "Abrir". */
    fun abrir() {
        val toma = _estado.value.elegido ?: return
        val usuario = gestor.usuario ?: return
        if (_estado.value.abriendo) return
        _estado.update { it.copy(abriendo = true) }
        viewModelScope.launch {
            // Ya abierto en este teléfono: se sigue contando en el mismo (no se pierde lo contado)
            gestor.local.inventarios.buscar(coEmpresa, toma.numero)?.let { existente ->
                _estado.update { it.copy(abriendo = false, abierto = existente.id) }
                return@launch
            }
            when (val r = gestor.inventarioProfit().abrir(usuario, coEmpresa, toma)) {
                is Resultado.Exito -> {
                    val inventario = gestor.local.inventarios.abrir(coEmpresa, toma, r.valor)
                    _estado.update { it.copy(abriendo = false, abierto = inventario.id) }
                }
                is Resultado.Error -> _estado.update { it.copy(abriendo = false, aviso = r.mensaje) }
            }
        }
    }

    /** La pantalla ya navegó a contar. */
    fun aperturaMostrada() = _estado.update { it.copy(abierto = null) }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
