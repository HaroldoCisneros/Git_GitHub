// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Cantidades exactas
import java.math.BigDecimal

// =====================================================================
// Toma física (inventario físico) de Profit Plus 2K12.
//
//   - Los inventarios se CREAN EN PROFIT (saInventarioFisico). La App consulta los
//     que no están procesados (procesado = 0), el usuario elige uno y lo ABRE en
//     el teléfono para contar sin WiFi.
//   - Al cerrar, la App ENVÍA lo contado como un RESULTADO DE INVENTARIO
//     (saResInventario + saResInventarioReng) y Profit se encarga de procesarlo.
//
// Todo el SQL de la toma física va en ESTE archivo.
// =====================================================================

/**
 * Lo que se graba en Profit al enviar.
 * @property numResinv número del resultado (saResInventario.num_resinv)
 * @property coUsuario / coSucursal los del REGISTRO del inventario (saInventarioFisico.co_us_in /
 *           co_sucu_in), guardados al abrirlo: así queda igual que si se cargara en Profit
 * @property totales stock contado por artículo (suma de los conteos que se envían)
 */
data class EnvioProfit(
    val numero: String,
    val coAlma: String,
    val numResinv: String,
    val descripcion: String,
    val coUsuario: String,
    val coSucursal: String,
    val totales: Map<String, BigDecimal>,
    val maquina: String = ""    // Teléfono (queda en la pista de Profit, @sMaquina)
)

/** Lo que la App necesita de la toma física de Profit. Se usa DENTRO de una conexión a la empresa. */
interface TomaFisicaProfit {

    /** Los inventarios físicos sin procesar en Profit (para abrir en el teléfono). */
    fun abiertas(cx: Conexion): List<TomaProfitDto>

    /** ¿Sigue sin procesar en Profit? (antes de abrir y antes de enviar). */
    fun estaAbierta(cx: Conexion, numero: String): Boolean

    /** Próximo número de resultado de inventario (correlativo de Profit). */
    fun proximoNumero(cx: Conexion, cfg: ConfigProfit): String

    /** Graba lo contado en Profit (un resultado de inventario). */
    fun enviar(cx: Conexion, cfg: ConfigProfit, envio: EnvioProfit)

    companion object {
        /** La implementación en uso. */
        val actual: TomaFisicaProfit = TomaFisica2K12
    }
}

/**
 * Profit Plus 2K12:
 *   saInventarioFisico   co_invfisico char(20) PK, des_invfisico, co_alma char(6) NULL, inicio,
 *                        art_cero bit, procesado bit (0 iniciado, 1 cerrado), co_us_in, co_sucu_in.
 *   saAlmacen            co_alma, des_alma.
 *   saResInventario      Resultado (encabezado): num_resinv char(20) PK, des_resinv, fecha, co_invfisico,
 *                        co_alma NOT NULL, co_mone, tasa > 0, co_us_in/mo, co_sucu_in/mo, fe_us_in/mo.
 *   saResInventarioReng  Renglones: num_resinv, reng_num > 0, co_art (único por resultado), co_uni
 *                        (FK saArtUnidad), sco_uni, total_art_teo, total_art, stotal_art_teo, stotal_art,
 *                        cost_unit (= dbo.ConsultarUltimoCosto(saArticulo.rowguid)), co_us_in/mo,
 *                        co_sucu_in/mo, fe_us_in/mo.
 *                        (validador = timestamp y rowguid = newid() los pone SQL Server)
 *   pInsertarResInventario           Procedimiento de Softech: encabezado del resultado + su pista.
 *   pInsertarRenglonesResInventario  Procedimiento de Softech: cada renglón + su pista.
 *   psObtenerProximoNumero           Procedimiento de la empresa: correlativo de num_resinv.
 */
object TomaFisica2K12 : TomaFisicaProfit {

