// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// JDBC (viene con Java; el driver jTDS lo implementa para SQL Server)
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
// Corrutinas: el trabajo con la base va en un hilo aparte (nunca en el de la pantalla)
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Conexión DIRECTA a SQL Server (por el WiFi de la empresa) con el driver jTDS.
 *
 *     baseDatos.usar("PURACERAMICA") { cx ->        // una transacción
 *         val filas = cx.consultar("SELECT ... WHERE co_art = ?", codigo)
 *         cx.ejecutar("INSERT ...", ...)
 *     }
 *     // Sin error: COMMIT. Con error: ROLLBACK (se deshace todo) y Resultado.Error.
 *
 * Cada operación abre su conexión y la cierra al terminar: el teléfono no deja
 * conexiones abiertas mientras se cuenta sin WiFi.
 *
 * IMPORTANTE: todas las consultas usan parámetros ("?") y NUNCA se arman
 * concatenando lo que escribe el usuario (evita la inyección SQL).
 *
 * @param config datos de la pantalla Configuración
 */
class BaseDatos(private val config: ConfigConexion) {

    /** Servidor al que se conecta (para los mensajes). */
    val servidor: String get() = config.servidor

    /** Base propia de la App (usuarios y permisos). */
    val baseApp: String get() = config.baseApp.trim()

