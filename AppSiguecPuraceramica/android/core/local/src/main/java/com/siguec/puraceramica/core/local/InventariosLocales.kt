// MÓDULO :core:local
package com.siguec.puraceramica.core.local

// SQLite de Android
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
// Modelos y lógica compartida (:core:red)
import com.siguec.puraceramica.core.red.AperturaDto
import com.siguec.puraceramica.core.red.ConteoDto
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.InventarioDto
import com.siguec.puraceramica.core.red.RenglonEnvio
import com.siguec.puraceramica.core.red.TomaProfitDto
// Cantidades exactas y fechas
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
// Hilo aparte para la base
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que ve quien cuenta después de anotar. */
data class Anotacion(val conteo: ConteoDto, val contadoUsuario: String, val contadoTotal: String)

/**
 * Inventarios (abiertos de saInventarioFisico de Profit) y conteos, en el TELÉFONO.
 *
 *   abrir()            Guarda el inventario elegido con quién lo abrió y cuándo.
 *   lista() / leer()   Inventarios del teléfono con su avance.
 *   anotar()           Un conteo individual (a nombre de quien cuenta).
 *   borrar()           Borra un conteo propio (solo con el inventario abierto).
 *   paraEnviar()       Los conteos que se mandan a Profit al cerrar.
 *   marcarEnviado()    Ya se envió: no se puede contar más en este teléfono.
 */
class InventariosLocales internal constructor(private val base: BaseLocal) {

    private val columnas = """i.id, i.numero, i.co_alma, i.des_alma, i.descripcion, i.fecha, i.abierto, i.enviado_por,
        i.fecha_envio, (SELECT COUNT(DISTINCT co_art) FROM conteos c WHERE c.inventario_id = i.id) AS articulos,
        (SELECT COUNT(*) FROM conteos c WHERE c.inventario_id = i.id) AS conteos,
        i.art_cero, i.abierto_por, i.fecha_apertura"""

    private fun inventario(c: Cursor) = InventarioDto(
        id = c.getInt(0), numero = c.getString(1), coAlma = c.getString(2), desAlma = c.getString(3),
        descripcion = c.getString(4), fecha = c.getString(5), abierto = c.getInt(6) == 1,
        enviadoPor = c.getString(7).orEmpty(), fechaEnvio = c.getString(8),
        articulosContados = c.getInt(9), conteos = c.getInt(10),
        artCero = c.getInt(11) == 1, abiertoPor = c.getString(12).orEmpty(), fechaApertura = c.getString(13)
    )

    /** Id del inventario en el teléfono (null = no se ha abierto aquí). */
    suspend fun buscar(coEmpresa: String, numero: String): InventarioDto? = withContext(Dispatchers.IO) {
        base.readableDatabase.rawQuery("SELECT $columnas FROM inventarios i WHERE i.co_empresa = ? AND i.numero = ?",
            arrayOf(coEmpresa, numero)).use { c -> if (c.moveToFirst()) inventario(c) else null }
    }

