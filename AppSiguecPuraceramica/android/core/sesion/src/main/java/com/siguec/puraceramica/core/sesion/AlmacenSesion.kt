// MÓDULO :core:sesion
// Paquete de la sesión
package com.siguec.puraceramica.core.sesion

// Acceso a los archivos privados de la app
import android.content.Context
// Edición de SharedPreferences con "edit { }"
import androidx.core.content.edit

/**
 * Lo que se guarda en el teléfono entre una apertura y otra de la App.
 *
 * Se usa SharedPreferences (archivo privado de la App: otras apps no lo
 * pueden leer). La CLAVE del usuario NUNCA se guarda: solo el token, que
 * vence solo en el servidor (<horas_sesion> del config.xml) y se puede anular.
 */
internal class AlmacenSesion(contexto: Context) {

    // Archivo "sesion.xml" dentro de la carpeta privada de la App
    private val preferencias = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    /** Dirección del servidor (se recuerda para no escribirla cada vez). */
    var urlServidor: String
        get() = preferencias.getString(URL, "").orEmpty()
        set(valor) = preferencias.edit { putString(URL, valor) }

    /** Último usuario que entró (se trae escrito en la pantalla de entrada). */
    var ultimoUsuario: String
        get() = preferencias.getString(USUARIO, "").orEmpty()
        set(valor) = preferencias.edit { putString(USUARIO, valor) }

    /** Token de la sesión (null = no hay sesión). */
    var token: String?
        get() = preferencias.getString(TOKEN, null)
        set(valor) = preferencias.edit { if (valor == null) remove(TOKEN) else putString(TOKEN, valor) }

    /** Empresa elegida la última vez (co_empresa). */
    var empresa: String?
        get() = preferencias.getString(EMPRESA, null)
        set(valor) = preferencias.edit { if (valor == null) remove(EMPRESA) else putString(EMPRESA, valor) }

    /** Último inventario en que se contó en esa empresa (para proponerlo al volver a contar). */
    fun inventario(empresa: String): Int? =
        preferencias.getInt(INVENTARIO + empresa, 0).takeIf { it > 0 }

    fun guardarInventario(empresa: String, id: Int?) =
        preferencias.edit { if (id == null) remove(INVENTARIO + empresa) else putInt(INVENTARIO + empresa, id) }

    // Nombres de las claves dentro del archivo
    private companion object {
        const val URL = "url_servidor"
        const val USUARIO = "ultimo_usuario"
        const val TOKEN = "token"
        const val EMPRESA = "empresa"
        const val INVENTARIO = "inventario_" // + co_empresa
    }
}
