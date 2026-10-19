// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red
import com.siguec.puraceramica.core.red.AlmacenDto
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.red.Resultado
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de inventarios por almacén. */
data class EstadoInventarios(
    val inventarios: List<InventarioDto> = emptyList(),
    val almacenes: List<AlmacenDto> = emptyList(),   // Para preparar uno nuevo
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    val trabajando: Boolean = false,                 // Preparando o cerrando
    val aviso: String? = null
)

/**
 * Lógica de "Inventarios por almacén": lista, preparar (abrir) el inventario de
 * un almacén con la foto del stock de Profit, y cerrarlo.
 */
class InventariosViewModel(private val repositorio: RepositorioInventarios) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoInventarios())
    val estado: StateFlow<EstadoInventarios> = _estado.asStateFlow()

    init {
        cargar()
    }

    /** Inventarios y almacenes de Profit. */
    fun cargar() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            val lista = repositorio.lista()
            val almacenes = (repositorio.almacenes() as? Resultado.Exito)?.valor.orEmpty()
            _estado.update {
                when (lista) {
                    is Resultado.Exito -> it.copy(cargando = false, inventarios = lista.valor, almacenes = almacenes)
                    is Resultado.Error -> it.copy(cargando = false, mensajeError = lista.mensaje)
                }
            }
        }
    }

    /**
     * Prepara el inventario del almacén.
     * @param alListo se llama con el inventario nuevo (para empezar a contar de una vez)
     */
    fun preparar(coAlma: String, descripcion: String, alListo: (InventarioDto) -> Unit = {}) {
        if (_estado.value.trabajando) return
        _estado.update { it.copy(trabajando = true) }
        viewModelScope.launch {
            when (val r = repositorio.preparar(coAlma, descripcion)) {
                is Resultado.Exito -> {
                    _estado.update {
                        it.copy(trabajando = false, aviso = "Inventario N° ${r.valor.id} preparado: " +
                            "${r.valor.articulosStock} artículos con stock en el almacén ${r.valor.coAlma}")
                    }
                    cargar()
                    alListo(r.valor)
                }
                is Resultado.Error -> _estado.update { it.copy(trabajando = false, aviso = r.mensaje) }
            }
        }
    }

    /** Cierra el inventario: ya no se puede contar. */
    fun cerrar(inventario: InventarioDto) {
        if (_estado.value.trabajando) return
        _estado.update { it.copy(trabajando = true) }
        viewModelScope.launch {
            when (val r = repositorio.cerrar(inventario.id)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(trabajando = false, aviso = "Inventario N° ${inventario.id} cerrado") }
                    cargar()
                }
                is Resultado.Error -> _estado.update { it.copy(trabajando = false, aviso = r.mensaje) }
            }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
