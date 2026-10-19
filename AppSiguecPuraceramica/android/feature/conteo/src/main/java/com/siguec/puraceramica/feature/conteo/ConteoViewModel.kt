// MÓDULO :feature:conteo
package com.siguec.puraceramica.feature.conteo

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red: repositorios, DTO, formato y resultado
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.ConteoDto
import com.siguec.puraceramica.core.red.DetalleInventarioDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.RepositorioArticulos
import com.siguec.puraceramica.core.red.RepositorioConteos
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.red.Resultado
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de contar. */
data class EstadoConteo(
    val detalle: DetalleInventarioDto? = null,  // El inventario y mis últimos conteos
    val cargando: Boolean = true,
    val mensajeError: String? = null,           // No se pudo abrir el inventario
    val codigo: String = "",                    // Lo escaneado o escrito
    val articulo: ArticuloDto? = null,          // El artículo de ese código (para confirmar antes de anotar)
    val buscando: Boolean = false,
    val cantidad: String = "",                  // Lo contado (se suma a lo anterior)
    val errorCantidad: String? = null,
    val anotando: Boolean = false,
    val pedirCantidad: Boolean = false,         // Se encontró el artículo: el cursor pasa a la cantidad
    val modoRapido: Boolean = false,            // Cada escaneo anota 1 y vuelve a abrir la cámara
    val abrirCamara: Boolean = false,           // Pedido a la pantalla: abrir el escáner otra vez
    val aviso: String? = null                   // Mensaje abajo (anotado, error...)
)

/**
 * Lógica de contar en UN inventario abierto:
 *  1. Se escanea (o escribe) el código de barras -> se muestra el artículo y lo ya contado.
 *  2. Se escribe la cantidad y "Anotar": queda un conteo A NOMBRE de quien cuenta.
 *  Modo rápido: cada escaneo anota 1 unidad y la cámara se vuelve a abrir.
 * Cada anotación SE SUMA al stock contado; si alguien se equivoca, borra su
 * conteo o anota en negativo.
 *
 * @param id número del inventario abierto
 */
class ConteoViewModel(
    private val inventarios: RepositorioInventarios,
    private val articulos: RepositorioArticulos,
    private val conteos: RepositorioConteos,
    private val id: Int
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoConteo())
    val estado: StateFlow<EstadoConteo> = _estado.asStateFlow()

    init {
        cargar()
    }

    /** El inventario y mis últimos conteos. */
    fun cargar() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            when (val r = inventarios.detalle(id)) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, detalle = r.valor) }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }

    fun alCambiarCodigo(texto: String) = _estado.update { it.copy(codigo = texto, articulo = null) }

    fun alCambiarCantidad(texto: String) = _estado.update { it.copy(cantidad = texto, errorCantidad = null) }

    fun alternarModoRapido() = _estado.update { it.copy(modoRapido = !it.modoRapido) }

    /** La pantalla ya abrió la cámara. */
    fun camaraAbierta() = _estado.update { it.copy(abrirCamara = false) }

    /** La pantalla ya movió el cursor a la cantidad. */
    fun cantidadPedida() = _estado.update { it.copy(pedirCantidad = false) }

    /** La cámara leyó un código. */
    fun alLeerCodigo(codigo: String) {
        _estado.update { it.copy(codigo = codigo, articulo = null) }
        if (_estado.value.modoRapido) anotar(cantidadFija = "1") else buscarArticulo()
    }

    /** Busca el artículo del código (escrito o escaneado) para confirmarlo antes de anotar. */
    fun buscarArticulo() {
        val codigo = _estado.value.codigo.trim()
        if (codigo.isEmpty()) return
        _estado.update { it.copy(buscando = true) }
        viewModelScope.launch {
            when (val r = articulos.porCodigo(codigo, id)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(buscando = false, articulo = r.valor, pedirCantidad = true)
                }
                is Resultado.Error -> _estado.update { it.copy(buscando = false, articulo = null, aviso = r.mensaje) }
            }
        }
    }

    /**
     * Botón "Anotar".
     * @param cantidadFija en modo rápido se anota "1" sin pedir la cantidad
     */
    fun anotar(cantidadFija: String? = null) {
        val e = _estado.value
        if (e.anotando) return
        if (e.codigo.isBlank()) {
            _estado.update { it.copy(aviso = "Escanee o escriba el código del artículo") }
            return
        }
        val cantidad = cantidadFija ?: e.cantidad
        Inventario.validarCantidad(cantidad)?.let { error ->
            _estado.update { it.copy(errorCantidad = error) }
            return
        }
        _estado.update { it.copy(anotando = true) }
        viewModelScope.launch {
            when (val r = conteos.anotar(id, e.codigo, cantidad)) {
                is Resultado.Exito -> {
                    val c = r.valor.conteo
                    // Listo para el siguiente: código y cantidad vacíos
                    _estado.update {
                        it.copy(anotando = false, codigo = "", cantidad = "", articulo = null,
                            abrirCamara = it.modoRapido,
                            aviso = "${c.coArt} ${c.artDes}: ${Inventario.diferencia(c.cantidad)} " +
                                "(usted ${Formato.cantidad(r.valor.contadoUsuario)}, " +
                                "total ${Formato.cantidad(r.valor.contadoTotal)})")
                    }
                    cargar()
                }
                is Resultado.Error -> _estado.update { it.copy(anotando = false, aviso = r.mensaje) }
            }
        }
    }

    /** Borra un conteo equivocado (solo los propios, con el inventario abierto). */
    fun borrar(conteo: ConteoDto) {
        viewModelScope.launch {
            when (val r = conteos.borrar(id, conteo.id)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(aviso = "Se borró ${Formato.cantidad(conteo.cantidad)} de ${conteo.coArt}") }
                    cargar()
                }
                is Resultado.Error -> _estado.update { it.copy(aviso = r.mensaje) }
            }
        }
    }

    /** El escáner no se pudo abrir. */
    fun alFallarEscaner(mensaje: String) = _estado.update { it.copy(aviso = mensaje, abrirCamara = false) }

    /** La pantalla ya mostró el aviso. */
    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
