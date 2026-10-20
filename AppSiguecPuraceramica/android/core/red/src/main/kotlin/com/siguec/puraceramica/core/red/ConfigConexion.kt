// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Para guardar la configuración en el teléfono como JSON
import kotlinx.serialization.Serializable

/**
 * Datos para conectarse DIRECTO al SQL Server de Profit por el WiFi de la empresa.
 * Se escriben en la pantalla "Configuración" (botón en la entrada y en el menú).
 *
 * @property servidor IP o nombre del equipo del SQL Server (ej. 192.168.1.10 o APPSRV)
 * @property puerto puerto TCP (1433 normalmente); vacío = lo busca por la [instancia]
 * @property instancia instancia con nombre (ej. SQLEXPRESS); vacío = la predeterminada
 * @property usuario usuario de SQL Server de la App (siguec_puraceramica: lo crea sql/03)
 * @property clave su clave (se guarda solo en este teléfono, nunca se muestra)
 * @property baseApp base propia de la App con usuarios y permisos
 * @property baseProfit base de la empresa en Profit 2K12 (ej. SAPURA_A): artículos, inventarios,
 *           monedas y resultado de inventario
 * @property coMone moneda del resultado de inventario (saMoneda de Profit). Se elige una vez en
 *           Configuración; la tasa se lee de saMoneda.cambio al enviar
 */
@Serializable
data class ConfigConexion(
    val servidor: String = "",
    val puerto: String = "1433",
    val instancia: String = "",
    val usuario: String = "siguec_puraceramica",
    val clave: String = "",
    val baseApp: String = "SIGUEC_PURACERAMICA",
    val baseProfit: String = "",
    val coMone: String = ""
) {
    /** ¿Están los datos mínimos? */
    val completa: Boolean get() = servidor.isNotBlank() && usuario.isNotBlank() && validarBase(baseApp) == null &&
        validarBase(baseProfit) == null

    /**
     * Dirección JDBC de jTDS para una base. Ejemplo:
     * jdbc:jtds:sqlserver://192.168.1.10:1433/SIGUEC_PURACERAMICA;appName=AppSiguecPuraceramica;...
     */
    fun urlJdbc(base: String): String {
        val puertoTexto = puerto.trim().takeIf { it.isNotEmpty() }?.let { ":$it" }.orEmpty()
        val partes = mutableListOf(
            "jdbc:jtds:sqlserver://${servidor.trim()}$puertoTexto/$base",
            "appName=AppSiguecPuraceramica",
            "loginTimeout=$SEGUNDOS_CONECTAR",  // Fuera del WiFi falla rápido
            "socketTimeout=$SEGUNDOS_RESPUESTA"
        )
        if (instancia.isNotBlank()) partes.add("instance=${instancia.trim()}")
        return partes.joinToString(";")
    }

    /** Revisa lo escrito en Configuración. null = está bien; si no, el mensaje. */
    fun validar(): String? = when {
        servidor.isBlank() -> "Escriba la IP o el nombre del servidor SQL"
        servidor.any { it == ';' || it == '/' || it.isWhitespace() } -> "La dirección del servidor no es válida"
        puerto.isNotBlank() && (puerto.trim().toIntOrNull() ?: 0) !in 1..65535 -> "El puerto debe ser un número (1433)"
        puerto.isBlank() && instancia.isBlank() -> "Escriba el puerto o el nombre de la instancia"
        instancia.any { it == ';' } -> "La instancia no es válida"
        usuario.isBlank() -> "Escriba el usuario de SQL Server"
        coMone.isNotEmpty() && !Regex("^[A-Za-z0-9$]{1,6}$").matches(coMone) -> "Código de moneda no válido: '$coMone'"
        baseProfit.isBlank() -> "Escriba la base de datos de Profit (la de la empresa, ej. SAPURA_A)"
        else -> validarBase(baseApp) ?: validarBase(baseProfit)
    }

    companion object {
        const val SEGUNDOS_CONECTAR = 8
        const val SEGUNDOS_RESPUESTA = 180   // La sincronización puede tardar

        /** Nombre de base: solo letras, números y guion bajo (va dentro del SQL). */
        private val PATRON_BASE = Regex("^[A-Za-z0-9_]{1,128}$")

        fun validarBase(base: String): String? =
            if (PATRON_BASE.matches(base.trim())) null else "Nombre de base de datos no válido: '$base'"
    }
}
