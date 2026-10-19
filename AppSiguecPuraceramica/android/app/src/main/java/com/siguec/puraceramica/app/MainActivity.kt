// MÓDULO :app
// Paquete principal de la app
package com.siguec.puraceramica.app

// Datos que Android entrega al crear la pantalla
import android.os.Bundle
// Activity preparada para Jetpack Compose
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// Estado en Compose
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.siguec.puraceramica.feature.inventario.PantallaComparacion
import com.siguec.puraceramica.feature.inventario.PantallaInventarios
import com.siguec.puraceramica.feature.login.PantallaCambiarEmpresa
import com.siguec.puraceramica.feature.login.PantallaLogin
// Corrutinas
import kotlinx.coroutines.launch

/**
 * Única Activity de la app. Funciona como "marco": dentro de ella Compose
 * va cambiando entre pantallas (entrada, menú, contar...).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla completa moderna; íconos BLANCOS arriba porque la barra es negra
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
private const val RUTA_INICIO = "inicio"            // Menú principal
private const val RUTA_EMPRESAS = "empresas"        // Cambiar empresa
private const val RUTA_ARTICULOS = "articulos"      // Consultar y sincronizar artículos
private const val RUTA_INVENTARIOS = "inventarios"  // Inventarios por almacén (preparar, cerrar)
private const val RUTA_COMPARACION = "comparacion"  // Diferencias contado vs. stock
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
    val alcance = rememberCoroutineScope()

    // Si el servidor rechaza el token en cualquier pantalla -> a la entrada
    val vencida by gestor.sesionVencida.collectAsStateWithLifecycle()
    LaunchedEffect(vencida) { if (vencida) nav.irAlLogin() }

    // Sesión y empresa actuales
    val sesion by gestor.sesion.collectAsStateWithLifecycle()
    val ambiente by gestor.ambiente.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = RUTA_LOGIN) {

        // ---------- Entrada ----------
        composable(RUTA_LOGIN) {
            PantallaLogin(
                gestor = gestor,
                alEntrar = { nav.navigate(RUTA_INICIO) { popUpTo(RUTA_LOGIN) { inclusive = true } } },
                alSalir = alSalir
            )
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
                puedePreparar = p.prepararInventario,
                esAdministrador = s.usuario.esAdministrador,
                alContar = { nav.navigate(RUTA_ELEGIR) },
                alAbrirArticulos = { nav.navigate(RUTA_ARTICULOS) },
                alAbrirInventarios = { nav.navigate(RUTA_INVENTARIOS) },
                alAbrirUsuarios = { nav.navigate(RUTA_USUARIOS) },
                alAbrirBitacora = { nav.navigate(RUTA_BITACORA) },
                alCambiarEmpresa = { nav.navigate(RUTA_EMPRESAS) },
                // Al borrar la sesión, la comprobación de arriba lleva a la entrada
                alCerrarSesion = { alcance.launch { gestor.salir() } },
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

        // ---------- Artículos: consultar y sincronizar ----------
        composable(RUTA_ARTICULOS) {
            val a = ambiente ?: return@composable
            // Repositorios de la empresa elegida (nuevos si cambia la empresa)
            val articulos = remember(a.coEmpresa) { gestor.articulos() } ?: return@composable
            val inventarios = remember(a.coEmpresa) { gestor.inventarios() } ?: return@composable
            PantallaArticulos(
                repositorio = articulos,
                inventarios = inventarios,
                nombreEmpresa = a.nombre,
                puedeSincronizar = a.permisos.sincronizarArticulos,
                inventarioInicial = gestor.ultimoInventario(),
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Inventarios por almacén ----------
        composable(RUTA_INVENTARIOS) {
            val a = ambiente ?: return@composable
            val inventarios = remember(a.coEmpresa) { gestor.inventarios() } ?: return@composable
            PantallaInventarios(
                repositorio = inventarios,
                nombreEmpresa = a.nombre,
                almacenInicial = a.almacen,
                alContar = { id -> gestor.recordarInventario(id); nav.navigate("$RUTA_CONTEO/$id") },
                alComparar = { id -> nav.navigate("$RUTA_COMPARACION/$id") },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Diferencias contado vs. stock ----------
        composable(
            route = "$RUTA_COMPARACION/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: return@composable
            val a = ambiente ?: return@composable
            val inventarios = remember(a.coEmpresa) { gestor.inventarios() } ?: return@composable
            PantallaComparacion(repositorio = inventarios, nombreEmpresa = a.nombre, id = id,
                alVolver = { nav.popBackStack() })
        }

        // ---------- ¿En qué inventario abierto va a contar? ----------
        composable(RUTA_ELEGIR) {
            val a = ambiente ?: return@composable
            val inventarios = remember(a.coEmpresa) { gestor.inventarios() } ?: return@composable
            PantallaElegirInventario(
                repositorio = inventarios,
                nombreEmpresa = a.nombre,
                ultimo = gestor.ultimoInventario(),
                directo = true,
                alElegir = { id -> gestor.recordarInventario(id); nav.navigate("$RUTA_CONTEO/$id") },
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Contar escaneando ----------
        composable(
            route = "$RUTA_CONTEO/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: return@composable
            val a = ambiente ?: return@composable
            val inventarios = remember(a.coEmpresa) { gestor.inventarios() } ?: return@composable
            val articulos = remember(a.coEmpresa) { gestor.articulos() } ?: return@composable
            val conteos = remember(a.coEmpresa) { gestor.conteos() } ?: return@composable
            PantallaConteo(
                inventarios = inventarios,
                articulos = articulos,
                conteos = conteos,
                nombreEmpresa = a.nombre,
                id = id,
                alVolver = { nav.popBackStack() }
            )
        }

        // ---------- Administración: usuarios ----------
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
