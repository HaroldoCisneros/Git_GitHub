package com.sighec.pedidos

import android.app.Application
import android.content.Context
import com.sighec.pedidos.data.CatalogoRepository
import com.sighec.pedidos.data.ConfigRepository
import com.sighec.pedidos.data.PedidoRepository
import com.sighec.pedidos.data.SqlServerClient
import com.sighec.pedidos.data.Usuario
import com.sighec.pedidos.data.UsuarioRepository
import com.sighec.pedidos.seguridad.BiometriaAuth
import com.sighec.pedidos.seguridad.CredencialStore

class SighecApp : Application() {
    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
    }
}

/** Dependencias compartidas de la app (inyección manual, sin frameworks). */
class Contenedor(context: Context) {
    val config = ConfigRepository(context)
    val sql = SqlServerClient(config)
    val usuarios = UsuarioRepository(sql)
    val catalogo = CatalogoRepository(sql, config)
    val pedidos = PedidoRepository(sql)
    val credenciales = CredencialStore(context)
    val biometria = BiometriaAuth(context)

    /** Usuario con sesión iniciada. */
    var usuarioActual: Usuario? = null
}

val Context.contenedor: Contenedor
    get() = (applicationContext as SighecApp).contenedor
