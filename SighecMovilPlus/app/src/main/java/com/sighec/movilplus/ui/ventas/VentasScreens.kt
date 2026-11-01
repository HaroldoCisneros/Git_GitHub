package com.sighec.movilplus.ui.ventas

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
import com.sighec.movilplus.data.Cobro
import com.sighec.movilplus.data.Estatus
import com.sighec.movilplus.data.Factura
import com.sighec.movilplus.data.FacturaDetalle
import com.sighec.movilplus.data.FormaPago
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
import com.sighec.movilplus.ui.theme.Verde

/* ======================= FACTURACIÓN ======================= */

@Composable
fun FacturasScreen(onAtras: () -> Unit, onNueva: () -> Unit, onVer: (Long) -> Unit) {
    val repo = repo()
    var q by remember { mutableStateOf("") }
    val lista by remember(q) { repo.db.facturas().observar(q.trim()) }.collectAsState(emptyList())
    PantallaBase("Facturación", onAtras, onAgregar = onNueva) { padding ->
        Column(Modifier.padding(padding)) {
            Buscador(q, { q = it })
            LazyColumn {
                if (lista.isEmpty()) item { Vacio("No hay facturas. Crea una con el botón +") }
                items(lista, key = { it.factura.id }) { f ->
                    val cancelada = f.factura.estatus == Estatus.CANCELADA
                    Fila(
                        titulo = "Folio ${f.factura.folio} · ${f.clienteNombre}",
                        subtitulo = f.factura.fecha.fecha() + if (cancelada) " · CANCELADA" else if (f.factura.saldo > 0.005) " · Saldo ${f.factura.saldo.moneda()}" else " · Pagada",
                        derecha = f.factura.total.moneda(),
                        colorDerecha = if (cancelada) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        onClick = { onVer(f.factura.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun FacturaNuevaScreen(onAtras: () -> Unit, onGuardada: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val clientes by remember { repo.db.clientes().observarActivos() }.collectAsState(emptyList())
    val condiciones by remember { repo.db.condiciones().observar() }.collectAsState(emptyList())
    val articulos by remember { repo.db.articulos().observarActivos() }.collectAsState(emptyList())

    var clienteId by remember { mutableStateOf(0L) }
    var condicionId by remember { mutableStateOf<Long?>(null) }
    var fecha by remember { mutableStateOf(System.currentTimeMillis()) }
    var notas by remember { mutableStateOf("") }
    var partidas by remember { mutableStateOf(listOf<Partida>()) }
    var guardando by remember { mutableStateOf(false) }

    PantallaBase("Nueva factura", onAtras) { padding ->
        Formulario(padding) {
            Selector("Cliente", clientes, clientes.firstOrNull { it.id == clienteId }, { "${it.codigo} · ${it.nombre}" }, { c ->
                clienteId = c.id
                if (c.condicionPagoId != null) condicionId = c.condicionPagoId
            })
            Selector("Condición de pago", condiciones, condiciones.firstOrNull { it.id == condicionId },
                { "${it.descripcion} (${it.dias} días)" }, { condicionId = it.id })
            CampoFecha("Fecha", fecha, { fecha = it })
            EditorPartidas(articulos, partidas, { partidas = it }, esCompra = false)
            Campo(notas, { notas = it }, "Notas", lineas = 2)
            Button(
                enabled = !guardando,
                onClick = {
                    val dias = condiciones.firstOrNull { it.id == condicionId }?.dias ?: 0
                    val factura = Factura(
                        folio = 0, fecha = fecha, clienteId = clienteId, condicionPagoId = condicionId, diasCredito = dias,
                        vencimiento = fecha, subtotal = 0.0, impuesto = 0.0, total = 0.0, saldo = 0.0, notas = notas.trim()
                    )
                    val detalles = partidas.map {
                        FacturaDetalle(articuloId = it.articulo.id, descripcion = it.articulo.descripcion, cantidad = it.cantidad,
                            precio = it.precio, tasaImpuesto = it.tasaImpuesto, importe = it.importe)
                    }
                    guardando = true
                    scope.ejecutar(context, exito = "Factura guardada") {
                        try { onGuardada(repo.registrarFactura(factura, detalles)) } finally { guardando = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar factura") }
        }
    }
}

@Composable
fun FacturaDetalleScreen(id: Long, onAtras: () -> Unit, onCobrar: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val datos by remember(id) { repo.db.facturas().observarPorId(id) }.collectAsState(null)
    val detalles by remember(id) { repo.db.facturas().observarDetalles(id) }.collectAsState(emptyList())
    val cobros by remember(id) { repo.db.cobros().observarDeFactura(id) }.collectAsState(emptyList())
    var cancelar by remember { mutableStateOf(false) }

    val f = datos?.factura
    PantallaBase(if (f != null) "Factura ${f.folio}" else "Factura", onAtras) { padding ->
        if (f == null) { Vacio("Cargando…", Modifier.padding(padding)); return@PantallaBase }
        Formulario(padding) {
            Text(datos!!.clienteNombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Fecha: ${f.fecha.fecha()}   Vence: ${f.vencimiento.fecha()}")
            Text("Estatus: ${f.estatus}" + if (f.estatus == Estatus.VIGENTE && f.saldo > 0.005) " · ${etiquetaVencimiento(f.vencimiento)}" else "",
                color = if (f.estatus == Estatus.CANCELADA) Rojo else Color.Unspecified)
            if (f.notas.isNotBlank()) Text("Notas: ${f.notas}")
            Text("Elaboró: ${f.usuario}", style = MaterialTheme.typography.bodySmall)
            detalles.forEach { d ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${d.cantidad.cantidad()} × ${d.descripcion} @ ${d.precio.moneda()}", Modifier.weight(1f))
                    Text(d.importe.moneda())
                }
            }
            Totales(f.subtotal, f.impuesto, if (f.estatus == Estatus.VIGENTE) f.saldo else null)

            if (cobros.isNotEmpty()) {
                Text("Cobros aplicados", style = MaterialTheme.typography.titleMedium)
                cobros.forEach { c ->
                    Text("${c.cobro.fecha.fecha()} · ${c.cobro.formaPago} · ${c.cobro.importe.moneda()} ${c.cobro.referencia}")
                }
            }
            if (f.estatus == Estatus.VIGENTE) {
                if (f.saldo > 0.005 && Sesion.puede(Modulo.COBROS)) {
                    Button(onClick = { onCobrar(f.id) }, Modifier.fillMaxWidth()) { Text("Registrar cobro") }
                }
                if (cobros.isEmpty()) {
                    OutlinedButton(onClick = { cancelar = true }, Modifier.fillMaxWidth()) { Text("Cancelar factura", color = Rojo) }
                }
            }
        }
    }
    if (cancelar) ConfirmarDialogo("Cancelar factura", "La mercancía regresará al inventario. ¿Continuar?",
        onConfirmar = { scope.ejecutar(context, exito = "Factura cancelada") { repo.cancelarFactura(id) } },
        onCancelar = { cancelar = false })
}

/* ======================= COBROS ======================= */

@Composable
fun CobrosScreen(onAtras: () -> Unit, onNuevo: () -> Unit) {
    val repo = repo()
    val lista by remember { repo.db.cobros().observar() }.collectAsState(emptyList())
    PantallaBase("Cobros", onAtras, onAgregar = onNuevo) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (lista.isEmpty()) item { Vacio("No hay cobros registrados") }
            items(lista, key = { it.cobro.id }) { c ->
                Fila(
                    titulo = c.clienteNombre,
                    subtitulo = "${c.cobro.fecha.fecha()} · Factura ${c.facturaFolio} · ${c.cobro.formaPago}" +
                        if (c.cobro.referencia.isNotBlank()) " · ${c.cobro.referencia}" else "",
                    derecha = c.cobro.importe.moneda(),
                    colorDerecha = Verde
                )
            }
        }
    }
}

@Composable
fun CobroNuevoScreen(facturaId: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val clientes by remember { repo.db.clientes().observar("") }.collectAsState(emptyList())
    val cuentas by remember { repo.db.cuentas().observarActivas() }.collectAsState(emptyList())

    var clienteId by remember { mutableStateOf(0L) }
    var facturaSel by remember { mutableStateOf(facturaId) }
    var importe by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(System.currentTimeMillis()) }
    var formaPago by remember { mutableStateOf(FormaPago.EFECTIVO) }
    var cuentaId by remember { mutableStateOf<Long?>(null) }
    var referencia by remember { mutableStateOf("") }

    LaunchedEffect(facturaId) {
        if (facturaId != 0L) repo.db.facturas().porId(facturaId)?.let { clienteId = it.clienteId; importe = it.saldo.aTexto() }
    }
    val pendientes by remember(clienteId) { repo.db.facturas().observarPendientes(clienteId) }.collectAsState(emptyList())
    val factura = pendientes.firstOrNull { it.factura.id == facturaSel }?.factura

    PantallaBase("Nuevo cobro", onAtras) { padding ->
        Formulario(padding) {
            Selector("Cliente", clientes, clientes.firstOrNull { it.id == clienteId }, { it.nombre }, {
                clienteId = it.id; facturaSel = 0; importe = ""
            })
            Selector("Factura pendiente", pendientes, pendientes.firstOrNull { it.factura.id == facturaSel },
                { "Folio ${it.factura.folio} · Saldo ${it.factura.saldo.moneda()} · Vence ${it.factura.vencimiento.fecha()}" },
                { facturaSel = it.factura.id; clienteId = it.factura.clienteId; importe = it.factura.saldo.aTexto() })
            if (clienteId != 0L && pendientes.isEmpty()) Text("El cliente no tiene facturas pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            factura?.let { Text("Saldo de la factura: ${it.saldo.moneda()}", fontWeight = FontWeight.Bold) }
            CampoFecha("Fecha", fecha, { fecha = it })
            CampoNumero(importe, { importe = it }, "Importe")
            CamposFormaPago(formaPago, { formaPago = it }, cuentas, cuentaId, { cuentaId = it })
            Campo(referencia, { referencia = it }, "Referencia")
            Button(onClick = {
                val c = Cobro(fecha = fecha, clienteId = clienteId, facturaId = facturaSel, importe = importe.aNumero(),
                    formaPago = formaPago, cuentaId = if (formaPago == FormaPago.EFECTIVO) null else cuentaId, referencia = referencia.trim())
                scope.ejecutar(context, exito = "Cobro registrado", alTerminar = onAtras) { repo.registrarCobro(c) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Registrar cobro") }
        }
    }
}

/* ======================= CUENTAS POR COBRAR ======================= */

@Composable
fun CxCScreen(onAtras: () -> Unit, onVerFactura: (Long) -> Unit) {
    val repo = repo()
    val hoy = remember { inicioDeHoy() }
    var clienteId by remember { mutableStateOf(0L) }
    val saldos by remember { repo.db.facturas().observarSaldosPorCliente(hoy) }.collectAsState(emptyList())
    val documentos by remember(clienteId) { repo.db.facturas().observarPendientes(clienteId) }.collectAsState(emptyList())

    PantallaBase("Cuentas por cobrar", onAtras) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TarjetaDato("Total por cobrar", saldos.sumOf { it.saldo }.moneda(), Modifier.weight(1f))
                        TarjetaDato("Vencido", saldos.sumOf { it.vencido }.moneda(), Modifier.weight(1f), color = Rojo)
                    }
                    if (clienteId != 0L) FilterChip(selected = true, onClick = { clienteId = 0 },
                        label = { Text("Cliente: ${saldos.firstOrNull { it.id == clienteId }?.nombre ?: ""}  ✕") })
                }
            }
            if (clienteId == 0L) {
                item { Text("Saldos por cliente", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium) }
                if (saldos.isEmpty()) item { Vacio("No hay saldos pendientes") }
                items(saldos, key = { it.id }) { s ->
                    Fila(s.nombre, "${s.documentos} factura(s)" + if (s.vencido > 0.005) " · Vencido ${s.vencido.moneda()}" else "",
                        derecha = s.saldo.moneda(), colorDerecha = if (s.vencido > 0.005) Rojo else Color.Unspecified,
                        onClick = { clienteId = s.id })
                }
            } else {
                items(documentos, key = { it.factura.id }) { d ->
                    val vencida = d.factura.vencimiento < hoy
                    Fila("Folio ${d.factura.folio} · ${d.factura.fecha.fecha()}",
                        "Total ${d.factura.total.moneda()} · ${etiquetaVencimiento(d.factura.vencimiento)}",
                        derecha = d.factura.saldo.moneda(), colorDerecha = if (vencida) Rojo else Color.Unspecified,
                        onClick = { onVerFactura(d.factura.id) })
                }
            }
        }
    }
}
