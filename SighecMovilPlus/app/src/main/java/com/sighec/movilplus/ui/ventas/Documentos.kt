package com.sighec.movilplus.ui.ventas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.data.Articulo
import com.sighec.movilplus.data.CuentaConBanco
import com.sighec.movilplus.data.FormaPago
import com.sighec.movilplus.data.r2
import com.sighec.movilplus.ui.componentes.Campo
import com.sighec.movilplus.ui.componentes.CampoNumero
import com.sighec.movilplus.ui.componentes.Selector
import com.sighec.movilplus.ui.componentes.aNumero
import com.sighec.movilplus.ui.componentes.aTexto
import com.sighec.movilplus.ui.componentes.cantidad
import com.sighec.movilplus.ui.componentes.moneda
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Renglón de una factura de venta o de compra mientras se captura. */
data class Partida(val articulo: Articulo, val cantidad: Double, val precio: Double, val tasaImpuesto: Double) {
    val importe: Double get() = (cantidad * precio).r2()
    val impuesto: Double get() = importe * tasaImpuesto / 100.0
}

/**
 * Captura de renglones. Si [esCompra] el precio sugerido es el costo del artículo,
 * de lo contrario el precio de venta.
 */
@Composable
fun EditorPartidas(articulos: List<Articulo>, partidas: List<Partida>, onCambio: (List<Partida>) -> Unit, esCompra: Boolean) {
    var agregando by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Artículos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { agregando = true }) {
            Icon(Icons.Filled.Add, null); Text("Agregar")
        }
    }
    if (partidas.isEmpty()) Text("Sin artículos", color = MaterialTheme.colorScheme.onSurfaceVariant)
    partidas.forEachIndexed { i, p ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(p.articulo.descripcion, fontWeight = FontWeight.Medium)
                    Text("${p.cantidad.cantidad()} ${p.articulo.unidad} × ${p.precio.moneda()}  (IVA ${p.tasaImpuesto.cantidad()}%)",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(p.importe.moneda(), fontWeight = FontWeight.Bold)
                IconButton(onClick = { onCambio(partidas.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Close, "Quitar") }
            }
        }
    }
    Totales(partidas.sumOf { it.importe }, partidas.sumOf { it.impuesto })

    if (agregando) {
        var filtro by remember { mutableStateOf("") }
        var articulo by remember { mutableStateOf<Articulo?>(null) }
        var cantidad by remember { mutableStateOf("1") }
        var precio by remember { mutableStateOf("") }
        val filtrados = articulos.filter {
            filtro.isBlank() || it.descripcion.contains(filtro, true) || it.codigo.contains(filtro, true)
        }
        AlertDialog(
            onDismissRequest = { agregando = false },
            title = { Text("Agregar artículo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(filtro, { filtro = it }, "Buscar artículo")
                    Selector("Artículo", filtrados, articulo, { "${it.codigo} · ${it.descripcion} (${it.existencia.cantidad()})" }, {
                        articulo = it
                        precio = (if (esCompra) it.costo else it.precioVenta).aTexto()
                    })
                    CampoNumero(cantidad, { cantidad = it }, "Cantidad")
                    CampoNumero(precio, { precio = it }, if (esCompra) "Costo unitario" else "Precio unitario")
                    if (!esCompra && articulo != null && cantidad.aNumero() > articulo!!.existencia) {
                        Text("Aviso: la existencia es ${articulo!!.existencia.cantidad()}", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = articulo != null && cantidad.aNumero() > 0,
                    onClick = {
                        val a = articulo ?: return@TextButton
                        onCambio(partidas + Partida(a, cantidad.aNumero(), precio.aNumero(), a.tasaImpuesto))
                        agregando = false
                    }
                ) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { agregando = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun Totales(subtotal: Double, impuesto: Double, saldo: Double? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Subtotal: ${subtotal.r2().moneda()}")
        Text("Impuesto: ${impuesto.r2().moneda()}")
        Text("Total: ${(subtotal.r2() + impuesto.r2()).moneda()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        saldo?.let { Text("Saldo: ${it.moneda()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
    }
}

/** Forma de pago y, si no es efectivo, cuenta bancaria. */
@Composable
fun CamposFormaPago(
    formaPago: String,
    onFormaPago: (String) -> Unit,
    cuentas: List<CuentaConBanco>,
    cuentaId: Long?,
    onCuenta: (Long) -> Unit
) {
    Selector("Forma de pago", FormaPago.todas, formaPago, { it.lowercase().replaceFirstChar { c -> c.uppercase() } }, onFormaPago)
    if (formaPago == FormaPago.EFECTIVO) {
        Text("Se registrará en Caja", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Selector("Cuenta bancaria", cuentas, cuentas.firstOrNull { it.cuenta.id == cuentaId },
            { "${it.bancoNombre} — ${it.cuenta.alias} (${it.cuenta.saldo.moneda()})" }, { onCuenta(it.cuenta.id) })
    }
}

fun etiquetaVencimiento(vencimiento: Long): String {
    val zona = ZoneId.systemDefault()
    val dias = ChronoUnit.DAYS.between(LocalDate.now(zona), Instant.ofEpochMilli(vencimiento).atZone(zona).toLocalDate())
    return when {
        dias < 0 -> "Vencida hace ${-dias} día(s)"
        dias == 0L -> "Vence hoy"
        else -> "Vence en $dias día(s)"
    }
}
