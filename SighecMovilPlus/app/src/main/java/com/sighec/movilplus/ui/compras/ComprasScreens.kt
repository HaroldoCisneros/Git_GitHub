package com.sighec.movilplus.ui.compras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.data.Compra
import com.sighec.movilplus.data.CompraDetalle
import com.sighec.movilplus.data.Estatus
import com.sighec.movilplus.data.FormaPago
import com.sighec.movilplus.data.Pago
import com.sighec.movilplus.seguridad.Modulo
import com.sighec.movilplus.seguridad.Sesion
import com.sighec.movilplus.ui.componentes.Buscador
import com.sighec.movilplus.ui.componentes.Campo
import com.sighec.movilplus.ui.componentes.CampoFecha
import com.sighec.movilplus.ui.componentes.CampoNumero
import com.sighec.movilplus.ui.componentes.ConfirmarDialogo
import com.sighec.movilplus.ui.componentes.Fila
import com.sighec.movilplus.ui.componentes.Formulario
import com.sighec.movilplus.ui.componentes.PantallaBase
import com.sighec.movilplus.ui.componentes.Selector
import com.sighec.movilplus.ui.componentes.TarjetaDato
import com.sighec.movilplus.ui.componentes.Vacio
import com.sighec.movilplus.ui.componentes.aNumero
import com.sighec.movilplus.ui.componentes.aTexto
import com.sighec.movilplus.ui.componentes.cantidad
import com.sighec.movilplus.ui.componentes.ejecutar
import com.sighec.movilplus.ui.componentes.fecha
import com.sighec.movilplus.ui.componentes.inicioDeHoy
import com.sighec.movilplus.ui.componentes.moneda
import com.sighec.movilplus.ui.componentes.repo
import com.sighec.movilplus.ui.theme.Rojo
import com.sighec.movilplus.ui.ventas.CamposFormaPago
import com.sighec.movilplus.ui.ventas.EditorPartidas
import com.sighec.movilplus.ui.ventas.Partida
import com.sighec.movilplus.ui.ventas.Totales
import com.sighec.movilplus.ui.ventas.etiquetaVencimiento

/* ======================= FACTURA DE COMPRAS ======================= */

