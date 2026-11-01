package com.sighec.movilplus.seguridad

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sighec.movilplus.data.Usuario
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Opciones del sistema. El administrador decide en el Maestro de Usuarios
 * qué módulos puede ver cada usuario.
 */
enum class Modulo(val titulo: String, val grupo: String, val icono: ImageVector, val ruta: String) {
    FACTURACION("Facturación", "Ventas", Icons.Filled.ReceiptLong, "facturas"),
    COBROS("Cobros", "Ventas", Icons.Filled.Payments, "cobros"),
    CXC("Cuentas por cobrar", "Ventas", Icons.Filled.Assessment, "cxc"),
    CLIENTES("Clientes", "Ventas", Icons.Filled.People, "clientes"),

    COMPRAS("Factura de compras", "Compras", Icons.Filled.ShoppingCart, "compras"),
    PAGOS("Pagos", "Compras", Icons.Filled.CreditCard, "pagos"),
    CXP("Cuentas por pagar", "Compras", Icons.Filled.RequestQuote, "cxp"),
    PROVEEDORES("Proveedores", "Compras", Icons.Filled.Store, "proveedores"),

    CAJA("Caja", "Tesorería", Icons.Filled.PointOfSale, "caja"),
    BANCOS("Bancos", "Tesorería", Icons.Filled.AccountBalance, "bancos"),
    CUENTAS("Cuentas bancarias", "Tesorería", Icons.Filled.AccountBalanceWallet, "cuentas"),

    ARTICULOS("Artículos", "Catálogos", Icons.Filled.Inventory2, "articulos"),
    CONDICIONES_PAGO("Condiciones de pago", "Catálogos", Icons.Filled.EventNote, "condiciones"),

    USUARIOS("Maestro de usuarios", "Administración", Icons.Filled.ManageAccounts, "usuarios"),
    BITACORA("Bitácora", "Administración", Icons.Filled.History, "bitacora");

    companion object {
        fun desdeTexto(permisos: String): Set<Modulo> =
            permisos.split(",").mapNotNull { p -> entries.firstOrNull { it.name == p.trim() } }.toSet()

        fun aTexto(modulos: Set<Modulo>): String = modulos.joinToString(",") { it.name }
    }
}

/** Hash de contraseñas con PBKDF2 + salt aleatorio (nunca se guarda la contraseña en claro). */
object Contrasenas {
    private const val ITERACIONES = 120_000
    private const val LONGITUD_BITS = 256

    fun nuevoSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERACIONES, LONGITUD_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verificar(password: String, u: Usuario): Boolean =
        java.security.MessageDigest.isEqual(hash(password, u.salt).toByteArray(), u.passwordHash.toByteArray())
}

/** Usuario que inició sesión. */
object Sesion {
    var usuario by mutableStateOf<Usuario?>(null)
        private set

    val nombreUsuario: String get() = usuario?.usuario ?: "sistema"

    fun iniciar(u: Usuario) { usuario = u }
    fun cerrar() { usuario = null }

    fun puede(m: Modulo): Boolean {
        val u = usuario ?: return false
        return u.esAdmin || m in Modulo.desdeTexto(u.permisos)
    }
}

/** Preferencias locales del dispositivo (usuario enrolado para entrar con huella). */
class PreferenciasDispositivo(context: Context) {
    private val prefs = context.getSharedPreferences("sighec_prefs", Context.MODE_PRIVATE)

    var usuarioHuella: String?
        get() = prefs.getString("usuario_huella", null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("usuario_huella") else editor.putString("usuario_huella", value)
            editor.apply()
        }

    var ultimoUsuario: String
        get() = prefs.getString("ultimo_usuario", "") ?: ""
        set(value) { prefs.edit().putString("ultimo_usuario", value).apply() }
}

object Biometria {
    private const val AUTENTICADORES = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun disponible(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(
        activity: FragmentActivity,
        titulo: String,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onExito()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        onError(errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle("SighecMovilPlus")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(AUTENTICADORES)
            .build()
        prompt.authenticate(info)
    }
}

fun Context.buscarActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}
