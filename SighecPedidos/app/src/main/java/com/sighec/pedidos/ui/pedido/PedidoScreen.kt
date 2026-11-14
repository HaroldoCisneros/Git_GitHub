package com.sighec.pedidos.ui.pedido

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.sighec.pedidos.contenedor
import com.sighec.pedidos.data.Articulo
import com.sighec.pedidos.data.RenglonPedido
import com.sighec.pedidos.ui.cantidad
import com.sighec.pedidos.ui.monto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoScreen(onVolver: () -> Unit) {
    val app = LocalContext.current.contenedor
    val vm: PedidoViewModel = viewModel { PedidoViewModel(app) }
    val snackbar = remember { SnackbarHostState() }
    var confirmarSalida by remember { mutableStateOf(false) }
    var mostrarClientes by rememberSaveable { mutableStateOf(false) }
    var editando by remember { mutableStateOf<RenglonPedido?>(null) }

    val intentarSalir: () -> Unit = {
        if (vm.renglones.isNotEmpty()) {
            confirmarSalida = true
        } else {
            onVolver()
        }
    }
    BackHandler(onBack = intentarSalir)

    LaunchedEffect(vm.mensaje) { vm.mensaje?.let { snackbar.showSnackbar(it); vm.mensaje = null } }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.error = null } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Toma de Pedidos") },
                navigationIcon = {
                    IconButton(onClick = intentarSalir) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Text(vm.nombreUsuario, modifier = Modifier.padding(end = 8.dp))
                    IconButton(onClick = { vm.refrescar() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar precios y stock")
                    }
                    IconButton(onClick = { vm.nuevoPedido() }) {
                        Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = "Nuevo pedido")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        // Pantalla dividida: pedido a la izquierda, catálogo a la derecha.
        Row(Modifier.padding(pad).fillMaxSize()) {
            PanelPedido(
                vm = vm,
                onElegirCliente = { mostrarClientes = true },
                onEditarRenglon = { editando = it },
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
            )
            VerticalDivider()
            PanelCatalogo(vm = vm, modifier = Modifier.weight(0.58f).fillMaxHeight())
        }
    }

    if (mostrarClientes) {
        DialogoClientes(vm, onCerrar = { mostrarClientes = false })
    }
    editando?.let { r ->
        DialogoCantidad(
            renglon = r,
            onAceptar = { vm.fijarCantidad(r.articulo.codigo, it); editando = null },
            onEliminar = { vm.quitar(r.articulo.codigo); editando = null },
            onCerrar = { editando = null },
        )
    }
    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("¿Salir sin guardar?") },
            text = { Text("El pedido en curso tiene artículos y se perderá.") },
            confirmButton = { TextButton(onClick = { confirmarSalida = false; onVolver() }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Seguir en el pedido") } },
        )
    }
}

/* ======================= Panel izquierdo: el pedido ======================= */

