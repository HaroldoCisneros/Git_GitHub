// MÓDULO :feature:articulos
package com.siguec.puraceramica.feature.articulos

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Modelos, resultado y sesión (con la base del teléfono y la conexión a Profit)
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.SincronizacionDto
import com.siguec.puraceramica.core.sesion.GestorSesion
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
    val inventarios: List<InventarioDto> = emptyList(), // Del teléfono (para ver el stock contado)
    val inventarioId: Int? = null,                  // Inventario elegido (null = sin stock contado)
    val soloContados: Boolean = false,
    val ultima: SincronizacionDto? = null,          // Última sincronización en este teléfono
    val sincronizando: Boolean = false,
    val aviso: String? = null                       // Mensaje abajo (resultado de sincronizar...)
)

/**
 * Lógica de "Artículos":
 *  - Consultar la copia del TELÉFONO (sin WiFi): código, descripción y stock contado
 *    en el inventario elegido.
 *  - Sincronizar (con WiFi): baja saArticulo de Profit y reemplaza la copia del teléfono.
 *
 * @param gestor sesión: base del teléfono y conexión al SQL Server
 * @param coEmpresa empresa elegida
 * @param inventarioInicial el último en que se contó (se propone al abrir)
 */
class ArticulosViewModel(
    private val gestor: GestorSesion,
    private val coEmpresa: String,
    inventarioInicial: Int?
) : ViewModel() {

    private val articulos = gestor.local.articulos
    private val inventarios = gestor.local.inventarios

    private val _estado = MutableStateFlow(EstadoArticulos(inventarioId = inventarioInicial))
    val estado: StateFlow<EstadoArticulos> = _estado.asStateFlow()

    // Búsqueda en curso (se cancela si se escribe otra cosa)
    private var busqueda: Job? = null

    init {
        cargarDatos()
    }

    /** Inventarios del teléfono y la última sincronización; después la primera página. */
    private fun cargarDatos() {
        viewModelScope.launch {
            val todos = inventarios.lista(coEmpresa)
            val ultima = articulos.ultimaSincronizacion(coEmpresa)
            _estado.update { e ->
                // Si el inventario propuesto ya no está se toma el único abierto (o ninguno)
                val elegido = e.inventarioId?.takeIf { id -> todos.any { it.id == id } }
                    ?: todos.singleOrNull { it.abierto }?.id
                e.copy(inventarios = todos, inventarioId = elegido, ultima = ultima)
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
            // En el teléfono: no necesita WiFi
            val (encontrados, hayMas) = articulos.buscar(coEmpresa, e.texto, e.inventarioId, e.soloContados, pagina)
            _estado.update {
                it.copy(cargando = false, pagina = pagina, hayMas = hayMas,
                    articulos = if (pagina == 1) encontrados else it.articulos + encontrados)
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
        _estado.update { it.copy(texto = com.siguec.puraceramica.core.red.Inventario.limpiarCodigo(codigo)) }
        buscar()
    }

    fun alFallarEscaner(mensaje: String) = _estado.update { it.copy(aviso = mensaje) }

    /** Acción "Sincronizar con Profit" (necesita WiFi): baja saArticulo y reemplaza la copia del teléfono. */
    fun sincronizar() {
        if (_estado.value.sincronizando) return
        val usuario = gestor.usuario ?: return
        _estado.update { it.copy(sincronizando = true) }
        viewModelScope.launch {
            when (val r = gestor.profit().leerArticulos(usuario, coEmpresa)) {
                is Resultado.Exito -> {
                    articulos.guardarCopia(coEmpresa, r.valor, usuario.usuario, Formato.ahora())
                    val s = articulos.ultimaSincronizacion(coEmpresa)
                    _estado.update {
                        it.copy(sincronizando = false, ultima = s,
                            aviso = "Sincronizado: ${Formato.cantidad(s?.total?.toDouble())} artículos y " +
                                "${Formato.cantidad(s?.codigos?.toDouble())} códigos para escanear")
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
