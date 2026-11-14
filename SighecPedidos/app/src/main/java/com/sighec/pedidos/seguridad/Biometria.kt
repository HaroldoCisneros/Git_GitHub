package com.sighec.pedidos.seguridad

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Nivel de huella del equipo:
 *  FUERTE -> la clave se cifra con una llave del Keystore que solo se libera con la huella.
 *  DEBIL  -> lector clase 2: se pide la huella y la clave se guarda cifrada con el Keystore.
 */
enum class NivelBiometria { FUERTE, DEBIL, NINGUNO }

class BiometriaAuth(private val context: Context) {

    fun nivelDisponible(): NivelBiometria {
        val bm = BiometricManager.from(context)
        return when {
            bm.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> NivelBiometria.FUERTE
            bm.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS -> NivelBiometria.DEBIL
            else -> NivelBiometria.NINGUNO
        }
    }

    /** Devuelve null si la llave fue invalidada (p. ej. se registró una huella nueva). */
    fun cipherCifrar(nivel: NivelBiometria): Cipher? = crearCipher(nivel, null)

    fun cipherDescifrar(nivel: NivelBiometria, iv: ByteArray): Cipher? = crearCipher(nivel, iv)

    private fun crearCipher(nivel: NivelBiometria, iv: ByteArray?): Cipher? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            val llave = obtenerLlave(nivel)
            if (iv == null) cipher.init(Cipher.ENCRYPT_MODE, llave)
            else cipher.init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(128, iv))
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            borrarLlaves()
            null
        }
    }

    fun borrarLlaves() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        listOf(ALIAS_FUERTE, ALIAS_DEBIL).forEach { if (ks.containsAlias(it)) ks.deleteEntry(it) }
    }

    private fun obtenerLlave(nivel: NivelBiometria): SecretKey {
        val alias = if (nivel == NivelBiometria.FUERTE) ALIAS_FUERTE else ALIAS_DEBIL
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (nivel == NivelBiometria.FUERTE) {
            spec.setUserAuthenticationRequired(true)
            spec.setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        kg.init(spec.build())
        return kg.generateKey()
    }

    /** Muestra el diálogo de huella. En [onExito] recibe el cipher ya autorizado. */
    fun autenticar(
        activity: FragmentActivity,
        titulo: String,
        nivel: NivelBiometria,
        cipher: Cipher,
        onExito: (Cipher) -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = if (nivel == NivelBiometria.FUERTE) result.cryptoObject?.cipher else cipher
                    if (c != null) onExito(c) else onError("No se pudo validar la huella")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle("Coloque su dedo en el lector de huellas")
            .setNegativeButtonText("Usar contraseña")
            .setAllowedAuthenticators(if (nivel == NivelBiometria.FUERTE) BIOMETRIC_STRONG else BIOMETRIC_WEAK)
            .build()
        if (nivel == NivelBiometria.FUERTE) prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        else prompt.authenticate(info)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS_FUERTE = "sighec_huella_fuerte"
        const val ALIAS_DEBIL = "sighec_huella_debil"
    }
}

/** Guarda (cifrada) la contraseña del último usuario que activó la huella. */
class CredencialStore(context: Context) {
    private val prefs = context.getSharedPreferences("sighec_huella", Context.MODE_PRIVATE)

    val usuario: String? get() = prefs.getString(K_USUARIO, null)
    val nivel: NivelBiometria?
        get() = prefs.getString(K_NIVEL, null)?.let { runCatching { NivelBiometria.valueOf(it) }.getOrNull() }
    val iv: ByteArray? get() = prefs.getString(K_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun hayCredencial(): Boolean = usuario != null && nivel != null && prefs.contains(K_DATOS)

    fun guardar(usuario: String, nivel: NivelBiometria, cipherCifrado: Cipher, clave: String) {
        val datos = cipherCifrado.doFinal(clave.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(K_USUARIO, usuario)
            .putString(K_NIVEL, nivel.name)
            .putString(K_IV, Base64.encodeToString(cipherCifrado.iv, Base64.NO_WRAP))
            .putString(K_DATOS, Base64.encodeToString(datos, Base64.NO_WRAP))
            .apply()
    }

    fun descifrar(cipherDescifrado: Cipher): String {
        val datos = Base64.decode(prefs.getString(K_DATOS, ""), Base64.NO_WRAP)
        return String(cipherDescifrado.doFinal(datos), Charsets.UTF_8)
    }

    fun borrar() = prefs.edit().clear().apply()

    private companion object {
        const val K_USUARIO = "usuario"
        const val K_NIVEL = "nivel"
        const val K_IV = "iv"
        const val K_DATOS = "datos"
    }
}
