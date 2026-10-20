// MÓDULO :core:red
package com.siguec.puraceramica.core.red

/**
 * Tablas y columnas de Profit Plus 2K12 que lee la App (SOLO LECTURA de artículos).
 *
 * Los valores por defecto son los de Profit 2KDoce. Si en su base alguno es
 * distinto, NO se cambia la App: se agrega un renglón en
 * SIGUEC_PURACERAMICA.dbo.parametros (clave "profit.<nombre>", ej.
 * "profit.columnas_codigo" = "modelo,ref,cod_barra"). La App los lee al conectarse.
 *
 * Como estos nombres van DENTRO del SQL (no pueden ser parámetros "?"), se
 * validan: solo letras, números y guion bajo.
 */
data class ConfigProfit(
    // Artículos
    val tablaArticulos: String = "saArticulo",
    val colCodigo: String = "co_art",
    val colDescripcion: String = "art_des",
    val colAnulado: String = "anulado",             // "" = todos activos
    // Tipo de artículo (V venta, C consumo, S servicio, F fabricación, M materia prima,
    // N envase, E empaque). Los servicios no tienen existencia: no se cuentan.
    val colTipo: String = "tipo",
    val tiposExcluidos: List<String> = listOf("S"),
    // Columnas del artículo que también se escanean (además de co_art); las que no existan se ignoran
    val columnasCodigo: List<String> = listOf("modelo", "ref"),
    // Tabla aparte de códigos de barra ("" = no hay)
    val tablaCodigosBarra: String = "",
    val colCodigoBarra: String = "cod_barra",
    val colCodigoBarraArticulo: String = "co_art",
    // Resultado de inventario (saResInventario): el número se arma con este prefijo + el
    // número del envío (APP0000012), para no chocar con el correlativo propio de Profit
    val prefijoResultado: String = "APP",
    // Moneda y tasa del resultado ("" = la del último saResInventario grabado en Profit)
    val coMone: String = "",
    val tasa: String = "1",
    // Existencia teórica de cada renglón (saResInventarioReng.total_art_teo)
    val tablaStock: String = "saStockAlmacen",
    val colStockTipo: String = "tipo",          // "" = la tabla no tiene tipo
    val tipoStockActual: String = "ACT",
    val colStockCantidad: String = "stock",
    // Costo unitario de cada renglón: función de la empresa que recibe saArticulo.rowguid
    val funcionCosto: String = "ConsultarUltimoCosto"
) {
    /** null = todo bien; si no, el nombre que no es válido. */
    fun validar(): String? =
        (listOf(tablaArticulos, colCodigo, colDescripcion, colAnulado, colTipo, tablaCodigosBarra, colCodigoBarra,
            colCodigoBarraArticulo, tablaStock, colStockTipo, colStockCantidad, funcionCosto) + columnasCodigo)
            .filter { it.isNotEmpty() }
            .firstOrNull { !PATRON.matches(it) }
            ?.let { "Nombre no válido de tabla o columna de Profit: '$it'" }
            // Los tipos van como texto dentro del SQL: solo una letra
            ?: tiposExcluidos.firstOrNull { !Regex("^[A-Z]$").matches(it) }?.let { "Tipo de artículo no válido: '$it'" }
            ?: prefijoResultado.takeUnless { Regex("^[A-Za-z0-9]{0,10}$").matches(it) }
                ?.let { "Prefijo del resultado no válido: '$it' (solo letras y números, máximo 10)" }
            ?: tasa.takeUnless { (it.toBigDecimalOrNull()?.signum() ?: 0) > 0 }?.let { "La tasa debe ser mayor que 0: '$it'" }
            ?: tipoStockActual.takeUnless { Regex("^[A-Za-z0-9]{0,6}$").matches(it) }?.let { "Tipo de stock no válido: '$it'" }

    companion object {
        private val PATRON = Regex("^[A-Za-z_][A-Za-z0-9_]{0,127}$")

        /** Claves de la tabla parametros que se entienden. */
        const val PREFIJO = "profit."

        /** Los valores de fábrica cambiados por los de la tabla parametros (clave -> valor). */
        fun desdeParametros(p: Map<String, String>): ConfigProfit {
            val d = ConfigProfit()
            fun v(nombre: String, defecto: String) = p[PREFIJO + nombre]?.trim() ?: defecto
            return ConfigProfit(
                tablaArticulos = v("tabla_articulos", d.tablaArticulos),
                colCodigo = v("col_codigo", d.colCodigo),
                colDescripcion = v("col_descripcion", d.colDescripcion),
                colAnulado = v("col_anulado", d.colAnulado),
                colTipo = v("col_tipo", d.colTipo),
                tiposExcluidos = v("tipos_excluidos", d.tiposExcluidos.joinToString(","))
                    .split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() },
                columnasCodigo = v("columnas_codigo", d.columnasCodigo.joinToString(","))
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                tablaCodigosBarra = v("tabla_codigos_barra", d.tablaCodigosBarra),
                colCodigoBarra = v("col_codigo_barra", d.colCodigoBarra),
                colCodigoBarraArticulo = v("col_codigo_barra_articulo", d.colCodigoBarraArticulo),
                prefijoResultado = v("prefijo_resultado", d.prefijoResultado),
                coMone = v("co_mone", d.coMone),
                tasa = v("tasa", d.tasa),
                tablaStock = v("tabla_stock", d.tablaStock),
                colStockTipo = v("col_stock_tipo", d.colStockTipo),
                tipoStockActual = v("tipo_stock_actual", d.tipoStockActual),
                colStockCantidad = v("col_stock_cantidad", d.colStockCantidad),
                funcionCosto = v("funcion_costo", d.funcionCosto)
            )
        }
    }
}
