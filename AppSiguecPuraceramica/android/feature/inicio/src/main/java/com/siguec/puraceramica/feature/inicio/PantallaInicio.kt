// MÓDULO :feature:inicio
// Paquete de la pantalla de inicio
package com.siguec.puraceramica.feature.inicio

// Herramientas de diseño
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
// Desplazamiento vertical (por si el menú no cabe)
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Iconos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
// Componentes de Material 3
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// Anotación para funciones que dibujan interfaz
import androidx.compose.runtime.Composable
// Alineación y modificadores
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
// Componentes compartidos
import com.siguec.puraceramica.core.ui.componentes.BarraSuperior
import com.siguec.puraceramica.core.ui.componentes.LogoBarraPuraceramica

/**
 * Menú principal (después de entrar y elegir la empresa).
 *
 * Para agregar una opción nueva: se crea su módulo feature/xxx, se agrega aquí
 * una [OpcionMenu] con su acción y en :app su ruta.
 *
 * @param nombreUsuario nombre del usuario que entró
 * @param nombreEmpresa empresa elegida (se muestra en la barra)
 * @param variasEmpresas true = se muestra "Cambiar empresa"
 * @param sinConexion entró sin WiFi (se avisa: contar sí, sincronizar/traer/enviar no)
 * @param puedeContar permiso "contar inventario": opción "Contar"
 * @param puedeConsultar permiso "consultar artículos" o "sincronizar": opción "Artículos"
 * @param puedeInventarios permiso "contar" o "cerrar": opción "Inventarios" (traer de Profit / enviar)
 * @param esAdministrador el usuario administra la App (usuarios, permisos y bitácora)
 * @param alConfiguracion opción "Configuración" (datos de la conexión al SQL Server)
 */
@Composable
fun PantallaInicio(
    nombreUsuario: String,
    nombreEmpresa: String,
    variasEmpresas: Boolean,
    sinConexion: Boolean,
    puedeContar: Boolean,
    puedeConsultar: Boolean,
    puedeInventarios: Boolean,
    esAdministrador: Boolean,
    alContar: () -> Unit,
    alAbrirArticulos: () -> Unit,
    alAbrirInventarios: () -> Unit,
    alAbrirUsuarios: () -> Unit,
    alAbrirBitacora: () -> Unit,
    alConfiguracion: () -> Unit,
    alCambiarEmpresa: () -> Unit,
    alCerrarSesion: () -> Unit,
    alSalir: () -> Unit
) {
    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = "Conteo de Inventario",
                subtitulo = nombreEmpresa,
                acciones = {
                    // Cerrar sesión: vuelve a la pantalla de usuario y clave
                    IconButton(onClick = alCerrarSesion) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                    }
                    // Salir: cierra la App
                    IconButton(onClick = alSalir) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir de la app")
                    }
                }
            )
        }
    ) { margenes ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Logo horizontal de Puracerámica arriba del menú
            LogoBarraPuraceramica(Modifier.fillMaxWidth())
            Text("Hola, $nombreUsuario", style = MaterialTheme.typography.headlineSmall)
            Text("Toma de inventario", style = MaterialTheme.typography.bodyLarge)
            if (sinConexion) {
                Text("Entró sin WiFi: puede contar. Para sincronizar, traer o enviar inventarios conéctese " +
                    "al WiFi de la empresa.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }

            // ---------- Opción 1: Contar (lo más usado, va primero) ----------
            if (puedeContar) {
                OpcionMenu(
                    icono = Icons.Filled.QrCodeScanner,
                    titulo = "Contar",
                    descripcion = "Elegir el inventario abierto del almacén y escanear los códigos de barra (sin WiFi)",
                    alTocar = alContar
                )
            }

            // ---------- Opción 2: Artículos (consultar y sincronizar) ----------
            if (puedeConsultar) {
                OpcionMenu(
                    icono = Icons.Filled.Inventory2,
                    titulo = "Artículos",
                    descripcion = "Consultar código, descripción y stock contado; sincronizar con Profit (WiFi)",
                    alTocar = alAbrirArticulos
                )
            }

            // ---------- Opción 3: Inventarios (abrir los de Profit y enviar) ----------
            if (puedeInventarios) {
                OpcionMenu(
                    icono = Icons.Filled.Warehouse,
                    titulo = "Inventarios",
                    descripcion = "Abrir un inventario creado en Profit y, al terminar, enviar lo contado (WiFi)",
                    alTocar = alAbrirInventarios
                )
            }

            // ---------- Administración (solo administradores) ----------
            if (esAdministrador) {
                OpcionMenu(
                    icono = Icons.Filled.AdminPanelSettings,
                    titulo = "Usuarios y permisos",
                    descripcion = "Crear usuarios, darles empresas y permisos, desbloquearlos",
                    alTocar = alAbrirUsuarios
                )
                OpcionMenu(
                    icono = Icons.Filled.History,
                    titulo = "Bitácora",
                    descripcion = "Quién entró, sincronizó, trajo o envió inventarios y cuándo",
                    alTocar = alAbrirBitacora
                )
            }

            // ---------- Configuración de la conexión ----------
            OpcionMenu(
                icono = Icons.Filled.Settings,
                titulo = "Configuración",
                descripcion = "Datos de la conexión al SQL Server (servidor, puerto, usuario y clave)",
                alTocar = alConfiguracion
            )

            // ---------- Cambiar empresa ----------
            if (variasEmpresas) {
                OpcionMenu(
                    icono = Icons.Filled.Business,
                    titulo = "Cambiar empresa",
                    descripcion = "Trabajar con otra base de Profit",
                    alTocar = alCambiarEmpresa
                )
            }
        }
    }
}

/**
 * Tarjeta de una opción del menú (icono + título + descripción).
 * @param alTocar acción; null = tarjeta apagada (opción todavía no disponible)
 */
@Composable
private fun OpcionMenu(icono: ImageVector, titulo: String, descripcion: String, alTocar: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier),
        colors = if (alTocar != null) CardDefaults.cardColors()
        else CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icono,
                contentDescription = null, // El título ya describe la opción
                tint = if (alTocar != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(descripcion, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
