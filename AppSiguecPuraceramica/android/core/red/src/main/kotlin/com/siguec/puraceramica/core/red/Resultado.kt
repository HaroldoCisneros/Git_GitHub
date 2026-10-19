// MÓDULO :core:red
package com.siguec.puraceramica.core.red

/**
 * Resultado de una operación con SQL Server: o salió bien (con el valor) o
 * salió mal (con un mensaje listo para mostrarle al usuario).
 * "sealed" = solo existen estos dos casos; el "when" obliga a tratar ambos.
 */
sealed interface Resultado<out T> {

    /** Salió bien. */
    data class Exito<T>(val valor: T) : Resultado<T>

    /**
     * Salió mal.
     * @property mensaje texto para el usuario (en español)
     * @property tipo qué clase de error fue (para decidir qué hacer)
     */
    data class Error(val mensaje: String, val tipo: TipoError) : Resultado<Nothing>
}

/** Clases de error. */
enum class TipoError {
    SIN_CONEXION,  // No se llegó al SQL Server (fuera del WiFi, servidor apagado, dirección errada)
    CONEXION,      // Se llegó, pero la configuración no sirve (usuario/clave de SQL, base que no existe)
    CLAVE,         // Usuario o clave de la App incorrectos, bloqueado o inactivo
    PERMISO,       // Su ambiente no lo permite
    NO_ENCONTRADO, // No existe
    CONFLICTO,     // Ya existe / ya se envió / ya está cerrado
    VALIDACION,    // Un dato no es válido
    SERVIDOR       // Cualquier otro error de SQL Server
}

/**
 * Error con un mensaje para el usuario. Se lanza DENTRO de una operación con la
 * base (BaseDatos.usar): la transacción se deshace y se convierte en [Resultado.Error].
 */
class ErrorApp(val mensaje: String, val tipo: TipoError = TipoError.VALIDACION) : Exception(mensaje)

/** Atajo: el valor si salió bien, o null. */
fun <T> Resultado<T>.valorOrNull(): T? = (this as? Resultado.Exito)?.valor
