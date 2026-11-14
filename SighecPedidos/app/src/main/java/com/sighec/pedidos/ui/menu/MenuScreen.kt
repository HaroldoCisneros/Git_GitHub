package com.sighec.pedidos.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sighec.pedidos.contenedor

private data class OpcionMenu(
    val titulo: String,
    val descripcion: String,
    val icono: ImageVector,
    val accion: (() -> Unit)?,   // null = próximamente
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(onPedidos: () -> Unit, onConfig: () -> Unit, onCerrarSesion: () -> Unit) {
    val usuario = LocalContext.current.contenedor.usuarioActual

    val opciones = listOf(
        OpcionMenu("Toma de Pedidos", "Crear y gestionar pedidos", Icons.Outlined.AddShoppingCart, onPedidos),
        OpcionMenu("Consulta de Pedidos", "Próximamente", Icons.AutoMirrored.Outlined.ReceiptLong, null),
        OpcionMenu("Clientes", "Próximamente", Icons.Outlined.Groups, null),
        OpcionMenu("Artículos", "Próximamente", Icons.Outlined.Inventory2, null),
        OpcionMenu("Configuración", "Servidor y parámetros", Icons.Outlined.Settings, onConfig),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sighec Pedidos") },
                actions = {
                    Text(usuario?.nombre.orEmpty(), modifier = Modifier.padding(end = 8.dp))
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(opciones) { op ->
                val habilitada = op.accion != null
                Card(
                    onClick = { op.accion?.invoke() },
                    enabled = habilitada,
                    modifier = Modifier.height(180.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (habilitada) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(op.icono, null, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(op.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(op.descripcion, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