    private const val SQL_ABIERTAS = """SELECT RTRIM(f.co_invfisico) AS co_invfisico, RTRIM(ISNULL(f.des_invfisico, '')) AS des_invfisico,
        RTRIM(ISNULL(f.co_alma, '')) AS co_alma, RTRIM(ISNULL(a.des_alma, '')) AS des_alma,
        CONVERT(varchar(16), f.inicio, 120) AS inicio, f.art_cero,
        RTRIM(f.co_us_in) AS co_us_in, RTRIM(ISNULL(f.co_sucu_in, '')) AS co_sucu_in
        FROM dbo.saInventarioFisico f LEFT JOIN dbo.saAlmacen a ON a.co_alma = f.co_alma
        WHERE f.procesado = 0 ORDER BY f.inicio DESC"""

    // co_invfisico es char(20): SQL Server compara sin los espacios de relleno
    private const val SQL_ABIERTA = "SELECT procesado FROM dbo.saInventarioFisico WHERE co_invfisico = ?"

    private const val SQL_ULTIMA_MONEDA =
        "SELECT TOP 1 RTRIM(co_mone) AS co_mone FROM dbo.saResInventario ORDER BY fe_us_in DESC"

    // Encabezado: el procedimiento de Profit (graba el resultado y su pista de auditoría).
    // @sdFecha no admite GETDATE() directo en el EXEC: va en una variable (hora del servidor).
    private const val SQL_ENCABEZADO = """SET NOCOUNT ON; DECLARE @fecha smalldatetime = GETDATE();
        EXEC dbo.pInsertarResInventario @sNum_ResInv = ?, @sDes_ResInv = ?, @sdFecha = @fecha,
        @sCo_InvFisico = ?, @sCo_Alma = ?, @sCo_Mone = ?, @deTasa = ?, @sCo_Us_In = ?, @sCo_Sucu_In = ?,
        @sMaquina = ?, @sRevisado = NULL, @sTrasnfe = NULL"""

    // Renglones: el procedimiento de Profit (graba el renglón y su pista)
    private const val SQL_RENGLON = """EXEC dbo.pInsertarRenglonesResInventario @sNum_ResInv = ?, @iReng_Num = ?,
        @sCo_Art = ?, @sCo_Uni = ?, @sSco_Uni = NULL, @deTotal_Art_Teo = ?, @deTotal_Art = ?, @deStotal_Art = 0,
        @deSTotal_Art_Teo = 0, @deCost_Unit = ?, @sCo_Us_In = ?, @sCo_Sucu_In = ?, @sMaquina = ?"""

    /** Cuántos artículos por consulta "IN (?, ?, ...)" (SQL Server admite 2100 parámetros). */
    private const val LOTE = 500

    override fun abiertas(cx: Conexion): List<TomaProfitDto> = cx.consultar(SQL_ABIERTAS).map {
        TomaProfitDto(
            numero = it.texto("co_invfisico"),
            coAlma = it.texto("co_alma"),
            desAlma = it.texto("des_alma"),
            descripcion = it.texto("des_invfisico"),
            fecha = it.texto("inicio").ifEmpty { null },
            artCero = it.logico("art_cero"),
            coUsProfit = it.texto("co_us_in"),
            coSucuProfit = it.texto("co_sucu_in")
        )
    }

    override fun estaAbierta(cx: Conexion, numero: String): Boolean =
        cx.consultarUno(SQL_ABIERTA, numero)?.let { !it.logico("procesado") } ?: false

