// MÓDULO :feature:articulos
package com.siguec.puraceramica.feature.articulos

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red: repositorios, DTO y resultado
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.RepositorioArticulos
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.SincronizacionDto
// Estado
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de artículos. */
data class EstadoArticulos(
    val texto: String = "",                         // Lo que se busca
    val articulos: List<ArticuloDto> = emptyList(),
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val cargando: Boolean = false,
    val mensajeError: String? = null,               // No se pudo consultar
    val inventarios: List<InventarioDto> = emptyList(), // Abiertos (para ver el stock contado)
    val inventarioId: Int? = null,                  // Inventario elegido (null = sin stock contado)
    val soloContados: Boolean = false,
    val ultima: SincronizacionDto? = null,          // Última sincronización con Profit
    val sincronizando: Boolean = false,
    val aviso: String? = null                       // Mensaje abajo (resultado de sincronizar...)
)

/**
 * Lógica de "Artículos": consultar la copia sincronizada (código, descripción y
 * stock contado en el inventario elegido) y la acción de sincronizar con Profit.
 *
 * @param repositorio artículos de la empresa elegida
 * @param inventarios para elegir de qué inventario abierto sale el stock contado
 * @param inventarioInicial el último en que se contó (se propone al abrir)
 */
class ArticulosViewModel(
    private val repositorio: RepositorioArticulos,
    private val inventarios: RepositorioInventarios,
    inventarioInicial: Int?
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoArticulos(inventarioId = inventarioInicial))
    val estado: StateFlow<EstadoArticulos> = _estado.asStateFlow()

    // Búsqueda en curso (se cancela si se escribe otra cosa)
    private var busqueda: Job? = null

    init {
        cargarDatos()
    }

    /** Inventarios abiertos y la última sincronización; después la primera página. */
    private fun cargarDatos() {
        viewModelScope.launch {
            val abiertos = (inventarios.lista(soloAbiertos = true) as? Resultado.Exito)?.valor.orEmpty()
            val ultima = (repositorio.ultimaSincronizacion() as? Resultado.Exito)?.valor
            _estado.update { e ->
                // Si el inventario propuesto ya no está abierto se toma el único abierto (o ninguno)
                val elegido = e.inventarioId?.takeIf { id -> abiertos.any { it.id == id } }
                    ?: abiertos.singleOrNull()?.id
                e.copy(inventarios = abiertos, inventarioId = elegido, ultima = ultima)
            }
            buscar()
        }
    }

    fun alCambiarTexto(texto: String) = _estado.update { it.copy(texto = texto) }

    /** Busca desde la primera página. */
    fun buscar() = cargarPagina(1)

    /** Siguiente página (al llegar al final de la lista). */
    fun masResultados() {
        val e = _estado.value
        if (e.hayMas && !e.cargando) cargarPagina(e.pagina + 1)
    }

    private fun cargarPagina(pagina: Int) {
        busqueda?.cancel()
        val e = _estado.value
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        busqueda = viewModelScope.launch {
            when (val r = repositorio.buscar(e.texto, e.inventarioId, e.soloContados, pagina)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(cargando = false, pagina = pagina, hayMas = r.valor.hayMas,
                        articulos = if (pagina == 1) r.valor.articulos else it.articulos + r.valor.articulos)
                }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }

    /** Elige de qué inventario se ve el stock contado (null = ninguno). */
    fun elegirInventario(id: Int?) {
        _estado.update { it.copy(inventarioId = id, soloContados = it.soloContados && id != null) }
        buscar()
    }

    fun alternarSoloContados() {
        _estado.update { it.copy(soloContados = !it.soloContados) }
        buscar()
    }

    /** La cámara leyó un código: se busca ese código. */
    fun alLeerCodigo(codigo: String) {
        _estado.update { it.copy(texto = codigo) }
        buscar()
    }

    fun alFallarEscaner(mensaje: String) = _estado.update { it.copy(aviso = mensaje) }

    /** Acción "Sincronizar con Profit": baja saArticulo a la copia de la App. */
    fun sincronizar() {
        if (_estado.value.sincronizando) return
        _estado.update { it.copy(sincronizando = true) }
        viewModelScope.launch {
            when (val r = repositorio.sincronizar()) {
                is Resultado.Exito -> {
                    val s = r.valor
                    _estado.update {
                        it.copy(sincronizando = false, ultima = s,
                            aviso = "Sincronizado: ${s.nuevos} nuevos, ${s.actualizados} actualizados, " +
                                "${s.retirados} retirados (${Formato.cantidad(s.total.toDouble())} artículos)")
                    }
                    buscar()
                }
                is Resultado.Error -> _estado.update { it.copy(sincronizando = false, aviso = r.mensaje) }
            }
        }
    }

    /** La pantalla ya mostró el aviso. */
    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
