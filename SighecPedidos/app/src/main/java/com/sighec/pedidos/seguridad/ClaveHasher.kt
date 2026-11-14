package com.sighec.pedidos.seguridad

import java.security.MessageDigest

/**
 * Debe coincidir con dbo.SighecGuardarUsuario:
 *   HASHBYTES('SHA2_256', @sal + CAST(@clave AS VARBINARY))  -- @clave es NVARCHAR (UTF-16LE)
 */
object ClaveHasher {
    fun hash(clave: String, sal: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(sal)
            update(clave.toByteArray(Charsets.UTF_16LE))
            digest()
        }

    fun verificar(clave: String, sal: ByteArray, esperado: ByteArray): Boolean =
        MessageDigest.isEqual(hash(clave, sal), esperado)
}
