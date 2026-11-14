package com.sighec.pedidos.ui.pedido

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sighec.pedidos.Contenedor
import com.sighec.pedidos.data.Articulo
import com.sighec.pedidos.data.Cliente
import com.sighec.pedidos.data.Linea
import com.sighec.pedidos.data.RenglonPedido
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PedidoViewModel(private val app: Contenedor) : ViewModel() {

    // ---- Catálogo (panel derecho) ----
    var lineas by mutableStateOf<List<Linea>>(emptyList())
        private set
    var lineaSeleccionada by mutableStateOf<String?>(null)
        private set
    var articulos by mutableStateOf<List<Articulo>>(emptyList())
        private set
    var cargandoCatalogo by mutableStateOf(false)
        private set
    var filtro by mutableStateOf("")

    private val cacheArticulos = mutableMapOf<String, List<Articulo>>()

    val articulosVisibles by derivedStateOf {
        val f = filtro.trim()
        if (f.isEmpty()) articulos
        else articulos.filter { it.descripcion.contains(f, true) || it.codigo.contains(f, true) }
    }

    // ---- Pedido (panel izquierdo) ----
    val renglones = mutableStateListOf<RenglonPedido>()
    var cliente by mutableStateOf<Cliente?>(null)
    var observacion by mutableStateOf("")
    var guardando by mutableStateOf(false)
        private set

    val ivaPorcentaje: Double get() = app.config.config.value.ivaPorcentaje
    val subtotal by derivedStateOf { renglones.sumOf { it.subtotal } }
    val iva by derivedStateOf { subtotal * ivaPorcentaje / 100.0 }
    val total by derivedStateOf { subtotal + iva }

    // ---- Búsqueda de clientes ----
    var clientesEncontrados by mutableStateOf<List<Cliente>>(emptyList())
        private set
    var buscandoClientes by mutableStateOf(false)
        private set
    private var busquedaJob: Job? = null

    // ---- Mensajes ----
    var error by mutableStateOf<String?>(null)
    var mensaje by mutableStateOf<String?>(null)

    val nombreUsuario: String get() = app.usuarioActual?.nombre.orEmpty()

    init {
        cargarLineas()
    }

    fun cargarLineas() {
        cargandoCatalogo = true
        error = null
        viewModelScope.launch {
            runCatching { app.catalogo.lineas() }
                .onSuccess { ls ->
                    lineas = ls
                    ls.firstOrNull()?.let { seleccionarLinea(it.codigo) } ?: run { cargandoCatalogo = false }
                }
                .onFailure { error = it.message; cargandoCatalogo = false }
        }
    }

    fun seleccionarLinea(codigo: String) {
        lineaSeleccionada = codigo
        filtro = ""
        cacheArticulos[codigo]?.let { articulos = it; cargandoCatalogo = false; return }
        cargandoCatalogo = true
        articulos = emptyList()
        viewModelScope.launch {
            runCatching { app.catalogo.articulos(codigo) }
                .onSuccess { arts ->
                    cacheArticulos[codigo] = arts
                    if (lineaSeleccionada == codigo) articulos = arts
                }
                .onFailure { error = it.message }
            if (lineaSeleccionada == codigo) cargandoCatalogo = false
        }
    }

    /** Recarga precios y stock desde Profit. */
    fun refrescar() {
        cacheArticulos.clear()
        lineaSeleccionada?.let { seleccionarLinea(it) } ?: cargarLineas()
    }

    fun urlImagen(a: Articulo): String? = app.catalogo.urlImagen(a.codigo)

    fun cantidadEnPedido(codigo: String): Double =
        renglones.firstOrNull { it.articulo.codigo == codigo }?.cantidad ?: 0.0

    fun agregar(a: Articulo, cantidad: Double = 1.0) {
        val i = renglones.indexOfFirst { it.articulo.codigo == a.codigo }
        if (i >= 0) renglones[i] = renglones[i].copy(cantidad = renglones[i].cantidad + cantidad)
        else renglones.add(RenglonPedido(a, cantidad))
    }

    fun restar(codigo: String) {
        val i = renglones.indexOfFirst { it.articulo.codigo == codigo }
        if (i < 0) return
        val nueva = renglones[i].cantidad - 1
        if (nueva <= 0) renglones.removeAt(i) else renglones[i] = renglones[i].copy(cantidad = nueva)
    }

    fun fijarCantidad(codigo: String, cantidad: Double) {
        val i = renglones.indexOfFirst { it.articulo.codigo == codigo }
        if (i < 0) return
        if (cantidad <= 0) renglones.removeAt(i) else renglones[i] = renglones[i].copy(cantidad = cantidad)
    }

    fun quitar(codigo: String) {
        renglones.removeAll { it.articulo.codigo == codigo }
    }

    fun buscarClientes(texto: String) {
        busquedaJob?.cancel()
        if (texto.trim().length < 2) { clientesEncontrados = emptyList(); return }
        busquedaJob = viewModelScope.launch {
            delay(350) // esperar a que deje de escribir
            buscandoClientes = true
            runCatching { app.catalogo.buscarClientes(texto) }
                .onSuccess { clientesEncontrados = it }
                .onFailure { error = it.message }
            buscandoClientes = false
        }
    }

    fun nuevoPedido() {
        renglones.clear()
        cliente = null
        observacion = ""
    }

    fun guardar() {
        val usr = app.usuarioActual ?: run { error = "Sesión vencida, ingrese de nuevo"; return }
        val cli = cliente ?: run { error = "Seleccione un cliente"; return }
        if (renglones.isEmpty()) { error = "El pedido no tiene artículos"; return }
        guardando = true
        viewModelScope.launch {
            runCatching { app.pedidos.guardar(usr, cli, renglones.toList(), ivaPorcentaje, observacion) }
                .onSuccess { numero ->
                    mensaje = "Pedido N° $numero guardado"
                    nuevoPedido()
                }
                .onFailure { error = it.message ?: "No se pudo guardar el pedido" }
            guardando = false
        }
    }
}
