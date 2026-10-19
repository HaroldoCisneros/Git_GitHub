// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// =====================================================================
// Toma física (inventario físico) de Profit Plus 2K12.
//
//   - Los inventarios se ABREN EN PROFIT. La App trae los que están abiertos
//     para contar en el teléfono (sin WiFi).
//   - Al cerrar, la App ENVÍA lo contado a las tablas de toma física de Profit
//     y Profit se encarga de cerrarlo (comparar con el stock y ajustar).
//
// Las tablas de toma física de Profit están POR CONFIRMAR: todo el SQL que
// las toca va SOLO en una clase que implemente [TomaFisicaProfit]. Cuando se
// tengan las tablas se escribe esa clase y se cambia en [TomaFisicaProfit.actual].
// =====================================================================

/** Lo que la App necesita de la toma física de Profit. Se usa DENTRO de una conexión a la empresa. */
interface TomaFisicaProfit {

    /** Las tomas físicas ABIERTAS en Profit (para traer al teléfono). */
    fun abiertas(cx: Conexion): List<TomaProfitDto>

    /** ¿Sigue abierta en Profit? (antes de enviar). */
    fun estaAbierta(cx: Conexion, numero: String): Boolean

    /**
     * Graba lo contado en la toma física de Profit.
     * @param totales stock contado por artículo (suma de los conteos que se envían)
     * @param usuarioProfit usuario de Profit de quien envía
     */
    fun enviar(cx: Conexion, numero: String, totales: Map<String, java.math.BigDecimal>, usuarioProfit: String)

    companion object {
        /** La implementación en uso. */
        val actual: TomaFisicaProfit = TomaFisicaPendiente
    }
}

/** Mientras no se confirmen las tablas de toma física de Profit 2K12: avisa en vez de adivinar. */
object TomaFisicaPendiente : TomaFisicaProfit {
    private const val MENSAJE = "Falta configurar en la App las tablas de toma física de Profit 2K12."

    override fun abiertas(cx: Conexion): List<TomaProfitDto> = throw ErrorApp(MENSAJE, TipoError.SERVIDOR)
    override fun estaAbierta(cx: Conexion, numero: String): Boolean = throw ErrorApp(MENSAJE, TipoError.SERVIDOR)
    override fun enviar(cx: Conexion, numero: String, totales: Map<String, java.math.BigDecimal>, usuarioProfit: String): Unit =
        throw ErrorApp(MENSAJE, TipoError.SERVIDOR)
}
