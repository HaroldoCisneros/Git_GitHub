package com.sighec.movilplus.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sighec.movilplus.SighecApp
import com.sighec.movilplus.seguridad.Biometria
import com.sighec.movilplus.seguridad.Modulo
import com.sighec.movilplus.seguridad.Sesion
import com.sighec.movilplus.seguridad.buscarActivity
import com.sighec.movilplus.ui.componentes.PantallaBase
import com.sighec.movilplus.ui.componentes.TarjetaDato
import com.sighec.movilplus.ui.componentes.aviso
import com.sighec.movilplus.ui.componentes.ejecutar
import com.sighec.movilplus.ui.componentes.inicioDeHoy
import com.sighec.movilplus.ui.componentes.moneda
import com.sighec.movilplus.ui.componentes.repo
import com.sighec.movilplus.ui.theme.Rojo
import kotlinx.coroutines.launch

@Composable
fun InicioScreen(onAbrir: (String) -> Unit, onSalir: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SighecApp
    val repo = repo()
    val scope = rememberCoroutineScope()
    val usuario = Sesion.usuario
    var dialogoHuella by remember { mutableStateOf(false) }

    val hoy = remember { inicioDeHoy() }
    val saldoCaja by remember { repo.db.caja().observarSaldo() }.collectAsState(0.0)
    val cxc by remember { repo.db.facturas().observarSaldosPorCliente(hoy) }.collectAsState(emptyList())
    val cxp by remember { repo.db.compras().observarSaldosPorProveedor(hoy) }.collectAsState(emptyList())

    val modulos = Modulo.entries.filter { Sesion.puede(it) }

    PantallaBase(
        titulo = "SighecMovilPlus",
        onAtras = null,
        acciones = {
            IconButton(onClick = { dialogoHuella = true }) { Icon(Icons.Filled.Fingerprint, "Huella digital") }
            IconButton(onClick = { scope.launch { repo.logout(); onSalir() } }) {
                Icon(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Hola, ${usuario?.nombre ?: ""}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (usuario?.esAdmin == true) "Administrador" else "Usuario: ${usuario?.usuario}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val indicadores = buildList {
                if (Sesion.puede(Modulo.CAJA)) add(Triple("Saldo en caja", saldoCaja.moneda(), false))
                if (Sesion.puede(Modulo.CXC)) add(Triple("Por cobrar", cxc.sumOf { it.saldo }.moneda(), cxc.any { it.vencido > 0.005 }))
                if (Sesion.puede(Modulo.CXP)) add(Triple("Por pagar", cxp.sumOf { it.saldo }.moneda(), cxp.any { it.vencido > 0.005 }))
            }
            if (indicadores.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        indicadores.forEach { (etiqueta, valor, alerta) ->
                            TarjetaDato(etiqueta, valor, Modifier.weight(1f), color = if (alerta) Rojo else androidx.compose.ui.graphics.Color.Unspecified)
                        }
                    }
                }
            }
            if (modulos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Tu usuario no tiene opciones asignadas. Pide al administrador que te dé acceso.", textAlign = TextAlign.Center)
                }
            }
            modulos.groupBy { it.grupo }.forEach { (grupo, lista) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(grupo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(lista) { m ->
                    Card(onClick = { onAbrir(m.ruta) }, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(m.icono, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(m.titulo, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (dialogoHuella && usuario != null) {
        val enrolado = app.prefs.usuarioHuella
        val esEste = enrolado.equals(usuario.usuario, ignoreCase = true)
        val disponible = Biometria.disponible(context)
        AlertDialog(
            onDismissRequest = { dialogoHuella = false },
            title = { Text("Entrar con huella") },
            text = {
                Text(
                    when {
                        !usuario.permiteHuella -> "El administrador no ha permitido el acceso con huella para tu usuario."
                        !disponible -> "Este teléfono no tiene huella digital configurada o no cuenta con sensor."
                        esEste -> "La huella de este teléfono está ligada a tu usuario (${usuario.usuario})."
                        enrolado != null -> "Actualmente la huella está ligada al usuario '$enrolado'. ¿Quieres ligarla a tu usuario?"
                        else -> "Liga la huella de este teléfono a tu usuario para entrar sin escribir la contraseña."
                    }
                )
            },
            confirmButton = {
                if (usuario.permiteHuella && disponible) {
                    TextButton(onClick = {
                        dialogoHuella = false
                        if (esEste) {
                            app.prefs.usuarioHuella = null
                            scope.ejecutar(context, exito = "Huella desactivada") { repo.bitacora(Modulo.USUARIOS, "Desactivó huella", usuario.usuario) }
                        } else {
                            val activity = context.buscarActivity() ?: return@TextButton
                            Biometria.autenticar(activity, "Confirma tu huella",
                                onExito = {
                                    app.prefs.usuarioHuella = usuario.usuario
                                    scope.ejecutar(context, exito = "Huella activada") { repo.bitacora(Modulo.USUARIOS, "Activó huella", usuario.usuario) }
                                },
                                onError = { aviso(context, it) })
                        }
                    }) { Text(if (esEste) "Desactivar" else "Activar") }
                }
            },
            dismissButton = { TextButton(onClick = { dialogoHuella = false }) { Text("Cerrar") } }
        )
    }
}
