package com.sighec.pedidos.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Datos para conectarse al SQL Server de Profit en la red local. */
data class ConexionConfig(
    val servidor: String = "",          // IP o nombre, ej: 192.168.1.10
    val puerto: Int = 1433,
    val instancia: String = "",         // ej: SQLEXPRESS (vacío = instancia por defecto)
    val baseDatos: String = "",
    val usuario: String = "",           // login SQL que usa la tablet
    val clave: String = "",
    val tipoPrecio: String = "01",      // co_precio de Profit a mostrar
    val almacen: String = "",           // co_alma para stock (vacío = todos)
    val ivaPorcentaje: Double = 16.0,
    val urlImagenes: String = "",       // ej: http://192.168.1.10/imagenes/{co_art}.jpg
) {
    val estaCompleta: Boolean
        get() = servidor.isNotBlank() && baseDatos.isNotBlank() && usuario.isNotBlank()
}

class ConfigRepository(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        "sighec_config",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _config = MutableStateFlow(leer())
    val config: StateFlow<ConexionConfig> = _config.asStateFlow()

    fun guardar(c: ConexionConfig) {
        prefs.edit()
            .putString("servidor", c.servidor.trim())
            .putInt("puerto", c.puerto)
            .putString("instancia", c.instancia.trim())
            .putString("baseDatos", c.baseDatos.trim())
            .putString("usuario", c.usuario.trim())
            .putString("clave", c.clave)
            .putString("tipoPrecio", c.tipoPrecio.trim())
            .putString("almacen", c.almacen.trim())
            .putFloat("iva", c.ivaPorcentaje.toFloat())
            .putString("urlImagenes", c.urlImagenes.trim())
            .apply()
        _config.value = leer()
    }

    private fun leer() = ConexionConfig(
        servidor = prefs.getString("servidor", "") ?: "",
        puerto = prefs.getInt("puerto", 1433),
        instancia = prefs.getString("instancia", "") ?: "",
        baseDatos = prefs.getString("baseDatos", "") ?: "",
        usuario = prefs.getString("usuario", "") ?: "",
        clave = prefs.getString("clave", "") ?: "",
        tipoPrecio = prefs.getString("tipoPrecio", "01") ?: "01",
        almacen = prefs.getString("almacen", "") ?: "",
        ivaPorcentaje = prefs.getFloat("iva", 16f).toDouble(),
        urlImagenes = prefs.getString("urlImagenes", "") ?: "",
    )
}
