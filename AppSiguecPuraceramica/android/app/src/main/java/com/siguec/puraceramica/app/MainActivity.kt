// MÓDULO :app
// Paquete principal de la app
package com.siguec.puraceramica.app

// Datos que Android entrega al crear la pantalla
import android.os.Bundle
// Activity preparada para Jetpack Compose
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// Estado en Compose
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
// Observar el estado respetando el ciclo de vida
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// Navegación entre pantallas
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
// Sesión
import com.siguec.puraceramica.core.sesion.GestorSesion
// Tema (módulo :core:ui)
import com.siguec.puraceramica.core.ui.tema.TemaSiguec
// Pantallas de los módulos de funciones
import com.siguec.puraceramica.feature.admin.PantallaBitacora
import com.siguec.puraceramica.feature.admin.PantallaUsuario
import com.siguec.puraceramica.feature.admin.PantallaUsuarios
import com.siguec.puraceramica.feature.articulos.PantallaArticulos
import com.siguec.puraceramica.feature.conteo.PantallaConteo
import com.siguec.puraceramica.feature.conteo.PantallaElegirInventario
import com.siguec.puraceramica.feature.inicio.PantallaInicio
import com.siguec.puraceramica.feature.inventario.PantallaAbrirInventario
import com.siguec.puraceramica.feature.inventario.PantallaInventarios
import com.siguec.puraceramica.feature.login.PantallaAutorizarConfiguracion
import com.siguec.puraceramica.feature.login.PantallaCambiarEmpresa
import com.siguec.puraceramica.feature.login.PantallaConfiguracion
import com.siguec.puraceramica.feature.login.PantallaLogin

/**
 * Única Activity de la app. Funciona como "marco": dentro de ella Compose
 * va cambiando entre pantallas (entrada, menú, contar...).
 */
// FragmentActivity (es una ComponentActivity): la ventana de huella de Android la necesita
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla completa moderna; íconos BLANCOS arriba porque la barra es roja oscura
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val app = application as SiguecApp
        setContent {
            TemaSiguec {
                // "finishAndRemoveTask()" cierra la app y la quita de las recientes (ícono "Salir")
                Navegacion(app.gestorSesion, alSalir = { finishAndRemoveTask() })
            }
        }
    }
}

// ---------- Nombres de las rutas (pantallas) ----------
private const val RUTA_LOGIN = "login"              // Entrada
private const val RUTA_CONFIGURACION = "configuracion" // Datos de la conexión al SQL Server
private const val RUTA_AUTORIZAR = "autorizar-configuracion" // Huella o clave de un usuario autorizado
private const val RUTA_INICIO = "inicio"            // Menú principal
private const val RUTA_EMPRESAS = "empresas"        // Cambiar empresa
private const val RUTA_ARTICULOS = "articulos"      // Consultar y sincronizar artículos
private const val RUTA_INVENTARIOS = "inventarios"  // Inventarios abiertos en el teléfono y enviar
private const val RUTA_ABRIR = "abrir-inventario"   // Abrir un inventario físico de Profit
private const val RUTA_ELEGIR = "elegir-inventario" // ¿En qué inventario abierto va a contar?
private const val RUTA_CONTEO = "conteo"            // Contar escaneando (un inventario)
private const val RUTA_USUARIOS = "usuarios"        // Administración: usuarios
private const val RUTA_USUARIO = "usuario"          // Administración: un usuario
private const val RUTA_BITACORA = "bitacora"        // Administración: bitácora

/** Vuelve a la pantalla de entrada borrando todo el historial. */
private fun NavHostController.irAlLogin() = navigate(RUTA_LOGIN) { popUpTo(0) { inclusive = true } }

/**
 * Las pantallas de la app y cómo se pasa de una a otra.
 * Es el ÚNICO lugar que conoce a todos los módulos de funciones.
 */
