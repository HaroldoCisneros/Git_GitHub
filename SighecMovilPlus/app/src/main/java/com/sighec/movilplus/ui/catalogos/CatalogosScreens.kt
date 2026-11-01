package com.sighec.movilplus.ui.catalogos

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.data.Articulo
import com.sighec.movilplus.data.Banco
import com.sighec.movilplus.data.Cliente
import com.sighec.movilplus.data.CondicionPago
import com.sighec.movilplus.data.Proveedor
import com.sighec.movilplus.ui.componentes.Buscador
import com.sighec.movilplus.ui.componentes.Campo
import com.sighec.movilplus.ui.componentes.CampoNumero
import com.sighec.movilplus.ui.componentes.ConfirmarDialogo
import com.sighec.movilplus.ui.componentes.Fila
import com.sighec.movilplus.ui.componentes.Formulario
import com.sighec.movilplus.ui.componentes.Interruptor
import com.sighec.movilplus.ui.componentes.PantallaBase
import com.sighec.movilplus.ui.componentes.Selector
import com.sighec.movilplus.ui.componentes.Vacio
import com.sighec.movilplus.ui.componentes.aNumero
import com.sighec.movilplus.ui.componentes.aTexto
import com.sighec.movilplus.ui.componentes.cantidad
import com.sighec.movilplus.ui.componentes.ejecutar
import com.sighec.movilplus.ui.componentes.moneda
import com.sighec.movilplus.ui.componentes.repo

/* ======================= ARTÍCULOS ======================= */