@Composable
fun ComprasScreen(onAtras: () -> Unit, onNueva: () -> Unit, onVer: (Long) -> Unit) {
    val repo = repo()
    var q by remember { mutableStateOf("") }
    val lista by remember(q) { repo.db.compras().observar(q.trim()) }.collectAsState(emptyList())
    PantallaBase("Factura de compras", onAtras, onAgregar = onNueva) { padding ->
        Column(Modifier.padding(padding)) {
            Buscador(q, { q = it })
            LazyColumn {
                if (lista.isEmpty()) item { Vacio("No hay compras. Registra una con el botón +") }
                items(lista, key = { it.compra.id }) { c ->
                    val cancelada = c.compra.estatus == Estatus.CANCELADA
                    Fila(
                        titulo = "Fact. ${c.compra.folio} · ${c.proveedorNombre}",
                        subtitulo = c.compra.fecha.fecha() + if (cancelada) " · CANCELADA" else if (c.compra.saldo > 0.005) " · Saldo ${c.compra.saldo.moneda()}" else " · Pagada",
                        derecha = c.compra.total.moneda(),
                        colorDerecha = if (cancelada) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        onClick = { onVer(c.compra.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CompraNuevaScreen(onAtras: () -> Unit, onGuardada: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val proveedores by remember { repo.db.proveedores().observarActivos() }.collectAsState(emptyList())
    val condiciones by remember { repo.db.condiciones().observar() }.collectAsState(emptyList())
    val articulos by remember { repo.db.articulos().observarActivos() }.collectAsState(emptyList())

    var proveedorId by remember { mutableStateOf(0L) }
    var condicionId by remember { mutableStateOf<Long?>(null) }
    var folio by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(System.currentTimeMillis()) }
    var notas by remember { mutableStateOf("") }
    var partidas by remember { mutableStateOf(listOf<Partida>()) }
    var guardando by remember { mutableStateOf(false) }

    PantallaBase("Nueva compra", onAtras) { padding ->
        Formulario(padding) {
            Selector("Proveedor", proveedores, proveedores.firstOrNull { it.id == proveedorId }, { "${it.codigo} · ${it.nombre}" }, { p ->
                proveedorId = p.id
                if (p.condicionPagoId != null) condicionId = p.condicionPagoId
            })
            Campo(folio, { folio = it.uppercase() }, "Número de factura del proveedor")
            Selector("Condición de pago", condiciones, condiciones.firstOrNull { it.id == condicionId },
                { "${it.descripcion} (${it.dias} días)" }, { condicionId = it.id })
            CampoFecha("Fecha", fecha, { fecha = it })
            EditorPartidas(articulos, partidas, { partidas = it }, esCompra = true)
            Campo(notas, { notas = it }, "Notas", lineas = 2)
            Button(
                enabled = !guardando,
                onClick = {
                    val dias = condiciones.firstOrNull { it.id == condicionId }?.dias ?: 0
                    val compra = Compra(
                        folio = folio.trim(), fecha = fecha, proveedorId = proveedorId, condicionPagoId = condicionId, diasCredito = dias,
                        vencimiento = fecha, subtotal = 0.0, impuesto = 0.0, total = 0.0, saldo = 0.0, notas = notas.trim()
                    )
                    val detalles = partidas.map {
                        CompraDetalle(articuloId = it.articulo.id, descripcion = it.articulo.descripcion, cantidad = it.cantidad,
                            costo = it.precio, tasaImpuesto = it.tasaImpuesto, importe = it.importe)
                    }
                    guardando = true
                    scope.ejecutar(context, exito = "Compra registrada") {
                        try { onGuardada(repo.registrarCompra(compra, detalles)) } finally { guardando = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar compra") }
        }
    }
}

@Composable
fun CompraDetalleScreen(id: Long, onAtras: () -> Unit, onPagar: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val datos by remember(id) { repo.db.compras().observarPorId(id) }.collectAsState(null)
    val detalles by remember(id) { repo.db.compras().observarDetalles(id) }.collectAsState(emptyList())
    val pagos by remember(id) { repo.db.pagos().observarDeCompra(id) }.collectAsState(emptyList())
    var cancelar by remember { mutableStateOf(false) }

    val c = datos?.compra
    PantallaBase(if (c != null) "Compra ${c.folio}" else "Compra", onAtras) { padding ->
        if (c == null) { Vacio("Cargando…", Modifier.padding(padding)); return@PantallaBase }
        Formulario(padding) {
            Text(datos!!.proveedorNombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Fecha: ${c.fecha.fecha()}   Vence: ${c.vencimiento.fecha()}")
            Text("Estatus: ${c.estatus}" + if (c.estatus == Estatus.VIGENTE && c.saldo > 0.005) " · ${etiquetaVencimiento(c.vencimiento)}" else "",
                color = if (c.estatus == Estatus.CANCELADA) Rojo else Color.Unspecified)
            if (c.notas.isNotBlank()) Text("Notas: ${c.notas}")
            Text("Registró: ${c.usuario}", style = MaterialTheme.typography.bodySmall)
            detalles.forEach { d ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${d.cantidad.cantidad()} × ${d.descripcion} @ ${d.costo.moneda()}", Modifier.weight(1f))
                    Text(d.importe.moneda())
                }
            }
            Totales(c.subtotal, c.impuesto, if (c.estatus == Estatus.VIGENTE) c.saldo else null)

            if (pagos.isNotEmpty()) {
                Text("Pagos aplicados", style = MaterialTheme.typography.titleMedium)
                pagos.forEach { p ->
                    Text("${p.pago.fecha.fecha()} · ${p.pago.formaPago} · ${p.pago.importe.moneda()} ${p.pago.referencia}")
                }
            }
            if (c.estatus == Estatus.VIGENTE) {
                if (c.saldo > 0.005 && Sesion.puede(Modulo.PAGOS)) {
                    Button(onClick = { onPagar(c.id) }, Modifier.fillMaxWidth()) { Text("Registrar pago") }
                }
                if (pagos.isEmpty()) {
                    OutlinedButton(onClick = { cancelar = true }, Modifier.fillMaxWidth()) { Text("Cancelar compra", color = Rojo) }
                }
            }
        }
    }
    if (cancelar) ConfirmarDialogo("Cancelar compra", "La mercancía se descontará del inventario. ¿Continuar?",
        onConfirmar = { scope.ejecutar(context, exito = "Compra cancelada") { repo.cancelarCompra(id) } },
        onCancelar = { cancelar = false })
}

/* ======================= PAGOS ======================= */

@Composable
fun PagosScreen(onAtras: () -> Unit, onNuevo: () -> Unit) {
    val repo = repo()
    val lista by remember { repo.db.pagos().observar() }.collectAsState(emptyList())
    PantallaBase("Pagos a proveedores", onAtras, onAgregar = onNuevo) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (lista.isEmpty()) item { Vacio("No hay pagos registrados") }
            items(lista, key = { it.pago.id }) { p ->
                Fila(
                    titulo = p.proveedorNombre,
                    subtitulo = "${p.pago.fecha.fecha()} · Fact. ${p.compraFolio} · ${p.pago.formaPago}" +
                        if (p.pago.referencia.isNotBlank()) " · ${p.pago.referencia}" else "",
                    derecha = p.pago.importe.moneda(),
                    colorDerecha = Rojo
                )
            }
        }
    }
}

@Composable
fun PagoNuevoScreen(compraId: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val proveedores by remember { repo.db.proveedores().observar("") }.collectAsState(emptyList())
    val cuentas by remember { repo.db.cuentas().observarActivas() }.collectAsState(emptyList())

    var proveedorId by remember { mutableStateOf(0L) }
    var compraSel by remember { mutableStateOf(compraId) }
    var importe by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(System.currentTimeMillis()) }
    var formaPago by remember { mutableStateOf(FormaPago.TRANSFERENCIA) }
    var cuentaId by remember { mutableStateOf<Long?>(null) }
    var referencia by remember { mutableStateOf("") }

    LaunchedEffect(compraId) {
        if (compraId != 0L) repo.db.compras().porId(compraId)?.let { proveedorId = it.proveedorId; importe = it.saldo.aTexto() }
    }
    val pendientes by remember(proveedorId) { repo.db.compras().observarPendientes(proveedorId) }.collectAsState(emptyList())
    val compra = pendientes.firstOrNull { it.compra.id == compraSel }?.compra

    PantallaBase("Nuevo pago", onAtras) { padding ->
        Formulario(padding) {
            Selector("Proveedor", proveedores, proveedores.firstOrNull { it.id == proveedorId }, { it.nombre }, {
                proveedorId = it.id; compraSel = 0; importe = ""
            })
            Selector("Factura de compra pendiente", pendientes, pendientes.firstOrNull { it.compra.id == compraSel },
                { "Fact. ${it.compra.folio} · Saldo ${it.compra.saldo.moneda()} · Vence ${it.compra.vencimiento.fecha()}" },
                { compraSel = it.compra.id; proveedorId = it.compra.proveedorId; importe = it.compra.saldo.aTexto() })
            if (proveedorId != 0L && pendientes.isEmpty()) Text("El proveedor no tiene compras pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            compra?.let { Text("Saldo de la compra: ${it.saldo.moneda()}", fontWeight = FontWeight.Bold) }
            CampoFecha("Fecha", fecha, { fecha = it })
            CampoNumero(importe, { importe = it }, "Importe")
            CamposFormaPago(formaPago, { formaPago = it }, cuentas, cuentaId, { cuentaId = it })
            Campo(referencia, { referencia = it }, "Referencia")
            Button(onClick = {
                val p = Pago(fecha = fecha, proveedorId = proveedorId, compraId = compraSel, importe = importe.aNumero(),
                    formaPago = formaPago, cuentaId = if (formaPago == FormaPago.EFECTIVO) null else cuentaId, referencia = referencia.trim())
                scope.ejecutar(context, exito = "Pago registrado", alTerminar = onAtras) { repo.registrarPago(p) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Registrar pago") }
        }
    }
}

/* ======================= CUENTAS POR PAGAR ======================= */

@Composable
fun CxPScreen(onAtras: () -> Unit, onVerCompra: (Long) -> Unit) {
    val repo = repo()
    val hoy = remember { inicioDeHoy() }
    var proveedorId by remember { mutableStateOf(0L) }
    val saldos by remember { repo.db.compras().observarSaldosPorProveedor(hoy) }.collectAsState(emptyList())
    val documentos by remember(proveedorId) { repo.db.compras().observarPendientes(proveedorId) }.collectAsState(emptyList())

    PantallaBase("Cuentas por pagar", onAtras) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TarjetaDato("Total por pagar", saldos.sumOf { it.saldo }.moneda(), Modifier.weight(1f))
                        TarjetaDato("Vencido", saldos.sumOf { it.vencido }.moneda(), Modifier.weight(1f), color = Rojo)
                    }
                    if (proveedorId != 0L) FilterChip(selected = true, onClick = { proveedorId = 0 },
                        label = { Text("Proveedor: ${saldos.firstOrNull { it.id == proveedorId }?.nombre ?: ""}  ✕") })
                }
            }
            if (proveedorId == 0L) {
                item { Text("Saldos por proveedor", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium) }
                if (saldos.isEmpty()) item { Vacio("No hay saldos pendientes") }
                items(saldos, key = { it.id }) { s ->
                    Fila(s.nombre, "${s.documentos} factura(s)" + if (s.vencido > 0.005) " · Vencido ${s.vencido.moneda()}" else "",
                        derecha = s.saldo.moneda(), colorDerecha = if (s.vencido > 0.005) Rojo else Color.Unspecified,
                        onClick = { proveedorId = s.id })
                }
            } else {
                items(documentos, key = { it.compra.id }) { d ->
                    val vencida = d.compra.vencimiento < hoy
                    Fila("Fact. ${d.compra.folio} · ${d.compra.fecha.fecha()}",
                        "Total ${d.compra.total.moneda()} · ${etiquetaVencimiento(d.compra.vencimiento)}",
                        derecha = d.compra.saldo.moneda(), colorDerecha = if (vencida) Rojo else Color.Unspecified,
                        onClick = { onVerCompra(d.compra.id) })
                }
            }
        }
    }
}
