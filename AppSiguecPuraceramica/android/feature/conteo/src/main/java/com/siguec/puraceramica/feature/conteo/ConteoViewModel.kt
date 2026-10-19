// MÓDULO :feature:conteo
package com.siguec.puraceramica.feature.conteo

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Base del teléfono (todo sin WiFi), modelos y formato
import com.siguec.puraceramica.core.local.AlmacenLocal
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.ConteoDto
import com.siguec.puraceramica.core.red.Formato
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.InventarioDto
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de contar. */
data class EstadoConteo(
    val inventario: InventarioDto? = null,      // La toma física (guardada en el teléfono)
    val misConteos: List<ConteoDto> = emptyList(), // Mis últimos conteos
    val contadoPorMi: Int = 0,                  // Cuántos conteos llevo
    val cargando: Boolean = true,
    val mensajeError: String? = null,           // No existe en el teléfono
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
 * Lógica de contar en UNA toma física abierta, TODO en el teléfono (sin WiFi):
 *  1. Se escanea (o escribe) el código de barras -> se muestra el artículo y lo ya contado.
 *  2. Se escribe la cantidad y "Anotar": queda un conteo A NOMBRE de quien cuenta.
 *  Modo rápido: cada escaneo anota 1 unidad y la cámara se vuelve a abrir.
 * Cada anotación SE SUMA al stock contado; si alguien se equivoca, borra su
 * conteo o anota en negativo.
 *
 * @param local base del teléfono
 * @param coEmpresa empresa elegida (los artículos son de esa empresa)
 * @param id número interno (en el teléfono) del inventario
 * @param usuario quien cuenta (cada conteo queda a su nombre)
 */
class ConteoViewModel(
    private val local: AlmacenLocal,
    private val coEmpresa: String,
    private val id: Int,
    private val usuario: String
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoConteo())
    val estado: StateFlow<EstadoConteo> = _estado.asStateFlow()

    init {
        cargar()
    }

    /** El inventario y mis últimos conteos. */
    fun cargar() {
        viewModelScope.launch {
            val inventario = local.inventarios.leer(id)
            val mios = local.inventarios.ultimos(id, usuario)
            val cuantos = local.inventarios.cuantosDe(id, usuario)
            _estado.update {
                it.copy(cargando = false, inventario = inventario, misConteos = mios, contadoPorMi = cuantos,
                    mensajeError = if (inventario == null) "Esta toma ya no está en el teléfono." else null)
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
        _estado.update { it.copy(codigo = Inventario.limpiarCodigo(codigo), articulo = null) }
        if (_estado.value.modoRapido) anotar(cantidadFija = "1") else buscarArticulo()
    }

    /** Busca el artículo del código (escrito o escaneado) para confirmarlo antes de anotar. */
    fun buscarArticulo() {
        val codigo = Inventario.limpiarCodigo(_estado.value.codigo)
        if (codigo.isEmpty()) return
        _estado.update { it.copy(buscando = true) }
        viewModelScope.launch {
            val articulo = local.articulos.porCodigo(coEmpresa, codigo)
            if (articulo == null) {
                _estado.update { it.copy(buscando = false, articulo = null, aviso = noExiste(codigo)) }
            } else {
                val contado = local.inventarios.contado(id, articulo.coArt)
                _estado.update { it.copy(buscando = false, articulo = articulo.copy(contado = contado), pedirCantidad = true) }
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
        val valor = Inventario.leer(cantidad) ?: return
        _estado.update { it.copy(anotando = true) }
        viewModelScope.launch {
            val articulo = local.articulos.porCodigo(coEmpresa, e.codigo)
            val r = articulo?.let { local.inventarios.anotar(id, it.coArt, it.artDes, Inventario.limpiarCodigo(e.codigo), valor, usuario) }
            when {
                articulo == null -> _estado.update { it.copy(anotando = false, aviso = noExiste(e.codigo)) }
                r == null -> _estado.update {
                    it.copy(anotando = false, aviso = "Esta toma ya se envió a Profit: no se puede contar más.")
                }
                else -> {
                    val c = r.conteo
                    // Listo para el siguiente: código y cantidad vacíos
                    _estado.update {
                        it.copy(anotando = false, codigo = "", cantidad = "", articulo = null,
                            abrirCamara = it.modoRapido,
                            aviso = "${c.coArt} ${c.artDes}: ${Inventario.diferencia(c.cantidad)} " +
                                "(usted ${Formato.cantidad(r.contadoUsuario)}, total ${Formato.cantidad(r.contadoTotal)})")
                    }
                    cargar()
                }
            }
        }
    }

    /** Borra un conteo equivocado (solo los propios, con el inventario abierto). */
    fun borrar(conteo: ConteoDto) {
        viewModelScope.launch {
            val borrado = local.inventarios.borrar(id, conteo.id, usuario)
            _estado.update {
                it.copy(aviso = if (borrado) "Se borró ${Formato.cantidad(conteo.cantidad)} de ${conteo.coArt}"
                else "No se pudo borrar: la toma ya se envió a Profit.")
            }
            cargar()
        }
    }

    private fun noExiste(codigo: String) = "No hay ningún artículo con el código ${codigo.trim()} en este teléfono. " +
        "Si es nuevo en Profit, sincronice los artículos (con WiFi)."

    /** El escáner no se pudo abrir. */
    fun alFallarEscaner(mensaje: String) = _estado.update { it.copy(aviso = mensaje, abrirCamara = false) }

    /** La pantalla ya mostró el aviso. */
    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
