// MÓDULO :feature:conteo
package com.siguec.puraceramica.feature.conteo

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Modelos y base del teléfono
import com.siguec.puraceramica.core.local.InventariosLocales
import com.siguec.puraceramica.core.red.InventarioDto
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "¿En qué inventario va a contar?". */
data class EstadoElegirInventario(
    val abiertos: List<InventarioDto> = emptyList(),
    val cargando: Boolean = true
)

/** Los inventarios ABIERTOS en el teléfono: solo en esos se cuenta. Sin WiFi. */
class ElegirInventarioViewModel(private val local: InventariosLocales, private val coEmpresa: String) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoElegirInventario())
    val estado: StateFlow<EstadoElegirInventario> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            val abiertos = local.lista(coEmpresa, soloAbiertos = true)
            _estado.update { it.copy(cargando = false, abiertos = abiertos) }
        }
    }
}
