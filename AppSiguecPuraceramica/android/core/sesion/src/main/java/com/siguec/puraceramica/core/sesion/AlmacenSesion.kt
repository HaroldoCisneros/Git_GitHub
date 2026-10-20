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
 * Entrada con huella de un usuario en este teléfono: su clave CIFRADA con una llave del
 * Android Keystore que solo se abre con la huella (ver core/ui/huella/Huella.kt).
 */
@Serializable
data class HuellaGuardada(val cifrado: String, val iv: String)

/**
 * Lo que se guarda en el teléfono entre una apertura y otra de la App
 * (SharedPreferences: archivo privado de la App; allowBackup=false en el
 * manifiesto, así no se copia a la nube).
 *
 *  - Configuración de la conexión al SQL Server (incluida la clave de SQL).
 *  - La sesión actual (usuario y empresas) y la empresa elegida.
 *  - Por cada usuario que entró con WiFi: el HASH de su clave (nunca la clave)
 *    y sus permisos, para poder entrar sin WiFi.
 *  - Por cada usuario que activó la huella: su clave cifrada (solo se abre con la huella).
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

    // ---------- Claves erradas en el teléfono (la entrada es sin conexión) ----------

    /** Hasta cuándo está bloqueado ese usuario en este teléfono (milisegundos; 0 = no). */
    fun bloqueoLocal(usuario: String): Long = preferencias.getLong(BLOQUEO + usuario.uppercase(), 0L)

    /** Una clave errada más; al llegar a [maximo], bloquea [minutos] minutos. */
    fun sumarFalloLocal(usuario: String, maximo: Int, minutos: Int) {
        val u = usuario.uppercase()
        val fallos = preferencias.getInt(FALLOS + u, 0) + 1
        preferencias.edit {
            if (fallos >= maximo) {
                putInt(FALLOS + u, 0)
                putLong(BLOQUEO + u, System.currentTimeMillis() + minutos * 60_000L)
            } else {
                putInt(FALLOS + u, fallos)
            }
        }
    }

    fun limpiarFallosLocales(usuario: String) = preferencias.edit {
        remove(FALLOS + usuario.uppercase())
        remove(BLOQUEO + usuario.uppercase())
    }

    // ---------- Huella (por usuario) ----------

    private val serializadorHuellas = MapSerializer(String.serializer(), HuellaGuardada.serializer())

    private fun huellas(): Map<String, HuellaGuardada> = preferencias.getString(HUELLAS, null)
        ?.let { runCatching { json.decodeFromString(serializadorHuellas, it) }.getOrNull() }
        .orEmpty()

    fun huella(usuario: String): HuellaGuardada? = huellas()[usuario.uppercase()]

    fun guardarHuella(usuario: String, huella: HuellaGuardada?) {
        val nuevas = huellas().toMutableMap()
        if (huella == null) nuevas.remove(usuario.uppercase()) else nuevas[usuario.uppercase()] = huella
        preferencias.edit { putString(HUELLAS, json.encodeToString(serializadorHuellas, nuevas)) }
    }

    /** El usuario dijo "No" a activar la huella: no se le vuelve a preguntar en este teléfono. */
    fun huellaRechazada(usuario: String): Boolean = preferencias.getBoolean(SIN_HUELLA + usuario.uppercase(), false)

    fun rechazarHuella(usuario: String, rechazada: Boolean) =
        preferencias.edit { putBoolean(SIN_HUELLA + usuario.uppercase(), rechazada) }

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
        const val HUELLAS = "huellas"
        const val FALLOS = "fallos_"         // + usuario
        const val BLOQUEO = "bloqueo_"       // + usuario
        const val SIN_HUELLA = "sin_huella_" // + usuario
    }
}