    /**
     * Abre en el teléfono el inventario elegido, con quién lo abrió y cuándo.
     * Si ya estaba abierto aquí, se deja como está (no se pierden sus conteos).
     * @return el inventario guardado
     */
    suspend fun abrir(coEmpresa: String, toma: TomaProfitDto, apertura: AperturaDto): InventarioDto =
        withContext(Dispatchers.IO) {
            val db = base.writableDatabase
            db.insertWithOnConflict("inventarios", null, ContentValues().apply {
                put("co_empresa", coEmpresa)
                put("numero", toma.numero)
                put("co_alma", toma.coAlma)
                put("des_alma", toma.desAlma)
                put("descripcion", toma.descripcion)
                put("fecha", toma.fecha)
                put("art_cero", if (toma.artCero) 1 else 0)
                put("abierto_por", apertura.usuario)
                put("fecha_apertura", apertura.fecha)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            db.rawQuery("SELECT $columnas FROM inventarios i WHERE i.co_empresa = ? AND i.numero = ?",
                arrayOf(coEmpresa, toma.numero)).use { c -> c.moveToFirst(); inventario(c) }
        }

    /** Los inventarios de la empresa en el teléfono (abiertos primero). */
    suspend fun lista(coEmpresa: String, soloAbiertos: Boolean = false): List<InventarioDto> = withContext(Dispatchers.IO) {
        base.readableDatabase.rawQuery(
            "SELECT $columnas FROM inventarios i WHERE i.co_empresa = ? AND (? = '0' OR i.abierto = 1) " +
                "ORDER BY i.abierto DESC, i.id DESC",
            arrayOf(coEmpresa, if (soloAbiertos) "1" else "0")
        ).use { c -> buildList { while (c.moveToNext()) add(inventario(c)) } }
    }

    suspend fun leer(id: Int): InventarioDto? = withContext(Dispatchers.IO) {
        base.readableDatabase.rawQuery("SELECT $columnas FROM inventarios i WHERE i.id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) inventario(c) else null }
    }

    /**
     * Anota un conteo. @param cantidad ya validada ("6", "2.5", "-1")
     * @return null si el inventario ya se envió a Profit (no se puede contar más)
     */
    suspend fun anotar(id: Int, coArt: String, artDes: String, codigoLeido: String, cantidad: BigDecimal,
                       usuario: String): Anotacion? = withContext(Dispatchers.IO) {
        val db = base.writableDatabase
        if (!estaAbierto(db, id)) return@withContext null
        val fecha = ahora()
        val conteoId = db.insert("conteos", null, ContentValues().apply {
            put("inventario_id", id)
            put("co_art", coArt)
            put("codigo_leido", codigoLeido)
            put("cantidad", cantidad.toPlainString())
            put("usuario", usuario)
            put("fecha", fecha)
        })
        val filas = cantidades(db, id, coArt)
        Anotacion(
            ConteoDto(conteoId, coArt, artDes, codigoLeido, Inventario.texto(cantidad), usuario, fecha, puedeBorrar = true),
            contadoUsuario = Inventario.sumar(filas.filter { it.first == usuario }.map { it.second }),
            contadoTotal = Inventario.sumar(filas.map { it.second })
        )
    }

    /** Borra un conteo propio (solo con el inventario abierto). @return true si se borró */
    suspend fun borrar(id: Int, conteoId: Long, usuario: String): Boolean = withContext(Dispatchers.IO) {
        val db = base.writableDatabase
        estaAbierto(db, id) &&
            db.delete("conteos", "id = ? AND inventario_id = ? AND usuario = ?",
                arrayOf(conteoId.toString(), id.toString(), usuario)) > 0
    }

    /** Los últimos conteos de ESE usuario en el inventario. */
    suspend fun ultimos(id: Int, usuario: String, cuantos: Int = 50): List<ConteoDto> = withContext(Dispatchers.IO) {
        val db = base.readableDatabase
        val abierto = estaAbierto(db, id)
        db.rawQuery(
            """SELECT c.id, c.co_art, IFNULL(a.art_des, ''), c.codigo_leido, c.cantidad, c.usuario, c.fecha
               FROM conteos c JOIN inventarios i ON i.id = c.inventario_id
               LEFT JOIN articulos a ON a.co_empresa = i.co_empresa AND a.co_art = c.co_art
               WHERE c.inventario_id = ? AND c.usuario = ? ORDER BY c.id DESC LIMIT $cuantos""",
            arrayOf(id.toString(), usuario)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(ConteoDto(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), puedeBorrar = abierto))
            }
        }
    }

    /** Cuántos conteos lleva ese usuario en el inventario. */
    suspend fun cuantosDe(id: Int, usuario: String): Int = withContext(Dispatchers.IO) {
        base.readableDatabase.rawQuery("SELECT COUNT(*) FROM conteos WHERE inventario_id = ? AND usuario = ?",
            arrayOf(id.toString(), usuario)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** Stock contado de un artículo (null = no se ha contado) para mostrar al escanear. */
    suspend fun contado(id: Int, coArt: String): String? = withContext(Dispatchers.IO) {
        contados(base, id, listOf(coArt))[coArt]
    }

    /**
     * Lo que se envía a Profit (TODOS los conteos de este teléfono en el inventario) y la
     * clave del envío (la misma en cada reintento, para que Profit no lo reciba dos veces).
     */
    suspend fun paraEnviar(id: Int): Pair<List<RenglonEnvio>, String> = withContext(Dispatchers.IO) {
        val db = base.writableDatabase
        val clave = db.rawQuery("SELECT clave_envio FROM inventarios WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: UUID.randomUUID().toString().also { nueva ->
                db.update("inventarios", ContentValues().apply { put("clave_envio", nueva) }, "id = ?", arrayOf(id.toString()))
            }
        val renglones = db.rawQuery(
            "SELECT co_art, codigo_leido, cantidad, usuario, fecha FROM conteos WHERE inventario_id = ? ORDER BY id",
            arrayOf(id.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(RenglonEnvio(c.getString(0), c.getString(1), BigDecimal(c.getString(2)),
                    c.getString(3), LocalDateTime.parse(c.getString(4).replace(' ', 'T'))))
            }
        }
        renglones to clave
    }

    /** Ya se envió a Profit: queda cerrado en este teléfono. */
    suspend fun marcarEnviado(id: Int, usuario: String) = withContext(Dispatchers.IO) {
        base.writableDatabase.update("inventarios", ContentValues().apply {
            put("abierto", 0)
            put("enviado_por", usuario)
            put("fecha_envio", ahora())
        }, "id = ?", arrayOf(id.toString()))
    }

    /** Borra del teléfono un inventario ya enviado (y sus conteos) para no acumular. */
    suspend fun borrarEnviado(id: Int) = withContext(Dispatchers.IO) {
        val db = base.writableDatabase
        db.beginTransaction()
        try {
            if (!estaAbierto(db, id)) {
                db.delete("conteos", "inventario_id = ?", arrayOf(id.toString()))
                db.delete("inventarios", "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun estaAbierto(db: SQLiteDatabase, id: Int): Boolean =
        db.rawQuery("SELECT abierto FROM inventarios WHERE id = ?", arrayOf(id.toString()))
            .use { c -> c.moveToFirst() && c.getInt(0) == 1 }

    private fun cantidades(db: SQLiteDatabase, id: Int, coArt: String): List<Pair<String, String>> =
        db.rawQuery("SELECT usuario, cantidad FROM conteos WHERE inventario_id = ? AND co_art = ?",
            arrayOf(id.toString(), coArt)).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }

    internal companion object {
        /** "2026-10-19 14:30:05" (hora del teléfono). */
        fun ahora(): String = LocalDateTime.now().withNano(0).toString().replace('T', ' ')

        /** {co_art: stock contado} de esos artículos (solo los que tienen conteos). */
        fun contados(base: BaseLocal, id: Int, codigos: List<String>): Map<String, String> {
            if (codigos.isEmpty()) return emptyMap()
            val marcas = codigos.joinToString(",") { "?" }
            return base.readableDatabase.rawQuery(
                "SELECT co_art, cantidad FROM conteos WHERE inventario_id = ? AND co_art IN ($marcas)",
                (listOf(id.toString()) + codigos).toTypedArray()
            ).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> Inventario.sumar(v) }
        }
    }
}
