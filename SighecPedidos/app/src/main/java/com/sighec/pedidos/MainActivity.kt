package com.sighec.pedidos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sighec.pedidos.ui.config.ConfigScreen
import com.sighec.pedidos.ui.login.LoginScreen
import com.sighec.pedidos.ui.menu.MenuScreen
import com.sighec.pedidos.ui.pedido.PedidoScreen
import com.sighec.pedidos.ui.theme.SighecTema

/** FragmentActivity (y no ComponentActivity) porque BiometricPrompt la necesita. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inicio = if (contenedor.config.config.value.estaCompleta) Rutas.LOGIN else Rutas.CONFIG

        setContent {
            SighecTema {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = inicio) {
                        composable(Rutas.CONFIG) {
                            ConfigScreen(
                                puedeVolver = nav.previousBackStackEntry != null,
                                onVolver = { nav.popBackStack() },
                                onGuardado = {
                                    if (!nav.popBackStack()) {
                                        nav.navigate(Rutas.LOGIN) { popUpTo(Rutas.CONFIG) { inclusive = true } }
                                    }
                                },
                            )
                        }
                        composable(Rutas.LOGIN) {
                            LoginScreen(
                                onConfig = { nav.navigate(Rutas.CONFIG) },
                                onIngreso = {
                                    nav.navigate(Rutas.MENU) { popUpTo(Rutas.LOGIN) { inclusive = true } }
                                },
                            )
                        }
                        composable(Rutas.MENU) {
                            SesionRequerida { nav.navigate(Rutas.LOGIN) { popUpTo(0) } }
                            MenuScreen(
                                onPedidos = { nav.navigate(Rutas.PEDIDO) },
                                onConfig = { nav.navigate(Rutas.CONFIG) },
                                onCerrarSesion = {
                                    contenedor.usuarioActual = null
                                    nav.navigate(Rutas.LOGIN) { popUpTo(Rutas.MENU) { inclusive = true } }
                                },
                            )
                        }
                        composable(Rutas.PEDIDO) {
                            SesionRequerida { nav.navigate(Rutas.LOGIN) { popUpTo(0) } }
                            PedidoScreen(onVolver = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

/** Si Android cerró la app en segundo plano se pierde la sesión: volver al login. */
@Composable
private fun FragmentActivity.SesionRequerida(irAlLogin: () -> Unit) {
    if (contenedor.usuarioActual == null) LaunchedEffect(Unit) { irAlLogin() }
}

object Rutas {
    const val CONFIG = "config"
    const val LOGIN = "login"
    const val MENU = "menu"
    const val PEDIDO = "pedido"
}
