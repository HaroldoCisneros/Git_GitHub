// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Hash de claves (viene con Java / Android 8 en adelante)
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Claves de los usuarios de la App: NUNCA se guardan en texto. Se guarda un
 * hash PBKDF2-SHA256 con sal aleatoria, en el MISMO formato de AppSighecMovil:
 *
 *     pbkdf2_sha256$<repeticiones>$<sal en base64>$<hash en base64>
 *
 * (los usuarios creados con la herramienta de Python de AppSighecMovil sirven aquí).
 */
object Seguridad {

    private const val ALGORITMO = "pbkdf2_sha256"
    const val REPETICIONES = 210_000        // Recomendación de OWASP para PBKDF2-SHA256
    private const val BYTES_SAL = 16
    private const val BITS_HASH = 256
    const val LARGO_MINIMO_CLAVE = 6

    private fun pbkdf2(clave: String, sal: ByteArray, repeticiones: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(clave.toCharArray(), sal, repeticiones, BITS_HASH))
            .encoded

    /** Hash de la clave para guardar en usuarios.clave_hash (tarda ~1 s en el teléfono a propósito). */
    fun cifrarClave(clave: String, repeticiones: Int = REPETICIONES): String {
        val sal = ByteArray(BYTES_SAL).also { SecureRandom().nextBytes(it) }
        val b64 = Base64.getEncoder()
        return "$ALGORITMO\$$repeticiones\$${b64.encodeToString(sal)}\$${b64.encodeToString(pbkdf2(clave, sal, repeticiones))}"
    }

    /** true si la clave escrita corresponde al hash guardado. */
    fun claveCoincide(clave: String, guardado: String): Boolean = try {
        val partes = guardado.trim().split("$")
        if (partes.size != 4 || partes[0] != ALGORITMO) {
            false
        } else {
            val b64 = Base64.getDecoder()
            val calculado = pbkdf2(clave, b64.decode(partes[2]), partes[1].toInt())
            // isEqual tarda lo mismo acierte o no (no da pistas por el tiempo)
            MessageDigest.isEqual(calculado, b64.decode(partes[3]))
        }
    } catch (e: IllegalArgumentException) {
        false // Hash dañado
    }

    /** Reglas mínimas de una clave nueva. null = está bien; si no, el motivo. */
    fun validarClaveNueva(clave: String): String? =
        if (clave.length < LARGO_MINIMO_CLAVE) "La clave debe tener al menos $LARGO_MINIMO_CLAVE caracteres." else null
}
