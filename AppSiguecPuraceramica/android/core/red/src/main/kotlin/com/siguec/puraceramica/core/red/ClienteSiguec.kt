// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Error de lectura/escritura en la red (sin internet, servidor apagado...)
import java.io.IOException
// Tiempos de espera
import java.util.concurrent.TimeUnit
// Cancelación de corrutinas (no es un error: se deja pasar)
import kotlin.coroutines.cancellation.CancellationException
// JSON
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
// Cliente HTTP
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
// Retrofit
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Cliente de la API de AppSiguecPuraceramica para UN servidor.
 *
 * - Agrega a cada petición el token de la sesión ("Authorization: Bearer ...").
 * - Convierte cualquier falla en un [Resultado.Error] con un mensaje claro:
 *   las pantallas nunca ven excepciones.
 * - Si el servidor responde 401 (sesión vencida) avisa con [alVencerSesion]
 *   para que la App vuelva a la pantalla de entrada.
 *
 * @param urlBase dirección del servidor (ej. "https://siguec.miempresa.com")
 * @param token función que devuelve el token guardado (o null si no hay sesión)
 * @param alVencerSesion se llama cuando el servidor dice que la sesión ya no sirve
 * @param http cliente HTTP (las pruebas pasan uno propio)
 */
class ClienteSiguec(
    urlBase: String,
    private val token: () -> String?,
    private val alVencerSesion: () -> Unit = {},
    http: OkHttpClient? = null
) {
    /** La dirección ya normalizada (con "https://" y "/" al final). */
    val urlBase: HttpUrl = normalizarUrl(urlBase)
        ?: throw IllegalArgumentException("Dirección del servidor no válida: $urlBase")

    // OkHttp: tiempos de espera y el "interceptor" que agrega el token a cada petición
    private val clienteHttp: OkHttpClient = (http?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS) // Máximo para conectarse
        .readTimeout(30, TimeUnit.SECONDS)    // Máximo esperando la respuesta
        .addInterceptor { cadena ->
            val peticion = cadena.request().newBuilder()
                .header("Accept", "application/json")
            // Solo si hay sesión se agrega el token
            token()?.takeIf { it.isNotBlank() }?.let { peticion.header("Authorization", "Bearer $it") }
            cadena.proceed(peticion.build())
        }
        .build()

    // Retrofit: convierte las funciones de ApiSiguec en peticiones HTTP
    internal val api: ApiSiguec = Retrofit.Builder()
        .baseUrl(this.urlBase)
        .client(clienteHttp)
        .addConverterFactory(JSON.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiSiguec::class.java)

    /**
     * Ejecuta una llamada y la convierte en [Resultado].
     * @param avisarSesionVencida false en el login: ahí un 401 es "clave errada", no "sesión vencida"
     */
    internal suspend fun <T> llamar(
        avisarSesionVencida: Boolean = true,
        bloque: suspend ApiSiguec.() -> T
    ): Resultado<T> = try {
        Resultado.Exito(api.bloque())
    } catch (e: CancellationException) {
        throw e // La pantalla se cerró: no es un error
    } catch (e: HttpException) {
        val codigo = e.code()
        val tipo = tipoDeError(codigo)
        if (tipo == TipoError.SESION && avisarSesionVencida) alVencerSesion()
        Resultado.Error(mensajeDelServidor(e) ?: mensajePorCodigo(codigo), tipo, codigo)
    } catch (e: IOException) {
        Resultado.Error(
            "No se pudo conectar con el servidor ${urlBase.host}. Revise el internet " +
                "y la dirección del servidor.",
            TipoError.RED
        )
    } catch (e: SerializationException) {
        Resultado.Error("El servidor respondió algo inesperado. ¿La dirección es la de AppSiguecPuraceramica?",
            TipoError.SERVIDOR)
    } catch (e: IllegalArgumentException) {
        Resultado.Error("Respuesta no válida del servidor: ${e.message}", TipoError.SERVIDOR)
    }

    companion object {
        /** JSON tolerante: si el servidor agrega campos nuevos, esta versión de la App los ignora. */
        val JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /**
         * "siguec.miempresa.com" -> "https://siguec.miempresa.com/".
         * Se respeta "http://" si el usuario lo escribe (pruebas en la red local).
         * @return null si no es una dirección válida
         */
        fun normalizarUrl(texto: String): HttpUrl? {
            var url = texto.trim()
            if (url.isEmpty()) return null
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            if (!url.endsWith("/")) url += "/"
            return url.toHttpUrlOrNull()
        }

        internal fun tipoDeError(codigo: Int): TipoError = when (codigo) {
            401 -> TipoError.SESION
            403 -> TipoError.PERMISO
            404 -> TipoError.NO_ENCONTRADO
            409 -> TipoError.CONFLICTO
            400, 422 -> TipoError.VALIDACION
            else -> TipoError.SERVIDOR
        }

        /** El "detalle" del JSON de error, si el servidor lo mandó. */
        private fun mensajeDelServidor(e: HttpException): String? = try {
            e.response()?.errorBody()?.string()
                ?.let { JSON.decodeFromString(ErrorDto.serializer(), it).detalle }
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null // No era JSON (por ejemplo, una página de error del proxy)
        }

        private fun mensajePorCodigo(codigo: Int): String = when (codigo) {
            401 -> "La sesión venció. Entre de nuevo."
            403 -> "No tiene permiso para esta opción."
            404 -> "No se encontró. ¿La dirección del servidor es correcta?"
            502, 503, 504 -> "El servidor de AppSiguecPuraceramica no está disponible en este momento."
            else -> "Error del servidor ($codigo)."
        }
    }
}
