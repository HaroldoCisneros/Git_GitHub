// MÓDULO :feature:inventario
package com.siguec.puraceramica.feature.inventario

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red: repositorio, DTO y resultado
import com.siguec.puraceramica.core.red.ComparacionDto
import com.siguec.puraceramica.core.red.DiferenciaDto
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.red.Resultado
// Corrutinas y estado
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la comparación contado vs. stock. */
data class EstadoComparacion(
    val resumen: ComparacionDto? = null,          // Totales (contados, sobrantes, faltantes...)
    val diferencias: List<DiferenciaDto> = emptyList(),
    val soloDiferencias: Boolean = true,                // false = también los que cuadran
    val incluirNoContados: Boolean = true,              // true = también lo que tiene stock y nadie contó
    val pagina: Int = 0,
    val hayMas: Boolean = false,
    val cargando: Boolean = true,
    val cargandoMas: Boolean = false,
    val mensajeError: String? = null
)

/**
 * Comparación de lo contado con la foto del stock del almacén en Profit (para que la oficina
 * decida el ajuste). Solo consulta: no cambia nada en Profit.
 */
class ComparacionViewModel(
    private val repositorio: RepositorioInventarios,
    private val id: Int
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoComparacion())
    val estado: StateFlow<EstadoComparacion> = _estado.asStateFlow()

    private var consulta: Job? = null

    init {
        cargar()
    }

    fun alternarSoloDiferencias() {
        _estado.update { it.copy(soloDiferencias = !it.soloDiferencias) }
        cargar()
    }

    fun alternarNoContados() {
        _estado.update { it.copy(incluirNoContados = !it.incluirNoContados) }
        cargar()
    }

    /** Desde la página 1. */
    fun cargar() {
        consulta?.cancel()
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        val e = _estado.value
        consulta = viewModelScope.launch {
            when (val r = repositorio.comparacion(id, e.soloDiferencias, e.incluirNoContados, 1)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(cargando = false, resumen = r.valor, diferencias = r.valor.diferencias, pagina = 1,
                        hayMas = r.valor.hayMas)
                }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }

    /** La lista llegó al final: página siguiente. */
    fun cargarMas() {
        val e = _estado.value
        if (!e.hayMas || e.cargando || e.cargandoMas) return
        _estado.update { it.copy(cargandoMas = true) }
        consulta = viewModelScope.launch {
            when (val r = repositorio.comparacion(id, e.soloDiferencias, e.incluirNoContados, e.pagina + 1)) {
                is Resultado.Exito -> _estado.update {
                    val nuevos = r.valor.diferencias.filter { n -> it.diferencias.none { d -> d.coArt == n.coArt } }
                    it.copy(cargandoMas = false, diferencias = it.diferencias + nuevos, pagina = r.valor.pagina,
                        hayMas = r.valor.hayMas)
                }
                is Resultado.Error -> _estado.update { it.copy(cargandoMas = false, mensajeError = r.mensaje) }
            }
        }
    }
}
