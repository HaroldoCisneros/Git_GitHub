// MÓDULO :core:red (pruebas)
package com.siguec.puraceramica.core.red

// JUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Claves: el hash del teléfono es el MISMO formato de AppSighecMovil (Python). */
class SeguridadTest {

    @Test
    fun aceptaLosHashesHechosEnPython() {
        // hashlib.pbkdf2_hmac('sha256', b'clave123', b'0123456789abcdef', 1000)
        val python = "pbkdf2_sha256\$1000\$MDEyMzQ1Njc4OWFiY2RlZg==\$a3Got4DIN9sc/bzCYV4+HmsbXMjWHSUqRVoYXB/ihuY="
        assertTrue(Seguridad.claveCoincide("clave123", python))
        assertFalse(Seguridad.claveCoincide("clave124", python))
    }

    @Test
    fun elAdministradorInicialDelScriptSql() {
        // El de sql/02_tablas.sql: ADMIN / cambiar123
        val admin = "pbkdf2_sha256\$210000\$MDEyMzQ1Njc4OWFiY2RlZg==\$5kJNFgwCFsd/NnIOxZFa4Ueun5htQZYH3ptFNF5606U="
        assertTrue(Seguridad.claveCoincide("cambiar123", admin))
    }

    @Test
    fun cifrarYComprobar() {
        val hash = Seguridad.cifrarClave("secreta1", 1000)
        assertTrue(hash.startsWith("pbkdf2_sha256\$1000\$"))
        assertTrue(Seguridad.claveCoincide("secreta1", hash))
        assertFalse(Seguridad.claveCoincide("secreta2", hash))
        // Dos veces la misma clave dan hashes distintos (sal aleatoria)
        assertFalse(hash == Seguridad.cifrarClave("secreta1", 1000))
    }

    @Test
    fun hashDanadoNoEntra() {
        assertFalse(Seguridad.claveCoincide("x", ""))
        assertFalse(Seguridad.claveCoincide("x", "md5\$1\$a\$b"))
        assertFalse(Seguridad.claveCoincide("x", "pbkdf2_sha256\$no\$%%%\$***"))
        assertNotNull(Seguridad.validarClaveNueva("123"))
        assertNull(Seguridad.validarClaveNueva("123456"))
    }
}
