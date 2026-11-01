package com.sighec.movilplus.ui.tesoreria

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.data.CuentaBancaria
import com.sighec.movilplus.data.MovimientoBanco
import com.sighec.movilplus.data.MovimientoCaja
import com.sighec.movilplus.data.TipoMovimiento
import com.sighec.movilplus.ui.componentes.Campo
import com.sighec.movilplus.ui.componentes.CampoFecha
import com.sighec.movilplus.ui.componentes.CampoNumero
import com.sighec.movilplus.ui.componentes.ConfirmarDialogo
import com.sighec.movilplus.ui.componentes.Fila
import com.sighec.movilplus.ui.componentes.Formulario
import com.sighec.movilplus.ui.componentes.Interruptor
import com.sighec.movilplus.ui.componentes.PantallaBase
import com.sighec.movilplus.ui.componentes.Selector
import com.sighec.movilplus.ui.componentes.TarjetaDato
import com.sighec.movilplus.ui.componentes.Vacio
import com.sighec.movilplus.ui.componentes.aNumero
import com.sighec.movilplus.ui.componentes.aTexto
import com.sighec.movilplus.ui.componentes.ejecutar
import com.sighec.movilplus.ui.componentes.fecha
import com.sighec.movilplus.ui.componentes.finDeHoy
import com.sighec.movilplus.ui.componentes.inicioDeHoy
import com.sighec.movilplus.ui.componentes.inicioDeMes
import com.sighec.movilplus.ui.componentes.moneda
import com.sighec.movilplus.ui.componentes.repo
import com.sighec.movilplus.ui.theme.Rojo
import com.sighec.movilplus.ui.theme.Verde

/* ======================= CUENTAS BANCARIAS ======================= */

@Composable
fun CuentasScreen(onAtras: () -> Unit, onAbrir: (Long) -> Unit, onMovimientos: (Long) -> Unit) {
    val repo = repo()
    val lista by remember { repo.db.cuentas().observar() }.collectAsState(emptyList())
    PantallaBase("Cuentas bancarias", onAtras, onAgregar = { onAbrir(0) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                TarjetaDato("Saldo total en bancos", lista.filter { it.cuenta.activo }.sumOf { it.cuenta.saldo }.moneda(),
                    Modifier.fillMaxWidth().padding(12.dp))
            }
            if (lista.isEmpty()) item { Vacio("Primero registra un banco y después sus cuentas") }
            items(lista, key = { it.cuenta.id }) { c ->
                Fila(
                    titulo = "${c.bancoNombre} — ${c.cuenta.alias}",
                    subtitulo = listOf(c.cuenta.numero, if (c.cuenta.activo) "" else "INACTIVA").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                    derecha = c.cuenta.saldo.moneda(),
                    colorDerecha = if (c.cuenta.saldo < 0) Rojo else androidx.compose.ui.graphics.Color.Unspecified,
                    detalleDerecha = "Ver movimientos",
                    onClick = { onMovimientos(c.cuenta.id) }
                )
            }
        }
    }
}

@Composable
fun CuentaFormScreen(id: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val bancos by remember { repo.db.bancos().observar() }.collectAsState(emptyList())
    var original by remember { mutableStateOf<CuentaBancaria?>(null) }
    var bancoId by remember { mutableStateOf(0L) }
    var alias by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var clabe by remember { mutableStateOf("") }
    var saldoInicial by remember { mutableStateOf("") }
    var activo by remember { mutableStateOf(true) }
    var baja by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) repo.db.cuentas().porId(id)?.let { c ->
            original = c; bancoId = c.bancoId; alias = c.alias; numero = c.numero; clabe = c.clabe
            saldoInicial = c.saldoInicial.aTexto(); activo = c.activo
        }
    }

    PantallaBase(if (id == 0L) "Nueva cuenta" else "Editar cuenta", onAtras, acciones = {
        if (original != null) IconButton(onClick = { baja = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
    }) { padding ->
        Formulario(padding) {
            Selector("Banco", bancos.filter { it.activo || it.id == bancoId }, bancos.firstOrNull { it.id == bancoId }, { it.nombre }, { bancoId = it.id })
            Campo(alias, { alias = it }, "Nombre de la cuenta (ej. Cheques principal)")
            Campo(numero, { numero = it }, "Número de cuenta")
            Campo(clabe, { clabe = it }, "CLABE")
            CampoNumero(saldoInicial, { saldoInicial = it }, "Saldo inicial")
            Interruptor("Activa", activo, { activo = it })
            Button(onClick = {
                val c = (original ?: CuentaBancaria(bancoId = 0, alias = "")).copy(
                    bancoId = bancoId, alias = alias.trim(), numero = numero.trim(), clabe = clabe.trim(),
                    saldoInicial = saldoInicial.aNumero(), activo = activo
                )
                scope.ejecutar(context, exito = "Cuenta guardada", alTerminar = onAtras) { repo.guardarCuenta(c) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }
    if (baja) ConfirmarDialogo("Eliminar cuenta", "¿Eliminar la cuenta '${original?.alias}'?",
        onConfirmar = { original?.let { c -> scope.ejecutar(context, exito = "Cuenta eliminada", alTerminar = onAtras) { repo.eliminarCuenta(c) } } },
        onCancelar = { baja = false })
}

@Composable
fun CuentaMovimientosScreen(id: Long, onAtras: () -> Unit, onEditar: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val cuenta by remember(id) { repo.db.cuentas().observarPorId(id) }.collectAsState(null)
    val movimientos by remember(id) { repo.db.movimientosBanco().observarDeCuenta(id) }.collectAsState(emptyList())
    var nuevoTipo by remember { mutableStateOf<String?>(null) }

    PantallaBase(cuenta?.alias ?: "Movimientos", onAtras, acciones = {
        TextButton(onClick = { onEditar(id) }) { Text("Editar") }
    }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TarjetaDato("Saldo actual", (cuenta?.saldo ?: 0.0).moneda(), Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nuevoTipo = TipoMovimiento.ENTRADA }, Modifier.weight(1f)) { Text("Depósito") }
                        OutlinedButton(onClick = { nuevoTipo = TipoMovimiento.SALIDA }, Modifier.weight(1f)) { Text("Retiro") }
                    }
                }
            }
            if (movimientos.isEmpty()) item { Vacio("Sin movimientos") }
            items(movimientos, key = { it.id }) { m ->
                val entrada = m.tipo == TipoMovimiento.ENTRADA
                Fila(
                    titulo = m.concepto,
                    subtitulo = listOf(m.fecha.fecha(), m.referencia, m.origen, m.usuario).filter { it.isNotBlank() }.joinToString(" · "),
                    derecha = (if (entrada) "+" else "-") + m.importe.moneda(),
                    colorDerecha = if (entrada) Verde else Rojo
                )
            }
        }
    }

    nuevoTipo?.let { tipo ->
        DialogoMovimiento(
            titulo = if (tipo == TipoMovimiento.ENTRADA) "Depósito" else "Retiro",
            onCancelar = { nuevoTipo = null },
            onGuardar = { fecha, concepto, importe, referencia ->
                scope.ejecutar(context, exito = "Movimiento registrado", alTerminar = { nuevoTipo = null }) {
                    repo.registrarMovimientoBanco(MovimientoBanco(cuentaId = id, fecha = fecha, tipo = tipo, concepto = concepto, importe = importe, referencia = referencia))
                }
            }
        )
    }
}

