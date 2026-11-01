@file:OptIn(ExperimentalMaterial3Api::class)

package com.sighec.movilplus.ui.componentes

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.SighecApp
import com.sighec.movilplus.data.ReglaNegocio
import com.sighec.movilplus.data.Repositorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ======================= ACCESO A DATOS ======================= */

@Composable
fun repo(): Repositorio = (LocalContext.current.applicationContext as SighecApp).repo

fun aviso(context: Context, texto: String) = Toast.makeText(context, texto, Toast.LENGTH_LONG).show()

/** Ejecuta una operación de base de datos mostrando los errores de negocio al usuario. */
fun CoroutineScope.ejecutar(
    context: Context,
    exito: String? = null,
    alTerminar: () -> Unit = {},
    accion: suspend () -> Unit
) = launch {
    try {
        accion()
        exito?.let { aviso(context, it) }
        alTerminar()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ReglaNegocio) {
        aviso(context, e.message ?: "Operación no permitida")
    } catch (e: SQLiteConstraintException) {
        aviso(context, "Ya existe un registro con ese código")
    } catch (e: Exception) {
        aviso(context, "Error: ${e.message}")
    }
}

/* ======================= FORMATOS ======================= */

private val localeMx = Locale("es", "MX")
private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy", localeMx)
private val formatoFechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", localeMx)

fun Double.moneda(): String = "$" + String.format(localeMx, "%,.2f", this)
fun Double.cantidad(): String = if (this % 1.0 == 0.0) String.format(localeMx, "%,.0f", this) else String.format(localeMx, "%,.2f", this)
fun Long.fecha(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatoFecha)
fun Long.fechaHora(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatoFechaHora)
fun String.aNumero(): Double = replace(",", "").replace("$", "").trim().toDoubleOrNull() ?: 0.0
fun Double.aTexto(): String = if (this == 0.0) "" else if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

fun inicioDeHoy(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun inicioDeMes(): Long = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun finDeHoy(): Long = inicioDeHoy() + 24L * 60 * 60 * 1000 - 1

/* ======================= ESTRUCTURA ======================= */

@Composable
fun PantallaBase(
    titulo: String,
    onAtras: (() -> Unit)?,
    acciones: @Composable RowScope.() -> Unit = {},
    onAgregar: (() -> Unit)? = null,
    contenido: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    if (onAtras != null) {
                        IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar") }
                    }
                },
                actions = acciones,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (onAgregar != null) {
                FloatingActionButton(onClick = onAgregar) { Icon(Icons.Filled.Add, "Agregar") }
            }
        },
        content = contenido
    )
}

/** Columna con scroll y separación estándar para formularios. */
@Composable
fun Formulario(padding: PaddingValues, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = contenido
    )
}

@Composable
fun Vacio(texto: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Fila(
    titulo: String,
    subtitulo: String? = null,
    derecha: String? = null,
    colorDerecha: Color = Color.Unspecified,
    detalleDerecha: String? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(titulo, fontWeight = FontWeight.Medium) },
        supportingContent = if (subtitulo != null) { { Text(subtitulo) } } else null,
        trailingContent = if (derecha != null || detalleDerecha != null) {
            {
                Column(horizontalAlignment = Alignment.End) {
                    derecha?.let { Text(it, fontWeight = FontWeight.Bold, color = colorDerecha) }
                    detalleDerecha?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
    HorizontalDivider()
}

@Composable
fun TarjetaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium)
            Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ConfirmarDialogo(titulo: String, texto: String, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = { onCancelar(); onConfirmar() }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/* ======================= CAMPOS ======================= */

@Composable
fun Buscador(texto: String, onCambio: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = texto,
        onValueChange = onCambio,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Buscar…") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (texto.isNotEmpty()) IconButton(onClick = { onCambio("") }) { Icon(Icons.Filled.Clear, "Limpiar") }
        },
        singleLine = true
    )
}

@Composable
fun Campo(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    lineas: Int = 1,
    habilitado: Boolean = true,
    password: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth(),
        singleLine = lineas == 1,
        minLines = lineas,
        enabled = habilitado,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
fun CampoNumero(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Campo(
        valor = valor,
        onCambio = { nuevo -> if (nuevo.all { it.isDigit() || it == '.' || it == ',' } && nuevo.count { it == '.' } <= 1) onCambio(nuevo) },
        etiqueta = etiqueta,
        modifier = modifier,
        teclado = KeyboardType.Decimal,
        habilitado = habilitado
    )
}

@Composable
fun Interruptor(texto: String, valor: Boolean, onCambio: (Boolean) -> Unit, habilitado: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = onCambio, enabled = habilitado)
    }
}

/** Lista desplegable para seleccionar un elemento. */
@Composable
fun <T> Selector(
    etiqueta: String,
    opciones: List<T>,
    seleccionado: T?,
    texto: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = abierto && habilitado,
        onExpandedChange = { if (habilitado) abierto = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = seleccionado?.let(texto) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            if (opciones.isEmpty()) {
                DropdownMenuItem(text = { Text("Sin registros") }, onClick = { abierto = false }, enabled = false)
            }
            opciones.forEach { op ->
                DropdownMenuItem(text = { Text(texto(op)) }, onClick = { onSeleccion(op); abierto = false })
            }
        }
    }
}

/** Campo de fecha con calendario. El valor se maneja en milisegundos (hora local). */
@Composable
fun CampoFecha(etiqueta: String, valor: Long, onCambio: (Long) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor.fecha(),
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth()
        )
        // Capa transparente para abrir el calendario al tocar el campo.
        Box(Modifier.matchParentSize().clickable { abierto = true })
    }
    if (abierto) {
        val zona = ZoneId.systemDefault()
        val localActual = Instant.ofEpochMilli(valor).atZone(zona).toLocalDate()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = localActual.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { utc ->
                        val dia = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val hora = Instant.ofEpochMilli(valor).atZone(zona).toLocalTime()
                        onCambio(dia.atTime(hora).atZone(zona).toInstant().toEpochMilli())
                    }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }
}
