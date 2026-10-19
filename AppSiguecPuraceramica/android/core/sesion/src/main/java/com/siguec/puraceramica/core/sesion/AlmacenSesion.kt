// MÓDULO :core:sesion
package com.siguec.puraceramica.core.sesion

// Acceso a los archivos privados de la app
import android.content.Context
// Edición de SharedPreferences con "edit { }"
import androidx.core.content.edit
// Datos que se guardan como JSON
import com.siguec.puraceramica.core.red.ConfigConexion
import com.siguec.puraceramica.core.red.SesionDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Lo que se recuerda de un usuario que entró CON WiFi, para dejarlo entrar sin WiFi. */
@Serializable
internal data class UsuarioRecordado(val claveHash: String, val sesion: SesionDto)

/**
 * Lo que se guarda en el teléfono entre una apertura y otra de la App
 * (SharedPreferences: archivo privado de la App; allowBackup=false en el
 * manifiesto, así no se copia a la nube).
 *
 *  - Configuración de la conexión al SQL Server (incluida la clave de SQL).
 *  - La sesión actual (usuario y empresas) y la empresa elegida.
 *  - Por cada usuario que entró con WiFi: el HASH de su clave (nunca la clave)
 *    y sus permisos, para poder entrar sin WiFi.
 */
internal class AlmacenSesion(contexto: Context) {

    private val preferencias = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Datos de conexión (pantalla Configuración). */
    var conexion: ConfigConexion
        get() = preferencias.getString(CONEXION, null)
            ?.let { runCatching { json.decodeFromString(ConfigConexion.serializer(), it) }.getOrNull() }
            ?: ConfigConexion()
        set(valor) = preferencias.edit { putString(CONEXION, json.encodeToString(ConfigConexion.serializer(), valor)) }

    /** Último usuario que entró (se trae escrito en la pantalla de entrada). */
    var ultimoUsuario: String
        get() = preferencias.getString(USUARIO, "").orEmpty()
        set(valor) = preferencias.edit { putString(USUARIO, valor) }

    /** Sesión abierta (null = hay que entrar). */
    var sesion: SesionDto?
        get() = preferencias.getString(SESION, null)
            ?.let { runCatching { json.decodeFromString(SesionDto.serializer(), it) }.getOrNull() }
        set(valor) = preferencias.edit {
            if (valor == null) remove(SESION) else putString(SESION, json.encodeToString(SesionDto.serializer(), valor))
        }

    /** Empresa elegida la última vez (co_empresa). */
    var empresa: String?
        get() = preferencias.getString(EMPRESA, null)
        set(valor) = preferencias.edit { if (valor == null) remove(EMPRESA) else putString(EMPRESA, valor) }

    // ---------- Usuarios recordados (entrar sin WiFi) ----------

    private val serializadorRecordados = MapSerializer(String.serializer(), UsuarioRecordado.serializer())

    private fun recordados(): Map<String, UsuarioRecordado> = preferencias.getString(RECORDADOS, null)
        ?.let { runCatching { json.decodeFromString(serializadorRecordados, it) }.getOrNull() }
        .orEmpty()

    fun recordado(usuario: String): UsuarioRecordado? = recordados()[usuario.uppercase()]

    fun recordar(usuario: String, datos: UsuarioRecordado?) {
        val nuevos = recordados().toMutableMap()
        if (datos == null) nuevos.remove(usuario.uppercase()) else nuevos[usuario.uppercase()] = datos
        preferencias.edit { putString(RECORDADOS, json.encodeToString(serializadorRecordados, nuevos)) }
    }

    // ---------- Último inventario en que se contó (por empresa) ----------

    fun inventario(empresa: String): Int? = preferencias.getInt(INVENTARIO + empresa, 0).takeIf { it > 0 }

    fun guardarInventario(empresa: String, id: Int?) =
        preferencias.edit { if (id == null) remove(INVENTARIO + empresa) else putInt(INVENTARIO + empresa, id) }

    // Nombres de las claves dentro del archivo
    private companion object {
        const val CONEXION = "conexion"
        const val USUARIO = "ultimo_usuario"
        const val SESION = "sesion"
        const val EMPRESA = "empresa"
        const val RECORDADOS = "usuarios_recordados"
        const val INVENTARIO = "inventario_" // + co_empresa
    }
}