@Composable
private fun PanelPedido(
    vm: PedidoViewModel,
    onElegirCliente: () -> Unit,
    onEditarRenglon: (RenglonPedido) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        OutlinedCard(onClick = onElegirCliente, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val c = vm.cliente
                    Text(
                        c?.nombre ?: "Toque para seleccionar el cliente",
                        fontWeight = if (c != null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (c != null) Text(
                        "${c.codigo}  ·  RIF ${c.rif}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Outlined.Search, null)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text("Artículo", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("Cantidad", Modifier.width(128.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            Text("Total", Modifier.width(92.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.renglones.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ShoppingCart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Toque un artículo para agregarlo", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn {
                    items(vm.renglones, key = { it.articulo.codigo }) { r ->
                        FilaRenglon(
                            r,
                            onMas = { vm.agregar(r.articulo) },
                            onMenos = { vm.restar(r.articulo.codigo) },
                            onEditar = { onEditarRenglon(r) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider()
        OutlinedTextField(
            value = vm.observacion,
            onValueChange = { vm.observacion = it.take(500) },
            label = { Text("Observación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        LineaTotal("Subtotal", vm.subtotal.monto())
        LineaTotal("IVA (${vm.ivaPorcentaje.cantidad()}%)", vm.iva.monto())
        LineaTotal("Total", vm.total.monto(), destacado = true)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.nuevoPedido() },
                enabled = !vm.guardando,
                modifier = Modifier.height(52.dp),
            ) { Text("Limpiar") }
            Button(
                onClick = { vm.guardar() },
                enabled = !vm.guardando && vm.cliente != null && vm.renglones.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                if (vm.guardando) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text("Guardar pedido")
            }
        }
    }
}

@Composable
private fun FilaRenglon(r: RenglonPedido, onMas: () -> Unit, onMenos: () -> Unit, onEditar: () -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(r.articulo.descripcion, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${r.articulo.codigo}  ·  ${r.articulo.precio.monto()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onMenos, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Remove, "Restar") }
        Text(
            r.cantidad.cantidad(),
            modifier = Modifier
                .width(48.dp)
                .clickable(onClick = onEditar)
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onMas, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Add, "Sumar") }
        Text(r.subtotal.monto(), Modifier.width(92.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun LineaTotal(etiqueta: String, valor: String, destacado: Boolean = false) {
    val estilo = if (destacado) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(etiqueta, Modifier.weight(1f), style = estilo)
        Text(
            valor, style = estilo,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal,
            color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* ================ Panel derecho: líneas arriba y botones de artículos ================ */

@Composable
private fun PanelCatalogo(vm: PedidoViewModel, modifier: Modifier) {
    Column(modifier) {
        if (vm.lineas.isNotEmpty()) {
            val indice = vm.lineas.indexOfFirst { it.codigo == vm.lineaSeleccionada }.coerceAtLeast(0)
            ScrollableTabRow(selectedTabIndex = indice, edgePadding = 8.dp) {
                vm.lineas.forEach { linea ->
                    Tab(
                        selected = linea.codigo == vm.lineaSeleccionada,
                        onClick = { vm.seleccionarLinea(linea.codigo) },
                        text = { Text(linea.descripcion, maxLines = 1) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = vm.filtro,
            onValueChange = { vm.filtro = it },
            placeholder = { Text("Buscar en esta línea…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.cargandoCatalogo -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.lineas.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No se cargaron las líneas de artículos")
                    TextButton(onClick = { vm.cargarLineas() }) { Text("Reintentar") }
                }
                vm.articulosVisibles.isEmpty() ->
                    Text("Sin artículos", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.outline)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vm.articulosVisibles, key = { it.codigo }) { a ->
                        BotonArticulo(
                            articulo = a,
                            urlImagen = vm.urlImagen(a),
                            enPedido = vm.cantidadEnPedido(a.codigo),
                            onClick = { vm.agregar(a) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonArticulo(articulo: Articulo, urlImagen: String?, enPedido: Double, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enPedido > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box {
            val modImagen = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
            if (urlImagen == null) {
                SinImagen(modImagen)
            } else {
                SubcomposeAsyncImage(
                    model = urlImagen,
                    contentDescription = articulo.descripcion,
                    contentScale = ContentScale.Crop,
                    modifier = modImagen,
                    loading = { SinImagen(Modifier.fillMaxSize()) },
                    error = { SinImagen(Modifier.fillMaxSize()) },
                )
            }
            if (enPedido > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Text(
                        enPedido.cantidad(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(
                articulo.descripcion,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    articulo.precio.monto(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Exist. ${articulo.stock.cantidad()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (articulo.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun SinImagen(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

/* ============================== Diálogos ============================== */

@Composable
private fun DialogoClientes(vm: PedidoViewModel, onCerrar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Seleccionar cliente") },
        text = {
            Column(Modifier.width(560.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it; vm.buscarClientes(it) },
                    placeholder = { Text("Código, nombre o RIF (mín. 2 letras)") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth().height(340.dp)) {
                    if (vm.buscandoClientes) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else LazyColumn {
                        items(vm.clientesEncontrados, key = { it.codigo }) { c ->
                            ListItem(
                                headlineContent = { Text(c.nombre) },
                                supportingContent = { Text("${c.codigo}  ·  RIF ${c.rif}") },
                                modifier = Modifier
                                    .clickable { vm.cliente = c; onCerrar() }
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun DialogoCantidad(
    renglon: RenglonPedido,
    onAceptar: (Double) -> Unit,
    onEliminar: () -> Unit,
    onCerrar: () -> Unit,
) {
    var texto by remember { mutableStateOf(renglon.cantidad.cantidad()) }
    // Acepta "2,5" (formato venezolano) o "2.5"
    val valor = texto.trim()
        .let { if (it.contains(',')) it.replace(".", "").replace(',', '.') else it }
        .toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(renglon.articulo.descripcion, maxLines = 2) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Cantidad") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            Button(onClick = { valor?.let(onAceptar) }, enabled = valor != null && valor > 0) { Text("Aceptar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEliminar) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}
