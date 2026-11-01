package com.sighec.movilplus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sighec.movilplus.seguridad.Modulo
import com.sighec.movilplus.seguridad.Sesion
import com.sighec.movilplus.ui.acceso.ConfiguracionInicialScreen
import com.sighec.movilplus.ui.acceso.LoginScreen
import com.sighec.movilplus.ui.bitacora.BitacoraScreen
import com.sighec.movilplus.ui.catalogos.ArticuloFormScreen
import com.sighec.movilplus.ui.catalogos.ArticulosScreen
import com.sighec.movilplus.ui.catalogos.BancosScreen
import com.sighec.movilplus.ui.catalogos.ClienteFormScreen
import com.sighec.movilplus.ui.catalogos.ClientesScreen
import com.sighec.movilplus.ui.catalogos.CondicionesScreen
import com.sighec.movilplus.ui.catalogos.ProveedorFormScreen
import com.sighec.movilplus.ui.catalogos.ProveedoresScreen
import com.sighec.movilplus.ui.componentes.Vacio
import com.sighec.movilplus.ui.componentes.repo
import com.sighec.movilplus.ui.compras.CompraDetalleScreen
import com.sighec.movilplus.ui.compras.CompraNuevaScreen
import com.sighec.movilplus.ui.compras.ComprasScreen
import com.sighec.movilplus.ui.compras.CxPScreen
import com.sighec.movilplus.ui.compras.PagoNuevoScreen
import com.sighec.movilplus.ui.compras.PagosScreen
import com.sighec.movilplus.ui.inicio.InicioScreen
import com.sighec.movilplus.ui.tesoreria.CajaScreen
import com.sighec.movilplus.ui.tesoreria.CuentaFormScreen
import com.sighec.movilplus.ui.tesoreria.CuentaMovimientosScreen
import com.sighec.movilplus.ui.tesoreria.CuentasScreen
import com.sighec.movilplus.ui.usuarios.UsuarioFormScreen
import com.sighec.movilplus.ui.usuarios.UsuariosScreen
import com.sighec.movilplus.ui.ventas.CobroNuevoScreen
import com.sighec.movilplus.ui.ventas.CobrosScreen
import com.sighec.movilplus.ui.ventas.CxCScreen
import com.sighec.movilplus.ui.ventas.FacturaDetalleScreen
import com.sighec.movilplus.ui.ventas.FacturaNuevaScreen
import com.sighec.movilplus.ui.ventas.FacturasScreen