@Composable
private fun Navegacion(gestor: GestorSesion, alSalir: () -> Unit) {
    val nav = rememberNavController()

    // Sesión y empresa actuales
    val sesion by gestor.sesion.collectAsStateWithLifecycle()
    val ambiente by gestor.ambiente.collectAsStateWithLifecycle()

    // Configuración: la primera vez se abre directo; ya configurada, pide huella o clave de un autorizado
    val irAConfiguracion = { nav.navigate(if (gestor.conexionConfigurada) RUTA_AUTORIZAR else RUTA_CONFIGURACION) }

    NavHost(navController = nav, startDestination = RUTA_LOGIN) {

        // ---------- Entrada ----------
        composable(RUTA_LOGIN) {
            PantallaLogin(
                gestor = gestor,
                alEntrar = { nav.navigate(RUTA_INICIO) { popUpTo(RUTA_LOGIN) { inclusive = true } } },
                alConfigurar = irAConfiguracion,
                alSalir = alSalir
            )
        }

        // ---------- Autorizar Configuración (huella o clave de un usuario autorizado) ----------
        composable(RUTA_AUTORIZAR) {
            PantallaAutorizarConfiguracion(
                gestor = gestor,
                usuarioInicial = sesion?.usuario?.usuario ?: gestor.ultimoUsuario,
                // "Volver" desde Configuración regresa a donde estaba, no a esta pantalla
                alAutorizado = { nav.navigate(RUTA_CONFIGURACION) { popUpTo(RUTA_AUTORIZAR) { inclusive = true } } },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Configuración de la conexión ----------
        composable(RUTA_CONFIGURACION) {
            PantallaConfiguracion(gestor = gestor, alVolver = { nav.popBackStack() })
        }

        // ---------- Menú principal ----------
        composable(RUTA_INICIO) {
            val s = sesion
            val a = ambiente
            // Sin sesión (cerró sesión) -> a la entrada
            if (s == null || a == null) {
                LaunchedEffect(Unit) { nav.irAlLogin() }
                return@composable
            }
            val p = a.permisos
            PantallaInicio(
                nombreUsuario = s.usuario.nombre,
                nombreEmpresa = a.nombre,
                variasEmpresas = s.ambientes.size > 1,
                puedeContar = p.contarInventario,
                puedeConsultar = p.consultarArticulos || p.sincronizarArticulos,
                puedeInventarios = p.contarInventario || p.cerrarInventario,
                esAdministrador = s.usuario.esAdministrador,
                alContar = { nav.navigate(RUTA_ELEGIR) },
                alAbrirArticulos = { nav.navigate(RUTA_ARTICULOS) },
                alAbrirInventarios = { nav.navigate(RUTA_INVENTARIOS) },
                alAbrirUsuarios = { nav.navigate(RUTA_USUARIOS) },
                alAbrirBitacora = { nav.navigate(RUTA_BITACORA) },
                alConfiguracion = irAConfiguracion,
                alCambiarEmpresa = { nav.navigate(RUTA_EMPRESAS) },
                // Al borrar la sesión, la comprobación de arriba lleva a la entrada
                alCerrarSesion = { gestor.salir() },
                alSalir = alSalir
            )
        }

        // ---------- Cambiar empresa ----------
        composable(RUTA_EMPRESAS) {
            PantallaCambiarEmpresa(
                ambientes = sesion?.ambientes.orEmpty(),
                actual = ambiente?.coEmpresa,
                alElegir = { gestor.elegirEmpresa(it); nav.popBackStack() },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Artículos: consultar (sin WiFi) y sincronizar (WiFi) ----------
        composable(RUTA_ARTICULOS) {
            val a = ambiente ?: return@composable
            PantallaArticulos(
                gestor = gestor,
                coEmpresa = a.coEmpresa,
                nombreEmpresa = a.nombre,
                puedeSincronizar = a.permisos.sincronizarArticulos,
                inventarioInicial = gestor.ultimoInventario(),
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Inventarios abiertos en el teléfono; enviar a Profit (WiFi) ----------
        composable(RUTA_INVENTARIOS) {
            val a = ambiente ?: return@composable
            PantallaInventarios(
                gestor = gestor,
                coEmpresa = a.coEmpresa,
                nombreEmpresa = a.nombre,
                puedeAbrir = a.permisos.contarInventario,
                puedeEnviar = a.permisos.cerrarInventario,
                alAbrirNuevo = { nav.navigate(RUTA_ABRIR) },
                alContar = { id -> gestor.recordarInventario(id); nav.navigate("$RUTA_CONTEO/$id") },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Abrir un inventario físico de Profit (WiFi) ----------
        composable(RUTA_ABRIR) {
            val a = ambiente ?: return@composable
            PantallaAbrirInventario(
                gestor = gestor,
                coEmpresa = a.coEmpresa,
                nombreEmpresa = a.nombre,
                // Abierto: se pasa a contar (y "volver" regresa a Inventarios, no a esta pantalla)
                alAbierto = { id ->
                    gestor.recordarInventario(id)
                    nav.navigate("$RUTA_CONTEO/$id") { popUpTo(RUTA_ABRIR) { inclusive = true } }
                },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- ¿En qué inventario abierto va a contar? ----------
        composable(RUTA_ELEGIR) {
            val a = ambiente ?: return@composable
            PantallaElegirInventario(
                gestor = gestor,
                coEmpresa = a.coEmpresa,
                nombreEmpresa = a.nombre,
                ultimo = gestor.ultimoInventario(),
                directo = true,
                alElegir = { id -> gestor.recordarInventario(id); nav.navigate("$RUTA_CONTEO/$id") },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Contar escaneando (sin WiFi) ----------
        composable(
            route = "$RUTA_CONTEO/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: return@composable
            val a = ambiente ?: return@composable
            val usuario = sesion?.usuario?.usuario ?: return@composable
            PantallaConteo(
                gestor = gestor,
                coEmpresa = a.coEmpresa,
                nombreEmpresa = a.nombre,
                id = id,
                usuario = usuario,
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Administración: usuarios (WiFi) ----------
        composable(RUTA_USUARIOS) {
            val repositorio = remember { gestor.admin() } ?: return@composable
            PantallaUsuarios(
                repositorio = repositorio,
                alAbrir = { id -> nav.navigate("$RUTA_USUARIO/$id") },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Administración: un usuario ----------
        composable(
            route = "$RUTA_USUARIO/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: return@composable
            val repositorio = remember { gestor.admin() } ?: return@composable
            PantallaUsuario(
                repositorio = repositorio,
                id = id,
                usuarioPropio = sesion?.usuario?.usuario.orEmpty(),
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Administración: bitácora ----------
        composable(RUTA_BITACORA) {
            val repositorio = remember { gestor.admin() } ?: return@composable
            PantallaBitacora(repositorio = repositorio, alVolver = { nav.popBackStack() })
        }
    }
}