    override fun enviar(cx: Conexion, cfg: ConfigProfit, envio: EnvioProfit) {
        if (envio.coAlma.isBlank()) {
            throw ErrorApp("El inventario ${envio.numero} no tiene almacén en Profit: el resultado de inventario " +
                "lo exige (saResInventario.co_alma).", TipoError.VALIDACION)
        }
        // Usuario y sucursal del registro del inventario (guardados al abrirlo; si el teléfono
        // lo abrió con una versión anterior de la App, se leen ahora de saInventarioFisico)
        val registro = if (envio.coUsuario.isNotBlank()) envio.coUsuario to envio.coSucursal else
            cx.consultarUno("SELECT RTRIM(co_us_in) AS co_us_in, RTRIM(ISNULL(co_sucu_in, '')) AS co_sucu_in " +
                "FROM dbo.saInventarioFisico WHERE co_invfisico = ?", envio.numero)
                ?.let { it.texto("co_us_in") to it.texto("co_sucu_in") } ?: ("" to "")
        if (registro.first.isBlank()) {
            throw ErrorApp("No se encontró el usuario de Profit del inventario ${envio.numero}.", TipoError.VALIDACION)
        }
        val codigos = envio.totales.keys.sorted()
        val unidades = unidadPrincipal(cx, codigos)
        codigos.filter { it !in unidades }.takeIf { it.isNotEmpty() }?.let { sinUnidad ->
            throw ErrorApp("Estos artículos no tienen unidad en Profit (saArtUnidad): " +
                sinUnidad.take(10).joinToString() + if (sinUnidad.size > 10) "..." else "", TipoError.VALIDACION)
        }
        val teorico = stockTeorico(cx, cfg, envio.coAlma, codigos)
        val costos = ultimoCosto(cx, cfg, codigos)
        val moneda = cfg.coMone.ifEmpty { cx.consultarUno(SQL_ULTIMA_MONEDA)?.texto("co_mone").orEmpty() }
        if (moneda.isEmpty()) {
            throw ErrorApp("No se sabe la moneda del resultado: agregue el parámetro profit.co_mone en " +
                "SIGUEC_PURACERAMICA.dbo.parametros.", TipoError.VALIDACION)
        }
        val us = registro.first.take(6)
        val sucu = registro.second.take(6).ifEmpty { null }

        val maquina = envio.maquina.take(60)

        // Encabezado (con su pista) con el procedimiento de Profit
        cx.ejecutarProcedimiento(SQL_ENCABEZADO, envio.numResinv, envio.descripcion.take(60), envio.numero,
            envio.coAlma, moneda, BigDecimal(cfg.tasa), us, sucu, maquina)

        // Renglones: uno por artículo (UK num_resinv + co_art), numerados desde 1, con el procedimiento de Profit
        codigos.forEachIndexed { i, coArt ->
            cx.ejecutarProcedimiento(SQL_RENGLON, envio.numResinv, i + 1, coArt, unidades.getValue(coArt),
                teorico[coArt] ?: BigDecimal.ZERO, envio.totales.getValue(coArt), costos[coArt] ?: BigDecimal.ZERO,
                us, sucu, maquina)
        }
    }

    /**
     * La unidad principal de cada artículo (saArtUnidad). Si la tabla tiene una columna que marca
     * la principal (uni_principal) se usa; si no, la de equivalencia 1; si no, la primera.
     */
    private fun unidadPrincipal(cx: Conexion, codigos: List<String>): Map<String, String> {
        val columnas = columnas(cx, "saArtUnidad")
        val orden = listOfNotNull(
            "CASE WHEN [uni_principal] = 1 THEN 0 ELSE 1 END".takeIf { "uni_principal" in columnas },
            "CASE WHEN [equivalencia] = 1 THEN 0 ELSE 1 END".takeIf { "equivalencia" in columnas },
            "co_uni"
        ).joinToString(", ")
        val unidades = mutableMapOf<String, String>()
        codigos.chunked(LOTE).forEach { lote ->
            cx.consultar("SELECT RTRIM(co_art) AS co_art, RTRIM(co_uni) AS co_uni FROM dbo.saArtUnidad " +
                "WHERE co_art IN (${lote.joinToString(",") { "?" }}) ORDER BY co_art, $orden", *lote.toTypedArray())
                .forEach { f -> unidades.putIfAbsent(f.texto("co_art"), f.texto("co_uni")) }
        }
        return unidades
    }

