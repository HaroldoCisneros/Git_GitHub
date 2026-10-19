// MÓDULO :feature:conteo
package com.siguec.puraceramica.feature.conteo

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.red.Resultado
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "¿En qué inventario va a contar?". */
data class EstadoElegirInventario(
    val abiertos: List<InventarioDto> = emptyList(),
    val cargando: Boolean = true,
    val mensajeError: String? = null
)

/** Trae los inventarios ABIERTOS de la empresa: solo en esos se puede contar. */
class ElegirInventarioViewModel(private val repositorio: RepositorioInventarios) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoElegirInventario())
    val estado: StateFlow<EstadoElegirInventario> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            when (val r = repositorio.lista(soloAbiertos = true)) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, abiertos = r.valor) }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }
}
