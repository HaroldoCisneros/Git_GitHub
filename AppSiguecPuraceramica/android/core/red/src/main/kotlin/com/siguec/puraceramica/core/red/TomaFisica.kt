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
//   - Al cerrar, la App ENVÍA lo contado a Profit y Profit se encarga de cerrarlo.
//
// Todo el SQL de la toma física va en ESTE archivo.
// =====================================================================

/** Lo que la App necesita de la toma física de Profit. Se usa DENTRO de una conexión a la empresa. */
interface TomaFisicaProfit {

    /** Los inventarios físicos sin procesar en Profit (para abrir en el teléfono). */
    fun abiertas(cx: Conexion): List<TomaProfitDto>

    /** ¿Sigue sin procesar en Profit? (antes de abrir y antes de enviar). */
    fun estaAbierta(cx: Conexion, numero: String): Boolean

    /**
     * Graba lo contado en la toma física de Profit.
     * @param totales stock contado por artículo (suma de los conteos que se envían)
     * @param usuarioProfit usuario de Profit de quien envía
     */
    fun enviar(cx: Conexion, numero: String, totales: Map<String, BigDecimal>, usuarioProfit: String)

    companion object {
        /** La implementación en uso. */
        val actual: TomaFisicaProfit = TomaFisica2K12
    }
}

/**
 * Profit Plus 2K12:
 *   saInventarioFisico  co_invfisico char(20) PK, des_invfisico varchar(60), co_alma char(6) NULL,
 *                       inicio smalldatetime, art_cero bit (los no contados quedan en 0),
 *                       procesado bit (0 = iniciado, 1 = cerrado).
 *   saAlmacen           co_alma char(6), des_alma varchar(60).
 */
object TomaFisica2K12 : TomaFisicaProfit {

    private const val SQL_ABIERTAS = """SELECT RTRIM(f.co_invfisico) AS co_invfisico, RTRIM(ISNULL(f.des_invfisico, '')) AS des_invfisico,
        RTRIM(ISNULL(f.co_alma, '')) AS co_alma, RTRIM(ISNULL(a.des_alma, '')) AS des_alma,
        CONVERT(varchar(16), f.inicio, 120) AS inicio, f.art_cero
        FROM dbo.saInventarioFisico f LEFT JOIN dbo.saAlmacen a ON a.co_alma = f.co_alma
        WHERE f.procesado = 0 ORDER BY f.inicio DESC"""

    // co_invfisico es char(20): SQL Server compara sin los espacios de relleno
    private const val SQL_ABIERTA = "SELECT procesado FROM dbo.saInventarioFisico WHERE co_invfisico = ?"

    override fun abiertas(cx: Conexion): List<TomaProfitDto> = cx.consultar(SQL_ABIERTAS).map {
        TomaProfitDto(
            numero = it.texto("co_invfisico"),
            coAlma = it.texto("co_alma"),
            desAlma = it.texto("des_alma"),
            descripcion = it.texto("des_invfisico"),
            fecha = it.texto("inicio").ifEmpty { null },
            artCero = it.logico("art_cero")
        )
    }

    override fun estaAbierta(cx: Conexion, numero: String): Boolean =
        cx.consultarUno(SQL_ABIERTA, numero)?.let { !it.logico("procesado") } ?: false

    /** PENDIENTE: falta la tabla de renglones de la toma física (dónde va lo contado por artículo). */
    override fun enviar(cx: Conexion, numero: String, totales: Map<String, BigDecimal>, usuarioProfit: String): Unit =
        throw ErrorApp("Falta configurar en la App la tabla de renglones de la toma física de Profit 2K12.",
            TipoError.SERVIDOR)
}
