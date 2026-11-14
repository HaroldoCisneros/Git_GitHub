package com.sighec.pedidos.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sighec.pedidos.contenedor
import com.sighec.pedidos.data.ConexionConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    puedeVolver: Boolean,
    onVolver: () -> Unit,
    onGuardado: () -> Unit,
) {
    val app = LocalContext.current.contenedor
    val actual = remember { app.config.config.value }
    val scope = rememberCoroutineScope()

    var servidor by rememberSaveable { mutableStateOf(actual.servidor) }
    var puerto by rememberSaveable { mutableStateOf(actual.puerto.toString()) }
    var instancia by rememberSaveable { mutableStateOf(actual.instancia) }
    var baseDatos by rememberSaveable { mutableStateOf(actual.baseDatos) }
    var usuario by rememberSaveable { mutableStateOf(actual.usuario) }
    var clave by rememberSaveable { mutableStateOf(actual.clave) }
    var tipoPrecio by rememberSaveable { mutableStateOf(actual.tipoPrecio) }
    var almacen by rememberSaveable { mutableStateOf(actual.almacen) }
    var iva by rememberSaveable { mutableStateOf(actual.ivaPorcentaje.toString()) }
    var urlImagenes by rememberSaveable { mutableStateOf(actual.urlImagenes) }

    var probando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun armar() = ConexionConfig(
        servidor = servidor.trim(),
        puerto = puerto.toIntOrNull() ?: 1433,
        instancia = instancia.trim(),
        baseDatos = baseDatos.trim(),
        usuario = usuario.trim(),
        clave = clave,
        tipoPrecio = tipoPrecio.trim().ifBlank { "01" },
        almacen = almacen.trim(),
        ivaPorcentaje = iva.replace(',', '.').toDoubleOrNull() ?: 16.0,
        urlImagenes = urlImagenes.trim(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración del servidor") },
                navigationIcon = {
                    if (puedeVolver) IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SQL Server de Profit", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Campo("Servidor (IP)", servidor, { servidor = it }, Modifier.weight(2f))
                    Campo("Puerto", puerto, { puerto = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                    Campo("Instancia (opcional)", instancia, { instancia = it }, Modifier.weight(1.5f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Campo("Base de datos", baseDatos, { baseDatos = it }, Modifier.weight(1f))
                    Campo("Usuario SQL", usuario, { usuario = it }, Modifier.weight(1f))
                    Campo("Clave SQL", clave, { clave = it }, Modifier.weight(1f), KeyboardType.Password, oculto = true)
                }

                Spacer(Modifier.size(8.dp))
                Text("Pedidos", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Campo("Tipo de precio (co_precio)", tipoPrecio, { tipoPrecio = it }, Modifier.weight(1f))
                    Campo("Almacén stock (vacío = todos)", almacen, { almacen = it }, Modifier.weight(1f))
                    Campo("IVA %", iva, { iva = it }, Modifier.weight(0.6f), KeyboardType.Decimal)
                }
                Campo(
                    "URL de imágenes, ej: http://192.168.1.10/img/{co_art}.jpg",
                    urlImagenes, { urlImagenes = it }, Modifier.fillMaxWidth(), KeyboardType.Uri,
                )

                resultado?.let { (ok, msg) ->
                    Text(
                        msg,
                        color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (probando) CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(
                        enabled = !probando,
                        onClick = {
                            probando = true; resultado = null
                            scope.launch {
                                resultado = runCatching { app.sql.probar(armar()) }
                                    .fold({ true to "Conexión exitosa: $it" }, { false to (it.message ?: "Error") })
                                probando = false
                            }
                        },
                    ) { Text("Probar conexión") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = !probando && servidor.isNotBlank() && baseDatos.isNotBlank() && usuario.isNotBlank(),
                        onClick = { app.config.guardar(armar()); onGuardado() },
                    ) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    modifier: Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    oculto: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (oculto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}
