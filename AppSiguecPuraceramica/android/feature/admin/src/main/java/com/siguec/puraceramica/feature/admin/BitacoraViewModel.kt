// MÓDULO :feature:admin
package com.siguec.puraceramica.feature.admin

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red: repositorio, DTO y resultado
import com.siguec.puraceramica.core.red.RenglonBitacoraDto
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.Resultado
// Fechas (Android 8 en adelante)
import java.time.LocalDate
// Corrutinas y estado
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Desde cuándo se ve la bitácora. */
enum class PeriodoBitacora(val etiqueta: String, val dias: Long?) {
    HOY("Hoy", 0),
    SEMANA("7 días", 6),
    MES("30 días", 29),
    TODO("Todo", null)
}

/** Estado de la bitácora. */
data class EstadoBitacora(
    val usuario: String = "",                   // Filtro por usuario ("" = todos)
    val accion: String = "",                    // Filtro por acción ("" = todas)
    val periodo: PeriodoBitacora = PeriodoBitacora.SEMANA,
    val renglones: List<RenglonBitacoraDto> = emptyList(),
    val pagina: Int = 0,
    val hayMas: Boolean = false,
    val cargando: Boolean = true,
    val cargandoMas: Boolean = false,
    val mensajeError: String? = null
)

/** Bitácora de la App con filtros (usuario, acción, periodo) y páginas al bajar. */
class BitacoraViewModel(private val repositorio: RepositorioAdmin) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoBitacora())
    val estado: StateFlow<EstadoBitacora> = _estado.asStateFlow()

    private var consulta: Job? = null

    init {
        buscar()
    }

    fun alCambiarUsuario(texto: String) {
        _estado.update { it.copy(usuario = texto) }
        buscar(esperar = true)
    }

    fun alElegirAccion(accion: String) {
        _estado.update { it.copy(accion = accion) }
        buscar()
    }

    fun alElegirPeriodo(periodo: PeriodoBitacora) {
        _estado.update { it.copy(periodo = periodo) }
        buscar()
    }

    /** "AAAA-MM-DD" desde donde se busca (null = desde el principio). */
    private fun desde(periodo: PeriodoBitacora): String? =
        periodo.dias?.let { LocalDate.now().minusDays(it).toString() }

    /** Desde la página 1 (mientras escribe el usuario espera medio segundo). */
    fun buscar(esperar: Boolean = false) {
        consulta?.cancel()
        consulta = viewModelScope.launch {
            if (esperar) delay(500)
            _estado.update { it.copy(cargando = true, mensajeError = null) }
            val e = _estado.value
            when (val r = repositorio.bitacora(e.usuario, accion = e.accion, desde = desde(e.periodo), pagina = 1)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(cargando = false, renglones = r.valor.renglones, pagina = 1, hayMas = r.valor.hayMas)
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, renglones = emptyList(), hayMas = false, mensajeError = r.mensaje)
                }
            }
        }
    }

    /** Página siguiente. */
    fun cargarMas() {
        val e = _estado.value
        if (!e.hayMas || e.cargando || e.cargandoMas) return
        _estado.update { it.copy(cargandoMas = true) }
        consulta = viewModelScope.launch {
            when (val r = repositorio.bitacora(e.usuario, accion = e.accion, desde = desde(e.periodo), pagina = e.pagina + 1)) {
                is Resultado.Exito -> _estado.update {
                    val nuevos = r.valor.renglones.filter { n -> it.renglones.none { b -> b.id == n.id } }
                    it.copy(cargandoMas = false, renglones = it.renglones + nuevos, pagina = r.valor.pagina,
                        hayMas = r.valor.hayMas)
                }
                is Resultado.Error -> _estado.update { it.copy(cargandoMas = false, mensajeError = r.mensaje) }
            }
        }
    }
}