@Composable
fun Navegacion() {
    val nav = rememberNavController()
    val repo = repo()
    var inicio by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { inicio = if (repo.hayUsuarios()) "login" else "configuracion" }

    val destinoInicial = inicio
    if (destinoInicial == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val atras: () -> Unit = { nav.popBackStack() }
    fun irALogin() = nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }

    NavHost(nav, startDestination = destinoInicial) {
        composable("configuracion") { ConfiguracionInicialScreen(onListo = ::irALogin) }
        composable("login") {
            LoginScreen(onEntrar = { nav.navigate("inicio") { popUpTo("login") { inclusive = true } } })
        }
        pantalla("inicio", null, nav) { InicioScreen(onAbrir = { nav.navigate(it) }, onSalir = ::irALogin) }

        // Administración
        pantalla("usuarios", Modulo.USUARIOS, nav) { UsuariosScreen(atras) { nav.navigate("usuario/$it") } }
        pantalla("usuario/{id}", Modulo.USUARIOS, nav) { UsuarioFormScreen(it, atras) }
        pantalla("bitacora", Modulo.BITACORA, nav) { BitacoraScreen(atras) }

        // Catálogos
        pantalla("articulos", Modulo.ARTICULOS, nav) { ArticulosScreen(atras) { nav.navigate("articulo/$it") } }
        pantalla("articulo/{id}", Modulo.ARTICULOS, nav) { ArticuloFormScreen(it, atras) }
        pantalla("condiciones", Modulo.CONDICIONES_PAGO, nav) { CondicionesScreen(atras) }

        // Ventas
        pantalla("clientes", Modulo.CLIENTES, nav) { ClientesScreen(atras) { nav.navigate("cliente/$it") } }
        pantalla("cliente/{id}", Modulo.CLIENTES, nav) { ClienteFormScreen(it, atras) }
        pantalla("facturas", Modulo.FACTURACION, nav) {
            FacturasScreen(atras, onNueva = { nav.navigate("facturaNueva") }, onVer = { nav.navigate("factura/$it") })
        }
        pantalla("facturaNueva", Modulo.FACTURACION, nav) {
            FacturaNuevaScreen(atras, onGuardada = { nav.navigate("factura/$it") { popUpTo("facturas") } })
        }
        pantalla("factura/{id}", Modulo.FACTURACION, nav) {
            FacturaDetalleScreen(it, atras, onCobrar = { f -> nav.navigate("cobro/$f") })
        }
        pantalla("cobros", Modulo.COBROS, nav) { CobrosScreen(atras, onNuevo = { nav.navigate("cobro/0") }) }
        pantalla("cobro/{id}", Modulo.COBROS, nav) { CobroNuevoScreen(it, atras) }
        pantalla("cxc", Modulo.CXC, nav) {
            CxCScreen(atras, onVerFactura = { f -> if (Sesion.puede(Modulo.FACTURACION)) nav.navigate("factura/$f") })
        }

        // Compras
        pantalla("proveedores", Modulo.PROVEEDORES, nav) { ProveedoresScreen(atras) { nav.navigate("proveedor/$it") } }
        pantalla("proveedor/{id}", Modulo.PROVEEDORES, nav) { ProveedorFormScreen(it, atras) }
        pantalla("compras", Modulo.COMPRAS, nav) {
            ComprasScreen(atras, onNueva = { nav.navigate("compraNueva") }, onVer = { nav.navigate("compra/$it") })
        }
        pantalla("compraNueva", Modulo.COMPRAS, nav) {
            CompraNuevaScreen(atras, onGuardada = { nav.navigate("compra/$it") { popUpTo("compras") } })
        }
        pantalla("compra/{id}", Modulo.COMPRAS, nav) {
            CompraDetalleScreen(it, atras, onPagar = { c -> nav.navigate("pago/$c") })
        }
        pantalla("pagos", Modulo.PAGOS, nav) { PagosScreen(atras, onNuevo = { nav.navigate("pago/0") }) }
        pantalla("pago/{id}", Modulo.PAGOS, nav) { PagoNuevoScreen(it, atras) }
        pantalla("cxp", Modulo.CXP, nav) {
            CxPScreen(atras, onVerCompra = { c -> if (Sesion.puede(Modulo.COMPRAS)) nav.navigate("compra/$c") })
        }

        // Tesorería
        pantalla("caja", Modulo.CAJA, nav) { CajaScreen(atras) }
        pantalla("bancos", Modulo.BANCOS, nav) { BancosScreen(atras) }
        pantalla("cuentas", Modulo.CUENTAS, nav) {
            CuentasScreen(atras, onAbrir = { nav.navigate("cuenta/$it") }, onMovimientos = { nav.navigate("cuentaMovs/$it") })
        }
        pantalla("cuenta/{id}", Modulo.CUENTAS, nav) { CuentaFormScreen(it, atras) }
        pantalla("cuentaMovs/{id}", Modulo.CUENTAS, nav) {
            CuentaMovimientosScreen(it, atras, onEditar = { c -> nav.navigate("cuenta/$c") })
        }
    }
}

/**
 * Registra una pantalla protegida: exige sesión iniciada y que el usuario tenga
 * permiso al [modulo]. Si la ruta lleva `{id}` se entrega como Long al contenido.
 */
private fun NavGraphBuilder.pantalla(
    ruta: String,
    modulo: Modulo?,
    nav: NavHostController,
    contenido: @Composable (Long) -> Unit
) {
    val conId = ruta.contains("{id}")
    composable(ruta, arguments = if (conId) listOf(navArgument("id") { type = NavType.LongType }) else emptyList()) { entrada ->
        val usuario = Sesion.usuario
        when {
            usuario == null -> LaunchedEffect(Unit) {
                // La sesión se pierde si Android cerró la app en segundo plano.
                nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
            }
            modulo != null && !Sesion.puede(modulo) -> Vacio("No tienes permiso para entrar a esta opción")
            else -> contenido(entrada.arguments?.getLong("id") ?: 0L)
        }
    }
}