    /**
     * Costo unitario de cada artículo (cost_unit) con la función de la empresa
     * dbo.ConsultarUltimoCosto(saArticulo.rowguid): el último costo de entrada sin REPO ni PROV
     * (0 si nunca tuvo entrada).
     */
    private fun ultimoCosto(cx: Conexion, cfg: ConfigProfit, codigos: List<String>): Map<String, BigDecimal> {
        val funcion = cfg.funcionCosto
        if (cx.consultarUno("SELECT OBJECT_ID(?) AS id", "dbo.$funcion")?.get("id") == null) {
            throw ErrorApp("No existe la función dbo.$funcion en la base de Profit (costo unitario del resultado).",
                TipoError.SERVIDOR)
        }
        val costos = mutableMapOf<String, BigDecimal>()
        codigos.chunked(LOTE).forEach { lote ->
            cx.consultar("SELECT RTRIM(a.co_art) AS co_art, dbo.[$funcion](a.rowguid) AS costo FROM dbo.saArticulo a " +
                "WHERE a.co_art IN (${lote.joinToString(",") { "?" }})", *lote.toTypedArray())
                .forEach { f -> costos[f.texto("co_art")] = f.decimal("costo") }
        }
        return costos
    }

    /** Existencia actual de cada artículo en el almacén (total_art_teo). Si no se puede leer: 0. */
    private fun stockTeorico(cx: Conexion, cfg: ConfigProfit, coAlma: String, codigos: List<String>): Map<String, BigDecimal> {
        val columnas = columnas(cx, cfg.tablaStock)
        val necesarias = listOf("co_alma", "co_art", cfg.colStockCantidad.lowercase()) +
            listOfNotNull(cfg.colStockTipo.lowercase().ifEmpty { null })
        if (!columnas.containsAll(necesarias)) return emptyMap()
        val tipo = if (cfg.colStockTipo.isNotEmpty()) " AND [${cfg.colStockTipo}] = ?" else ""
        val stock = mutableMapOf<String, BigDecimal>()
        codigos.chunked(LOTE).forEach { lote ->
            val parametros = listOf(coAlma) + listOfNotNull(cfg.tipoStockActual.takeIf { tipo.isNotEmpty() }) + lote
            cx.consultar("SELECT RTRIM(co_art) AS co_art, SUM([${cfg.colStockCantidad}]) AS stock FROM dbo.[${cfg.tablaStock}] " +
                "WHERE co_alma = ?$tipo AND co_art IN (${lote.joinToString(",") { "?" }}) GROUP BY co_art",
                *parametros.toTypedArray())
                .forEach { f -> stock[f.texto("co_art")] = f.decimal("stock") }
        }
        return stock
    }

    private fun columnas(cx: Conexion, tabla: String): Set<String> =
        cx.consultar("SELECT LOWER(COLUMN_NAME) AS columna FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?", tabla)
            .map { it.texto("columna") }.toSet()

    /**
     * Correlativo con el procedimiento de la empresa dbo.psObtenerProximoNumero: último
     * saResInventario.num_resinv (con ese prefijo y solo dígitos después) + 1, respetando los ceros
     * a la izquierda. Lee con UPDLOCK + HOLDLOCK: dentro de la transacción del envío, dos teléfonos
     * que envían a la vez no pueden recibir el mismo número.
     */
    override fun proximoNumero(cx: Conexion, cfg: ConfigProfit): String {
        val procedimiento = cfg.procedimientoNumero
        if (cx.consultarUno("SELECT OBJECT_ID(?) AS id", "dbo.$procedimiento")?.get("id") == null) {
            throw ErrorApp("No existe el procedimiento dbo.$procedimiento en la base de Profit (correlativo del " +
                "resultado de inventario). Ejecute sql/06_procedimiento_proximo_numero.sql.", TipoError.SERVIDOR)
        }
        val numero = cx.consultarUno(
            "SET NOCOUNT ON; DECLARE @p varchar(50); EXEC dbo.[$procedimiento] @sTabla = 'saResInventario', " +
                "@sCampo = 'num_resinv', @sPrefijo = ?, @sProximo = @p OUTPUT",
            cfg.prefijoResultado
        )?.texto("proximonumero").orEmpty()
        if (numero.isEmpty() || numero.length > LARGO_NUM_RESINV) {
            throw ErrorApp("El correlativo del resultado de inventario no es válido: '$numero'.", TipoError.SERVIDOR)
        }
        return numero
    }

    /** saResInventario.num_resinv es char(20). */
    private const val LARGO_NUM_RESINV = 20
}