@Composable
fun ArticulosScreen(onAtras: () -> Unit, onAbrir: (Long) -> Unit) {
    val repo = repo()
    var q by remember { mutableStateOf("") }
    val lista by remember(q) { repo.db.articulos().observar(q.trim()) }.collectAsState(emptyList())
    PantallaBase("Artículos", onAtras, onAgregar = { onAbrir(0) }) { padding ->
        Column(Modifier.padding(padding)) {
            Buscador(q, { q = it })
            LazyColumn {
                if (lista.isEmpty()) item { Vacio("No hay artículos. Agrega uno con el botón +") }
                items(lista, key = { it.id }) { a ->
                    Fila(
                        titulo = a.descripcion,
                        subtitulo = "${a.codigo} · Existencia: ${a.existencia.cantidad()} ${a.unidad}" + if (!a.activo) " · INACTIVO" else "",
                        derecha = a.precioVenta.moneda(),
                        detalleDerecha = "Costo ${a.costo.moneda()}",
                        colorDerecha = if (a.existencia < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                        onClick = { onAbrir(a.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArticuloFormScreen(id: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Articulo?>(null) }
    var codigo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("PZA") }
    var precio by remember { mutableStateOf("") }
    var costo by remember { mutableStateOf("") }
    var existencia by remember { mutableStateOf("") }
    var impuesto by remember { mutableStateOf("16") }
    var activo by remember { mutableStateOf(true) }
    var baja by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) repo.db.articulos().porId(id)?.let { a ->
            original = a; codigo = a.codigo; descripcion = a.descripcion; unidad = a.unidad
            precio = a.precioVenta.aTexto(); costo = a.costo.aTexto(); existencia = a.existencia.aTexto()
            impuesto = a.tasaImpuesto.aTexto(); activo = a.activo
        }
    }

    PantallaBase(if (id == 0L) "Nuevo artículo" else "Editar artículo", onAtras, acciones = {
        if (original != null) IconButton(onClick = { baja = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
    }) { padding ->
        Formulario(padding) {
            Campo(codigo, { codigo = it.uppercase() }, "Código")
            Campo(descripcion, { descripcion = it }, "Descripción")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(unidad, { unidad = it.uppercase() }, "Unidad", Modifier.weight(1f))
                CampoNumero(impuesto, { impuesto = it }, "% Impuesto", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoNumero(precio, { precio = it }, "Precio de venta", Modifier.weight(1f))
                CampoNumero(costo, { costo = it }, "Costo", Modifier.weight(1f))
            }
            // La existencia sólo se captura al dar de alta; después la mueven ventas y compras.
            CampoNumero(existencia, { existencia = it }, if (id == 0L) "Existencia inicial" else "Existencia (se actualiza con ventas y compras)", habilitado = id == 0L)
            Interruptor("Activo", activo, { activo = it })
            Button(onClick = {
                val a = (original ?: Articulo(codigo = "", descripcion = "")).copy(
                    codigo = codigo.trim(), descripcion = descripcion.trim(), unidad = unidad.trim().ifBlank { "PZA" },
                    precioVenta = precio.aNumero(), costo = costo.aNumero(), tasaImpuesto = impuesto.aNumero(), activo = activo,
                    existencia = if (original == null) existencia.aNumero() else original!!.existencia
                )
                scope.ejecutar(context, exito = "Artículo guardado", alTerminar = onAtras) { repo.guardarArticulo(a) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }
    if (baja) ConfirmarDialogo("Eliminar artículo", "¿Eliminar '${original?.descripcion}'?",
        onConfirmar = { original?.let { a -> scope.ejecutar(context, exito = "Artículo eliminado", alTerminar = onAtras) { repo.eliminarArticulo(a) } } },
        onCancelar = { baja = false })
}

/* ======================= CLIENTES Y PROVEEDORES ======================= */

@Composable
fun ClientesScreen(onAtras: () -> Unit, onAbrir: (Long) -> Unit) {
    val repo = repo()
    var q by remember { mutableStateOf("") }
    val lista by remember(q) { repo.db.clientes().observar(q.trim()) }.collectAsState(emptyList())
    PantallaBase("Clientes", onAtras, onAgregar = { onAbrir(0) }) { padding ->
        Column(Modifier.padding(padding)) {
            Buscador(q, { q = it })
            LazyColumn {
                if (lista.isEmpty()) item { Vacio("No hay clientes registrados") }
                items(lista, key = { it.id }) { c ->
                    Fila(c.nombre, listOf(c.codigo, c.rfc, c.telefono).filter { it.isNotBlank() }.joinToString(" · ") + if (!c.activo) " · INACTIVO" else "",
                        onClick = { onAbrir(c.id) })
                }
            }
        }
    }
}

@Composable
fun ProveedoresScreen(onAtras: () -> Unit, onAbrir: (Long) -> Unit) {
    val repo = repo()
    var q by remember { mutableStateOf("") }
    val lista by remember(q) { repo.db.proveedores().observar(q.trim()) }.collectAsState(emptyList())
    PantallaBase("Proveedores", onAtras, onAgregar = { onAbrir(0) }) { padding ->
        Column(Modifier.padding(padding)) {
            Buscador(q, { q = it })
            LazyColumn {
                if (lista.isEmpty()) item { Vacio("No hay proveedores registrados") }
                items(lista, key = { it.id }) { p ->
                    Fila(p.nombre, listOf(p.codigo, p.rfc, p.telefono).filter { it.isNotBlank() }.joinToString(" · ") + if (!p.activo) " · INACTIVO" else "",
                        onClick = { onAbrir(p.id) })
                }
            }
        }
    }
}

/** Datos comunes de un cliente o proveedor. */
private data class DatosTercero(
    val codigo: String = "", val nombre: String = "", val rfc: String = "", val telefono: String = "",
    val email: String = "", val direccion: String = "", val condicionPagoId: Long? = null,
    val limiteCredito: String = "", val activo: Boolean = true
)

@Composable
private fun FormularioTercero(
    titulo: String,
    esCliente: Boolean,
    puedeEliminar: Boolean,
    datos: DatosTercero,
    onCambio: (DatosTercero) -> Unit,
    onGuardar: () -> Unit,
    onEliminar: () -> Unit,
    onAtras: () -> Unit
) {
    val repo = repo()
    val condiciones by remember { repo.db.condiciones().observar() }.collectAsState(emptyList())
    var baja by remember { mutableStateOf(false) }
    PantallaBase(titulo, onAtras, acciones = {
        if (puedeEliminar) IconButton(onClick = { baja = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
    }) { padding ->
        Formulario(padding) {
            Campo(datos.codigo, { onCambio(datos.copy(codigo = it.uppercase())) }, "Código")
            Campo(datos.nombre, { onCambio(datos.copy(nombre = it)) }, "Nombre / Razón social")
            Campo(datos.rfc, { onCambio(datos.copy(rfc = it.uppercase())) }, "RFC")
            Campo(datos.telefono, { onCambio(datos.copy(telefono = it)) }, "Teléfono", teclado = KeyboardType.Phone)
            Campo(datos.email, { onCambio(datos.copy(email = it)) }, "Correo", teclado = KeyboardType.Email)
            Campo(datos.direccion, { onCambio(datos.copy(direccion = it)) }, "Dirección", lineas = 2)
            Selector("Condición de pago", condiciones, condiciones.firstOrNull { it.id == datos.condicionPagoId },
                { "${it.descripcion} (${it.dias} días)" }, { onCambio(datos.copy(condicionPagoId = it.id)) })
            if (esCliente) CampoNumero(datos.limiteCredito, { onCambio(datos.copy(limiteCredito = it)) }, "Límite de crédito (0 = sin límite)")
            Interruptor("Activo", datos.activo, { onCambio(datos.copy(activo = it)) })
            Button(onClick = onGuardar, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }
    if (baja) ConfirmarDialogo("Eliminar", "¿Eliminar '${datos.nombre}'?", onConfirmar = onEliminar, onCancelar = { baja = false })
}

@Composable
fun ClienteFormScreen(id: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Cliente?>(null) }
    var datos by remember { mutableStateOf(DatosTercero()) }
    LaunchedEffect(id) {
        if (id != 0L) repo.db.clientes().porId(id)?.let { c ->
            original = c
            datos = DatosTercero(c.codigo, c.nombre, c.rfc, c.telefono, c.email, c.direccion, c.condicionPagoId, c.limiteCredito.aTexto(), c.activo)
        }
    }
    FormularioTercero(
        if (id == 0L) "Nuevo cliente" else "Editar cliente", true, original != null, datos, { datos = it },
        onGuardar = {
            val c = (original ?: Cliente(codigo = "", nombre = "")).copy(
                codigo = datos.codigo.trim(), nombre = datos.nombre.trim(), rfc = datos.rfc.trim(), telefono = datos.telefono.trim(),
                email = datos.email.trim(), direccion = datos.direccion.trim(), condicionPagoId = datos.condicionPagoId,
                limiteCredito = datos.limiteCredito.aNumero(), activo = datos.activo
            )
            scope.ejecutar(context, exito = "Cliente guardado", alTerminar = onAtras) { repo.guardarCliente(c) }
        },
        onEliminar = { original?.let { c -> scope.ejecutar(context, exito = "Cliente eliminado", alTerminar = onAtras) { repo.eliminarCliente(c) } } },
        onAtras = onAtras
    )
}

@Composable
fun ProveedorFormScreen(id: Long, onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Proveedor?>(null) }
    var datos by remember { mutableStateOf(DatosTercero()) }
    LaunchedEffect(id) {
        if (id != 0L) repo.db.proveedores().porId(id)?.let { p ->
            original = p
            datos = DatosTercero(p.codigo, p.nombre, p.rfc, p.telefono, p.email, p.direccion, p.condicionPagoId, "", p.activo)
        }
    }
    FormularioTercero(
        if (id == 0L) "Nuevo proveedor" else "Editar proveedor", false, original != null, datos, { datos = it },
        onGuardar = {
            val p = (original ?: Proveedor(codigo = "", nombre = "")).copy(
                codigo = datos.codigo.trim(), nombre = datos.nombre.trim(), rfc = datos.rfc.trim(), telefono = datos.telefono.trim(),
                email = datos.email.trim(), direccion = datos.direccion.trim(), condicionPagoId = datos.condicionPagoId, activo = datos.activo
            )
            scope.ejecutar(context, exito = "Proveedor guardado", alTerminar = onAtras) { repo.guardarProveedor(p) }
        },
        onEliminar = { original?.let { p -> scope.ejecutar(context, exito = "Proveedor eliminado", alTerminar = onAtras) { repo.eliminarProveedor(p) } } },
        onAtras = onAtras
    )
}

/* ======================= CONDICIONES DE PAGO ======================= */

@Composable
fun CondicionesScreen(onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val lista by remember { repo.db.condiciones().observar() }.collectAsState(emptyList())
    var editando by remember { mutableStateOf<CondicionPago?>(null) }

    PantallaBase("Condiciones de pago", onAtras, onAgregar = { editando = CondicionPago(descripcion = "", dias = 0) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (lista.isEmpty()) item { Vacio("Sin condiciones de pago") }
            items(lista, key = { it.id }) { c ->
                Fila(c.descripcion, if (c.dias == 0) "Contado" else "Crédito a ${c.dias} días", onClick = { editando = c })
            }
        }
    }

    editando?.let { c ->
        var descripcion by remember(c) { mutableStateOf(c.descripcion) }
        var dias by remember(c) { mutableStateOf(if (c.id == 0L) "" else c.dias.toString()) }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text(if (c.id == 0L) "Nueva condición" else "Editar condición") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(descripcion, { descripcion = it }, "Descripción")
                    Campo(dias, { v -> if (v.all { it.isDigit() }) dias = v }, "Días de crédito", teclado = KeyboardType.Number)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.ejecutar(context, exito = "Guardado", alTerminar = { editando = null }) {
                        repo.guardarCondicion(c.copy(descripcion = descripcion.trim(), dias = dias.toIntOrNull() ?: 0))
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (c.id != 0L) TextButton(onClick = {
                        scope.ejecutar(context, exito = "Eliminado", alTerminar = { editando = null }) { repo.eliminarCondicion(c) }
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editando = null }) { Text("Cancelar") }
                }
            }
        )
    }
}

/* ======================= BANCOS ======================= */

@Composable
fun BancosScreen(onAtras: () -> Unit) {
    val context = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val lista by remember { repo.db.bancos().observar() }.collectAsState(emptyList())
    var editando by remember { mutableStateOf<Banco?>(null) }

    PantallaBase("Bancos", onAtras, onAgregar = { editando = Banco(nombre = "") }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (lista.isEmpty()) item { Vacio("Registra los bancos con los que trabajas (ej. BBVA, Banorte, Santander)") }
            items(lista, key = { it.id }) { b ->
                Fila(b.nombre, if (b.activo) null else "INACTIVO", onClick = { editando = b })
            }
        }
    }

    editando?.let { b ->
        var nombre by remember(b) { mutableStateOf(b.nombre) }
        var activo by remember(b) { mutableStateOf(b.activo) }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text(if (b.id == 0L) "Nuevo banco" else "Editar banco") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(nombre, { nombre = it }, "Nombre del banco")
                    Interruptor("Activo", activo, { activo = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.ejecutar(context, exito = "Guardado", alTerminar = { editando = null }) {
                        repo.guardarBanco(b.copy(nombre = nombre.trim(), activo = activo))
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (b.id != 0L) TextButton(onClick = {
                        scope.ejecutar(context, exito = "Eliminado", alTerminar = { editando = null }) { repo.eliminarBanco(b) }
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editando = null }) { Text("Cancelar") }
                }
            }
        )
    }
}
