package com.sighec.pedidos.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Conexión directa al SQL Server de Profit usando jTDS (driver JDBC que
 * funciona en Android). Se abre una conexión por operación: en red local
 * es rápido y evita conexiones colgadas cuando la tablet se suspende.
 */
class SqlServerClient(private val configRepo: ConfigRepository) {

    init {
        Class.forName("net.sourceforge.jtds.jdbc.Driver")
    }

    private fun url(c: ConexionConfig) = buildString {
        append("jdbc:jtds:sqlserver://").append(c.servidor).append(':').append(c.puerto)
        append('/').append(c.baseDatos)
        if (c.instancia.isNotBlank()) append(";instance=").append(c.instancia)
        append(";loginTimeout=10;socketTimeout=60;appName=SighecPedidos")
    }

    private fun abrir(c: ConexionConfig): Connection =
        DriverManager.getConnection(url(c), c.usuario, c.clave)

    /** Ejecuta [bloque] con una conexión abierta, en un hilo de E/S. */
    suspend fun <T> usar(bloque: (Connection) -> T): T = withContext(Dispatchers.IO) {
        val c = configRepo.config.value
        if (!c.estaCompleta) throw IllegalStateException("Falta configurar la conexión al servidor")
        try {
            abrir(c).use(bloque)
        } catch (e: SQLException) {
            throw IllegalStateException(mensajeAmigable(e), e)
        }
    }

    /** Prueba una configuración (aún sin guardar) y devuelve la versión del servidor. */
    suspend fun probar(c: ConexionConfig): String = withContext(Dispatchers.IO) {
        try {
            abrir(c).use { con ->
                con.createStatement().use { st ->
                    st.executeQuery("SELECT @@VERSION").use { rs ->
                        rs.next()
                        rs.getString(1).lineSequence().first().trim()
                    }
                }
            }
        } catch (e: SQLException) {
            throw IllegalStateException(mensajeAmigable(e), e)
        }
    }

    private fun mensajeAmigable(e: SQLException): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("Login failed", true) -> "Usuario o clave de SQL Server incorrectos"
            m.contains("Network error", true) || m.contains("Unknown server host", true) ||
                m.contains("timed out", true) ->
                "No se pudo conectar al servidor. Verifique la IP, el puerto y que la tablet esté en la misma red."
            m.contains("Invalid object name", true) ->
                "Falta una tabla en la base de datos: $m"
            else -> m.ifBlank { "Error de base de datos" }
        }
    }
}
