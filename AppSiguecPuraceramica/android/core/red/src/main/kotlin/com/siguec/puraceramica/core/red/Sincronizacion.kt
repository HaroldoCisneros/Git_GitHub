// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// =====================================================================
// Sincronizar artículos: lee saArticulo de Profit 2K12 (SOLO LECTURA) y lo
// prepara para guardarlo en el TELÉFONO (core:local), donde se cuenta sin WiFi.
// =====================================================================

private const val LARGO_CODIGO_ARTICULO = 30
private const val LARGO_DESCRIPCION = 150
private const val LARGO_CODIGO_ESCANEO = 60
private const val MENSAJE_SIN_PERMISO = "Su ambiente no permite sincronizar los artículos con Profit."

/**
 * SELECT de todos los artículos con las columnas de código que existan (nombres validados).
 * @param conTipo la tabla tiene la columna de tipo: se excluyen los [ConfigProfit.tiposExcluidos] (servicios)
 */
internal fun sqlArticulos(cfg: ConfigProfit, conAnulado: Boolean, extras: List<String>, conTipo: Boolean = false): String {
    val anulado = if (conAnulado) "[${cfg.colAnulado}] AS anulado" else "0 AS anulado"
    val columnas = extras.joinToString("") { ", RTRIM(CAST([$it] AS varchar(60))) AS [x_${it.lowercase()}]" }
    val filtro = if (conTipo && cfg.tiposExcluidos.isNotEmpty()) {
        " WHERE [${cfg.colTipo}] NOT IN (${cfg.tiposExcluidos.joinToString(", ") { "'$it'" }})"
    } else {
        ""
    }
    return "SELECT RTRIM([${cfg.colCodigo}]) AS co_art, RTRIM([${cfg.colDescripcion}]) AS art_des, " +
        "$anulado$columnas FROM dbo.[${cfg.tablaArticulos}]$filtro"
}

internal fun sqlCodigosBarra(cfg: ConfigProfit): String =
    "SELECT RTRIM(CAST([${cfg.colCodigoBarra}] AS varchar(60))) AS codigo, " +
        "RTRIM([${cfg.colCodigoBarraArticulo}]) AS co_art FROM dbo.[${cfg.tablaCodigosBarra}]"

/**
 * Lo leído de Profit -> artículos y códigos limpios (no toca la base: se prueba solo).
 *  - Se saltan los artículos sin código, los de código muy largo y los repetidos.
 *  - Códigos: co_art (prioridad 0), cada columna extra (1, 2...) y la tabla de barras
 *    (la última). Mismo código dos veces en el mismo artículo: queda la prioridad más baja.
 */
fun prepararCopia(filas: List<Map<String, Any?>>, extras: List<String>, barras: List<Map<String, Any?>> = emptyList()): CopiaArticulos {
    val articulos = LinkedHashMap<String, ArticuloCopia>()
    val codigos = LinkedHashMap<Pair<String, String>, CodigoCopia>()

    fun agregar(codigo: String, coArt: String, origen: String, prioridad: Int) {
        val limpio = codigo.trim()
        if (limpio.isEmpty() || limpio.length > LARGO_CODIGO_ESCANEO) return
        val clave = limpio.uppercase() to coArt
        val actual = codigos[clave]
        if (actual == null || prioridad < actual.prioridad) codigos[clave] = CodigoCopia(limpio, coArt, origen, prioridad)
    }

    for (f in filas) {
        val coArt = f.texto("co_art")
        if (coArt.isEmpty() || coArt.length > LARGO_CODIGO_ARTICULO || coArt in articulos) continue
        val artDes = f.texto("art_des").take(LARGO_DESCRIPCION).ifEmpty { coArt }
        articulos[coArt] = ArticuloCopia(coArt, artDes, f.logico("anulado"))
        agregar(coArt, coArt, "co_art", 0)
        extras.forEachIndexed { i, columna -> agregar(f.texto("x_" + columna.lowercase()), coArt, columna.lowercase(), i + 1) }
    }
    val prioridadBarras = extras.size + 1
    for (f in barras) {
        val coArt = f.texto("co_art")
        if (coArt in articulos) agregar(f.texto("codigo"), coArt, "barra", prioridadBarras)
    }
    return CopiaArticulos(articulos.values.toList(), codigos.values.toList())
}

/**
 * Lectura de los artículos de Profit 2K12 para el teléfono.
 * @param acceso para revisar el permiso y leer los nombres de Profit (tabla parametros)
 */
class RepositorioProfit(private val bd: BaseDatos, private val acceso: RepositorioAcceso) {

    /**
     * Lee todos los artículos de la empresa (y sus códigos para escanear).
     * Anota la sincronización en la bitácora de la App.
     */
    suspend fun leerArticulos(usuario: UsuarioDto, coEmpresa: String): Resultado<CopiaArticulos> = bd.usar(coEmpresa) { cx ->
        acceso.exigirPermiso(cx, usuario, coEmpresa, { it.sincronizarArticulos }, MENSAJE_SIN_PERMISO)
        val cfg = acceso.configProfit(cx)
        val columnas = columnas(cx, cfg.tablaArticulos)
        if (columnas.isEmpty()) {
            throw ErrorApp("No se encontró la tabla ${cfg.tablaArticulos} en la base $coEmpresa.", TipoError.SERVIDOR)
        }
        listOf(cfg.colCodigo, cfg.colDescripcion).firstOrNull { it.lowercase() !in columnas }?.let {
            throw ErrorApp("La tabla ${cfg.tablaArticulos} no tiene la columna $it.", TipoError.SERVIDOR)
        }
        val conAnulado = cfg.colAnulado.isNotEmpty() && cfg.colAnulado.lowercase() in columnas
        // Las columnas de código que no existan en esta base se ignoran
        val extras = cfg.columnasCodigo.filter { it.lowercase() in columnas }
        val conTipo = cfg.colTipo.isNotEmpty() && cfg.colTipo.lowercase() in columnas
        val filas = cx.consultar(sqlArticulos(cfg, conAnulado, extras, conTipo))
        val barras = if (cfg.tablaCodigosBarra.isNotEmpty() && columnas(cx, cfg.tablaCodigosBarra).isNotEmpty()) {
            cx.consultar(sqlCodigosBarra(cfg))
        } else {
            emptyList()
        }
        val copia = prepararCopia(filas, extras, barras)
        if (copia.articulos.isEmpty()) throw ErrorApp("No se leyó ningún artículo de ${cfg.tablaArticulos}.")
        acceso.anotarDesde(cx, Acciones.SINCRONIZAR, usuario, coEmpresa,
            "${copia.articulos.size} artículos, ${copia.codigos.size} códigos")
        copia
    }

    private fun columnas(cx: Conexion, tabla: String): Set<String> =
        cx.consultar("SELECT LOWER(COLUMN_NAME) AS columna FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?", tabla)
            .map { it.texto("columna") }.toSet()
}
