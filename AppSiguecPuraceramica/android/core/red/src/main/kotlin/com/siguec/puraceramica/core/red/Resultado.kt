// MÓDULO :core:red
package com.siguec.puraceramica.core.red

/**
 * Resultado de una llamada a la API: o salió bien (con el valor) o salió
 * mal (con un mensaje listo para mostrarle al usuario).
 * "sealed" = solo existen estos dos casos; el "when" obliga a tratar ambos.
 */
sealed interface Resultado<out T> {

    /** Salió bien. */
    data class Exito<T>(val valor: T) : Resultado<T>

    /**
     * Salió mal.
     * @property mensaje texto para el usuario (en español, viene del servidor o de la App)
     * @property tipo qué clase de error fue (para decidir qué hacer)
     * @property codigoHttp código del servidor (401, 403, 409...) o null si no hubo respuesta
     */
    data class Error(
        val mensaje: String,
        val tipo: TipoError,
        val codigoHttp: Int? = null
    ) : Resultado<Nothing>
}

/** Clases de error, según el código HTTP de la API (ver servidor/api/aplicacion.py). */
enum class TipoError {
    SESION,        // 401: la sesión venció o la clave está mal -> pedir la clave
    PERMISO,       // 403: su ambiente no lo permite
    NO_ENCONTRADO, // 404: no existe
    CONFLICTO,     // 409: otro usuario lo modificó -> recargar
    VALIDACION,    // 422: un dato no es válido
    SERVIDOR,      // 500 / 503: falla en el servidor o en SQL Server
    RED            // No hubo respuesta: sin internet o dirección del servidor errada
}

/** Atajo: el valor si salió bien, o null. */
fun <T> Resultado<T>.valorOrNull(): T? = (this as? Resultado.Exito)?.valor
