// MÓDULO :core:local
package com.siguec.puraceramica.core.local

// SQLite de Android
import android.content.ContentValues
import android.database.Cursor
// Modelos y lógica compartida (:core:red)
import com.siguec.puraceramica.core.red.ArticuloDto
import com.siguec.puraceramica.core.red.CopiaArticulos
import com.siguec.puraceramica.core.red.Inventario
import com.siguec.puraceramica.core.red.SincronizacionDto
// Hilo aparte para la base (nunca en el de la pantalla)
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Los artículos en el TELÉFONO (copia de saArticulo): se consultan y se
 * escanean sin WiFi.
 *
 *   guardarCopia()   Reemplaza la copia de la empresa con lo sincronizado.
 *   buscar()         Código, descripción y stock contado (del inventario elegido).
 *   porCodigo()      Lo escaneado -> artículo (co_art, modelo, ref o código de barra).
 */
class ArticulosLocales internal constructor(private val base: BaseLocal) {

    /** Reemplaza TODA la copia de la empresa (en una transacción: o queda la nueva o la vieja). */
    suspend fun guardarCopia(coEmpresa: String, copia: CopiaArticulos, usuario: String, fecha: String) =
        withContext(Dispatchers.IO) {
            val db = base.writableDatabase
            db.beginTransaction()
            try {
                db.delete("articulos", "co_empresa = ?", arrayOf(coEmpresa))
                db.delete("codigos", "co_empresa = ?", arrayOf(coEmpresa))
                val articulo = db.compileStatement("INSERT INTO articulos (co_empresa, co_art, art_des, anulado) VALUES (?, ?, ?, ?)")
                copia.articulos.forEach { a ->
                    articulo.clearBindings()
                    articulo.bindString(1, coEmpresa)
                    articulo.bindString(2, a.coArt)
                    articulo.bindString(3, a.artDes)
                    articulo.bindLong(4, if (a.anulado) 1 else 0)
                    articulo.executeInsert()
                }
                // OR IGNORE: el mismo código en mayúsculas/minúsculas para el mismo artículo
                val codigo = db.compileStatement(
                    "INSERT OR IGNORE INTO codigos (co_empresa, codigo, co_art, origen, prioridad) VALUES (?, ?, ?, ?, ?)")
                copia.codigos.forEach { c ->
                    codigo.clearBindings()
                    codigo.bindString(1, coEmpresa)
                    codigo.bindString(2, c.codigo)
                    codigo.bindString(3, c.coArt)
                    codigo.bindString(4, c.origen)
                    codigo.bindLong(5, c.prioridad.toLong())
                    codigo.executeInsert()
                }
                db.insertWithOnConflict("sincronizaciones", null, ContentValues().apply {
                    put("co_empresa", coEmpresa)
                    put("fecha", fecha)
                    put("usuario", usuario)
                    put("total", copia.articulos.count { !it.anulado })
                    put("codigos", copia.codigos.size)
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    /** Última sincronización en este teléfono (null = nunca). */
    suspend fun ultimaSincronizacion(coEmpresa: String): SincronizacionDto? = withContext(Dispatchers.IO) {
        base.readableDatabase.rawQuery(
            "SELECT fecha, usuario, total, codigos FROM sincronizaciones WHERE co_empresa = ?", arrayOf(coEmpresa)
        ).use { c ->
            if (!c.moveToFirst()) null else SincronizacionDto(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3))
        }
    }

    /**
     * Consulta: SOLO código, descripción y stock contado.
     * @param texto código, descripción o código de barra ("*" = comodín; vacío = todos)
     * @param inventarioId de qué inventario sale el contado (null = no se muestra)
     * @param soloContados true = solo los que ya tienen conteo en ese inventario
     * @return (artículos de la página, hay más)
     */
    suspend fun buscar(
        coEmpresa: String,
        texto: String,
        inventarioId: Int?,
        soloContados: Boolean,
        pagina: Int,
        tamano: Int = TAMANO_PAGINA
    ): Pair<List<ArticuloDto>, Boolean> = withContext(Dispatchers.IO) {
        val patron = Inventario.patronLike(texto)
        val condiciones = mutableListOf("a.co_empresa = ?", "a.anulado = 0")
        val parametros = mutableListOf(coEmpresa)
        if (patron.isNotEmpty()) {
            condiciones.add("""(a.co_art LIKE ? ESCAPE '\' OR a.art_des LIKE ? ESCAPE '\' OR EXISTS (SELECT 1 FROM codigos k
                WHERE k.co_empresa = a.co_empresa AND k.co_art = a.co_art AND k.codigo LIKE ? ESCAPE '\'))""")
            repeat(3) { parametros.add(patron) }
        }
        if (soloContados && inventarioId != null) {
            condiciones.add("EXISTS (SELECT 1 FROM conteos n WHERE n.inventario_id = ? AND n.co_art = a.co_art)")
            parametros.add(inventarioId.toString())
        }
        val sql = "SELECT a.co_art, a.art_des FROM articulos a WHERE ${condiciones.joinToString(" AND ")} " +
            "ORDER BY a.co_art LIMIT ${tamano + 1} OFFSET ${(maxOf(1, pagina) - 1) * tamano}"
        val filas = base.readableDatabase.rawQuery(sql, parametros.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
        val pag = filas.take(tamano)
        // El contado se suma en Kotlin (BigDecimal) solo para los de esta página
        val contados = if (inventarioId == null) emptyMap() else InventariosLocales.contados(base, inventarioId, pag.map { it.first })
        pag.map { (coArt, artDes) -> ArticuloDto(coArt, artDes, contados[coArt]) } to (filas.size > tamano)
    }

    /**
     * Lo escaneado -> artículo. Se busca en co_art, modelo y ref (y la tabla de barras si la hay);
     * si el código está en varios, gana co_art, luego modelo, luego ref. Sin distinguir
     * mayúsculas de minúsculas (Code 39 solo imprime mayúsculas).
     */
    suspend fun porCodigo(coEmpresa: String, codigo: String): ArticuloDto? = withContext(Dispatchers.IO) {
        base.readableDatabase.rawQuery(
            """SELECT a.co_art, a.art_des FROM codigos k JOIN articulos a ON a.co_empresa = k.co_empresa AND a.co_art = k.co_art
               WHERE k.co_empresa = ? AND k.codigo = ? ORDER BY a.anulado, k.prioridad, a.co_art LIMIT 1""",
            arrayOf(coEmpresa, Inventario.limpiarCodigo(codigo))
        ).use(::articulo)
    }

    private fun articulo(c: Cursor): ArticuloDto? = if (c.moveToFirst()) ArticuloDto(c.getString(0), c.getString(1)) else null

    companion object {
        const val TAMANO_PAGINA = 30
    }
}