    /**
     * Abre una conexión a [base], ejecuta [bloque] en una transacción y la cierra.
     * Las fallas (sin WiFi, clave de SQL, [ErrorApp]...) llegan como [Resultado.Error].
     */
    suspend fun <T> usar(base: String = baseApp, bloque: (Conexion) -> T): Resultado<T> = withContext(Dispatchers.IO) {
        try {
            ConfigConexion.validarBase(base)?.let { throw ErrorApp(it, TipoError.CONEXION) }
            config.validar()?.let { throw ErrorApp("Revise la configuración de la conexión: $it", TipoError.CONEXION) }
            Class.forName(DRIVER)
            DriverManager.getConnection(config.urlJdbc(base), config.usuario.trim(), config.clave).use { jdbc ->
                jdbc.autoCommit = false
                try {
                    val valor = bloque(Conexion(jdbc, base))
                    jdbc.commit()
                    Resultado.Exito(valor)
                } catch (e: Throwable) {
                    runCatching { jdbc.rollback() } // Se deshace todo lo de esta operación
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e // La pantalla se cerró: no es un error
        } catch (e: ErrorApp) {
            Resultado.Error(e.mensaje, e.tipo)
        } catch (e: SQLException) {
            traducir(e, base)
        } catch (e: ClassNotFoundException) {
            Resultado.Error("Falta el driver jTDS de SQL Server en la App.", TipoError.SERVIDOR)
        }
    }

    /** Error de SQL Server -> mensaje claro en español. */
    private fun traducir(e: SQLException, base: String): Resultado.Error {
        val codigo = e.errorCode
        val estado = e.sqlState.orEmpty()
        return when {
            // jTDS: 08S01 = no hubo respuesta (sin red, servidor apagado, puerto cerrado)
            estado.startsWith("08") || codigo == 0 && e.message.orEmpty().contains("Network", ignoreCase = true) ->
                Resultado.Error("No se pudo conectar con el servidor ${config.servidor}. " +
                    "¿Está conectado al WiFi de la empresa?", TipoError.SIN_CONEXION)
            codigo == 18456 -> Resultado.Error(
                "El usuario de SQL Server '${config.usuario}' no existe o su clave no es correcta. El usuario se " +
                    "crea en el servidor con sql/03_permisos_usuario_sql.sql (Paso 1.3 de PASOS_A_SEGUIR).",
                TipoError.CONEXION)
            codigo == 4060 -> Resultado.Error("No se pudo abrir la base $base: no existe en el servidor o el " +
                "usuario de SQL '${config.usuario}' no tiene acceso (sql/03_permisos_usuario_sql.sql).", TipoError.CONEXION)
            codigo == 229 || codigo == 230 -> Resultado.Error(
                "El usuario de SQL Server no tiene permiso para esta operación (ver sql/03_permisos_usuario_sql.sql).",
                TipoError.PERMISO)
            // Columna que no existe: la base de la App es de una versión anterior de los scripts
            codigo == 207 -> Resultado.Error(
                "A la base $base le falta una columna (${e.message}). Si es la base de la App, ejecute de nuevo " +
                    "COMPLETO sql/02_tablas.sql (solo agrega lo que falta).", TipoError.SERVIDOR)
            codigo == 208 -> Resultado.Error(
                "Falta una tabla en la base $base: ${e.message}. Revise los nombres en parametros.", TipoError.SERVIDOR)
            else -> Resultado.Error("Error de SQL Server: ${e.message}", TipoError.SERVIDOR)
        }
    }

    private companion object {
        const val DRIVER = "net.sourceforge.jtds.jdbc.Driver"
    }
}

/**
 * Una conexión abierta dentro de una transacción (la entrega [BaseDatos.usar]).
 * Las filas se devuelven como mapas {"columna": valor}, con los nombres en minúsculas.
 */
class Conexion internal constructor(private val jdbc: Connection, val base: String) {

    /** Ejecuta un SELECT (puede empezar con SET NOCOUNT / INSERT) y devuelve las filas del primer resultado. */
    fun consultar(sql: String, vararg parametros: Any?): List<Map<String, Any?>> =
        preparar(sql, parametros).use { st ->
            var hayResultado = st.execute()
            // Se saltan los "x filas afectadas" hasta llegar al primer SELECT
            while (!hayResultado && st.updateCount != -1) hayResultado = st.moreResults
            if (!hayResultado) emptyList() else st.resultSet.use(::leerFilas)
        }

    /** Igual que [consultar] pero solo la primera fila (o null). */
    fun consultarUno(sql: String, vararg parametros: Any?): Map<String, Any?>? = consultar(sql, *parametros).firstOrNull()

    /** INSERT / UPDATE / DELETE. Devuelve las filas afectadas. */
    fun ejecutar(sql: String, vararg parametros: Any?): Int = preparar(sql, parametros).use { it.executeUpdate() }

    /**
     * Ejecuta un procedimiento almacenado de Profit (EXEC ...) y descarta lo que devuelva
     * (los de Softech terminan con un SELECT de control).
     */
    fun ejecutarProcedimiento(sql: String, vararg parametros: Any?) {
        preparar(sql, parametros).use { st ->
            var hayResultado = st.execute()
            // Se leen y descartan todos los resultados para que el procedimiento termine completo
            while (hayResultado || st.updateCount != -1) {
                if (hayResultado) st.resultSet.use { rs -> while (rs.next()) Unit }
                hayResultado = st.moreResults
            }
        }
    }

    /** El mismo INSERT para muchas filas, en bloques (sincronización, envío del conteo). */
    fun ejecutarLote(sql: String, filas: List<List<Any?>>, tamanoBloque: Int = 500) {
        if (filas.isEmpty()) return
        jdbc.prepareStatement(sql).use { st ->
            filas.chunked(tamanoBloque).forEach { bloque ->
                bloque.forEach { fila ->
                    fila.forEachIndexed { i, valor -> poner(st, i + 1, valor) }
                    st.addBatch()
                }
                st.executeBatch()
            }
        }
    }

    private fun preparar(sql: String, parametros: Array<out Any?>): PreparedStatement =
        jdbc.prepareStatement(sql).also { st -> parametros.forEachIndexed { i, valor -> poner(st, i + 1, valor) } }

    /** Pone un parámetro "?" según su tipo de Kotlin. */
    private fun poner(st: PreparedStatement, posicion: Int, valor: Any?) {
        when (valor) {
            null -> st.setNull(posicion, Types.VARCHAR)
            is String -> st.setString(posicion, valor)
            is Int -> st.setInt(posicion, valor)
            is Long -> st.setLong(posicion, valor)
            is Boolean -> st.setBoolean(posicion, valor)
            is BigDecimal -> st.setBigDecimal(posicion, valor)
            is LocalDateTime -> st.setTimestamp(posicion, Timestamp.valueOf(valor))
            else -> st.setObject(posicion, valor)
        }
    }

    private fun leerFilas(rs: ResultSet): List<Map<String, Any?>> {
        val datos = rs.metaData
        val columnas = (1..datos.columnCount).map { datos.getColumnLabel(it).lowercase() }
        val filas = mutableListOf<Map<String, Any?>>()
        while (rs.next()) filas.add(columnas.mapIndexed { i, c -> c to rs.getObject(i + 1) }.toMap())
        return filas
    }
}

// ---------- Lectura de valores de las filas ----------

/** Texto sin los espacios de relleno de los campos char de Profit ("" si es null). */
fun Map<String, Any?>.texto(columna: String): String = this[columna]?.toString()?.trim().orEmpty()

fun Map<String, Any?>.entero(columna: String): Int = (this[columna] as? Number)?.toInt() ?: texto(columna).toIntOrNull() ?: 0

fun Map<String, Any?>.logico(columna: String): Boolean = when (val v = this[columna]) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    null -> false
    else -> v.toString().trim().uppercase() in setOf("1", "S", "T", "TRUE", "SI", "SÍ")
}

fun Map<String, Any?>.decimal(columna: String): BigDecimal = when (val v = this[columna]) {
    is BigDecimal -> v
    is Number -> BigDecimal(v.toString())
    null -> BigDecimal.ZERO
    else -> v.toString().trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
}

/** Fecha de SQL Server -> "2026-10-19 14:30:00" (o null). */
fun Map<String, Any?>.fecha(columna: String): String? = (this[columna] as? java.util.Date)?.let {
    Timestamp(it.time).toLocalDateTime().withNano(0).toString().replace('T', ' ')
}