@Composable
private fun DialogoMovimiento(titulo: String, onCancelar: () -> Unit, onGuardar: (Long, String, Double, String) -> Unit) {
    var fecha by remember { mutableStateOf(System.currentTimeMillis()) }
    var concepto by remember { mutableStateOf("") }
    var importe by remember { mutableStateOf("") }
    var referencia by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoFecha("Fecha", fecha, { fecha = it })
                Campo(concepto, { concepto = it }, "Concepto")
                CampoNumero(importe, { importe = it }, "Importe")
                Campo(referencia, { referencia = it }, "Referencia")
            }
        },
        confirmButton = { TextButton(onClick = { onGuardar(fecha, concepto.trim(), importe.aNumero(), referencia.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/* ======================= CAJA ======================= */

@Composable
fun CajaScreen(onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    var periodo by remember { mutableStateOf("Hoy") }
    val desde = when (periodo) { "Hoy" -> inicioDeHoy(); "Mes" -> inicioDeMes(); else -> 0L }
    val hasta = finDeHoy() + 365L * 24 * 60 * 60 * 1000 // incluye movimientos con fecha futura
    val movimientos by remember(desde) { repo.db.caja().observar(desde, hasta) }.collectAsState(emptyList())
    val saldo by remember { repo.db.caja().observarSaldo() }.collectAsState(0.0)
    var nuevoTipo by remember { mutableStateOf<String?>(null) }

    val entradas = movimientos.filter { it.tipo == TipoMovimiento.ENTRADA }.sumOf { it.importe }
    val salidas = movimientos.filter { it.tipo == TipoMovimiento.SALIDA }.sumOf { it.importe }

    PantallaBase("Caja", onAtras) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TarjetaDato("Saldo en caja", saldo.moneda(), Modifier.fillMaxWidth(), color = if (saldo < 0) Rojo else androidx.compose.ui.graphics.Color.Unspecified)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Hoy", "Mes", "Todo").forEach { p ->
                            FilterChip(selected = periodo == p, onClick = { periodo = p }, label = { Text(p) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TarjetaDato("Entradas", entradas.moneda(), Modifier.weight(1f), color = Verde)
                        TarjetaDato("Salidas", salidas.moneda(), Modifier.weight(1f), color = Rojo)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nuevoTipo = TipoMovimiento.ENTRADA }, Modifier.weight(1f)) { Text("Ingreso") }
                        OutlinedButton(onClick = { nuevoTipo = TipoMovimiento.SALIDA }, Modifier.weight(1f)) { Text("Egreso") }
                    }
                }
            }
            if (movimientos.isEmpty()) item { Vacio("Sin movimientos en el periodo") }
            items(movimientos, key = { it.id }) { m ->
                val entrada = m.tipo == TipoMovimiento.ENTRADA
                Fila(
                    titulo = m.concepto,
                    subtitulo = listOf(m.fecha.fecha(), m.referencia, m.origen, m.usuario).filter { it.isNotBlank() }.joinToString(" · "),
                    derecha = (if (entrada) "+" else "-") + m.importe.moneda(),
                    colorDerecha = if (entrada) Verde else Rojo
                )
            }
        }
    }

    nuevoTipo?.let { tipo ->
        DialogoMovimiento(
            titulo = if (tipo == TipoMovimiento.ENTRADA) "Ingreso a caja" else "Egreso de caja",
            onCancelar = { nuevoTipo = null },
            onGuardar = { fecha, concepto, importe, referencia ->
                scope.ejecutar(context, exito = "Movimiento registrado", alTerminar = { nuevoTipo = null }) {
                    repo.registrarMovimientoCaja(MovimientoCaja(fecha = fecha, tipo = tipo, concepto = concepto, importe = importe, referencia = referencia))
                }
            }
        )
    }
}
