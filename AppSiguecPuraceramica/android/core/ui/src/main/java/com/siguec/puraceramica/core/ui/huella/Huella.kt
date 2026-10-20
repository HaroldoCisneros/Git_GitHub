// MÓDULO :core:ui
// Paquete de la autenticación con huella
package com.siguec.puraceramica.core.ui.huella

// Contexto para preguntar si el teléfono tiene huella
import android.content.Context
// Llaves guardadas en el chip de seguridad del teléfono (Android Keystore)
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
// Texto en Base64 para guardar lo cifrado
import android.util.Base64
// Ventana de huella de Android
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
// Ejecutar en el hilo de la pantalla
import androidx.core.content.ContextCompat
// La ventana de huella necesita una FragmentActivity (MainActivity lo es)
import androidx.fragment.app.FragmentActivity
// Cifrado AES-GCM
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
// Esperar la respuesta de la ventana de huella dentro de una corrutina
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Lo cifrado con la huella de un usuario (se guarda en el teléfono, ver :core:sesion). */
data class SecretoHuella(val cifrado: String, val iv: String)

/** Respuesta de la ventana de huella. */
sealed interface ResultadoHuella<out T> {
    data class Exito<T>(val valor: T) : ResultadoHuella<T>
    /** El usuario cerró la ventana o tocó "Usar clave". */
    data object Cancelada : ResultadoHuella<Nothing>
    /** Se agregó o borró una huella en el teléfono: la guardada ya no sirve, hay que activarla de nuevo. */
    data object Invalidada : ResultadoHuella<Nothing>
    data class Error(val mensaje: String) : ResultadoHuella<Nothing>
}

/**
 * Entrada con huella.
 *
 * Cómo funciona: al activar la huella, la CLAVE del usuario se cifra con una llave del
 * Android Keystore que solo se puede usar después de poner una huella del teléfono
 * (la llave nunca sale del chip de seguridad). Para entrar con huella se descifra la
 * clave y se entra igual que escribiéndola: el servidor revisa clave, bloqueo y
 * permisos como siempre. Si se agrega una huella nueva al teléfono, Android invalida
 * la llave y hay que volver a entrar con la clave para activarla.
 *
 * OJO: Android no distingue DE QUIÉN es la huella: sirve cualquier huella registrada
 * en el teléfono. Por eso cada usuario debe activar la huella solo en su teléfono.
 */
object Huella {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val PREFIJO_LLAVE = "huella_"           // + usuario
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128

    /** ¿El teléfono tiene lector de huella y al menos una huella registrada? */
    fun disponible(contexto: Context): Boolean =
        BiometricManager.from(contexto).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Activa la huella: pide la huella y cifra [clave] para [usuario]. */
    suspend fun cifrar(actividad: FragmentActivity, usuario: String, clave: String): ResultadoHuella<SecretoHuella> {
        val cifrador = try {
            borrar(usuario) // Llave nueva cada vez que se activa
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, crearLlave(usuario)) }
        } catch (e: Exception) {
            return ResultadoHuella.Error("No se pudo preparar la huella: ${e.message}")
        }
        return pedirHuella(actividad, cifrador, "Activar entrada con huella", "Usuario $usuario") { c ->
            SecretoHuella(
                cifrado = Base64.encodeToString(c.doFinal(clave.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP),
                iv = Base64.encodeToString(c.iv, Base64.NO_WRAP)
            )
        }
    }

    /** Pide la huella y devuelve la clave guardada de [usuario]. */
    suspend fun descifrar(actividad: FragmentActivity, usuario: String, secreto: SecretoHuella,
                          titulo: String = "Entrar con huella"): ResultadoHuella<String> {
        val cifrador = try {
            val llave = leerLlave(usuario) ?: return ResultadoHuella.Invalidada
            Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(BITS_ETIQUETA, Base64.decode(secreto.iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            borrar(usuario)
            return ResultadoHuella.Invalidada
        } catch (e: Exception) {
            return ResultadoHuella.Error("No se pudo leer la huella guardada: ${e.message}")
        }
        return pedirHuella(actividad, cifrador, titulo, "Usuario $usuario") { c ->
            String(c.doFinal(Base64.decode(secreto.cifrado, Base64.NO_WRAP)), Charsets.UTF_8)
        }
    }

    /** Borra la llave de [usuario] (al quitar la huella o si quedó invalidada). */
    fun borrar(usuario: String) {
        runCatching { almacen().deleteEntry(alias(usuario)) }
    }

    // ---------------------------------------------------------------------------

    private fun alias(usuario: String) = PREFIJO_LLAVE + usuario.uppercase()

    private fun almacen(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun leerLlave(usuario: String): SecretKey? = almacen().getKey(alias(usuario), null) as? SecretKey

    /** Llave AES que solo se puede usar con una huella (y se invalida si cambian las huellas del teléfono). */
    private fun crearLlave(usuario: String): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(alias(usuario), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)       // Solo después de poner la huella
                .setInvalidatedByBiometricEnrollment(true) // Huella nueva en el teléfono = hay que activarla otra vez
                .build()
        )
        return generador.generateKey()
    }

    /** Muestra la ventana de huella de Android y, si la acepta, usa el cifrador desbloqueado. */
    private suspend fun <T> pedirHuella(
        actividad: FragmentActivity,
        cifrador: Cipher,
        titulo: String,
        subtitulo: String,
        usar: (Cipher) -> T
    ): ResultadoHuella<T> = suspendCancellableCoroutine { continuacion ->
        val respuesta = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                val c = resultado.cryptoObject?.cipher
                val r = if (c == null) ResultadoHuella.Error("La huella no desbloqueó la llave.") else
                    try { ResultadoHuella.Exito(usar(c)) } catch (e: Exception) { ResultadoHuella.Error("Huella: ${e.message}") }
                if (continuacion.isActive) continuacion.resume(r)
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                val r = when (codigo) {
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> ResultadoHuella.Cancelada
                    else -> ResultadoHuella.Error(mensaje.toString())
                }
                if (continuacion.isActive) continuacion.resume(r)
            }
            // onAuthenticationFailed (huella no reconocida): la ventana sigue abierta y deja reintentar
        }
        val ventana = BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), respuesta)
        val datos = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText("Usar clave")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        ventana.authenticate(datos, BiometricPrompt.CryptoObject(cifrador))
        continuacion.invokeOnCancellation { ventana.cancelAuthentication() }
    }
}
