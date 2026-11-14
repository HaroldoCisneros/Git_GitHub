package com.sighec.pedidos.data

/**
 * Todas las consultas a tablas de Profit Plus están aquí para poder
 * ajustarlas fácilmente a la versión instalada.
 *
 * Escritas para el esquema de Profit Plus 2K12 (tablas sa*):
 *   saLineaArticulo, saArticulo, saArtPrecio, saStockAlmacen, saCliente.
 * Si usa otra versión (p. ej. 2K8: lin_art, art, clientes) cambie solo este archivo.
 */
object ProfitQueries {

    const val LINEAS = """
        SELECT RTRIM(l.co_lin) AS co_lin, RTRIM(l.lin_des) AS lin_des
        FROM saLineaArticulo l
        WHERE EXISTS (SELECT 1 FROM saArticulo a WHERE a.co_lin = l.co_lin AND a.anulado = 0)
        ORDER BY l.lin_des
    """

    /** Parámetros: 1 co_precio, 2 co_alma, 3 co_alma, 4 co_lin */
    const val ARTICULOS_POR_LINEA = """
        SELECT RTRIM(a.co_art) AS co_art, RTRIM(a.art_des) AS art_des, RTRIM(a.co_lin) AS co_lin,
               ISNULL(p.monto, 0) AS precio, ISNULL(s.stock, 0) AS stock
        FROM saArticulo a
        OUTER APPLY (SELECT TOP 1 ap.monto FROM saArtPrecio ap
                     WHERE ap.co_art = a.co_art AND ap.co_precio = ? AND ap.inactivo = 0
                     ORDER BY ap.desde DESC) p
        OUTER APPLY (SELECT SUM(sa.stock) AS stock FROM saStockAlmacen sa
                     WHERE sa.co_art = a.co_art AND sa.tipo = 'ACT'
                       AND (? = '' OR sa.co_alma = ?)) s
        WHERE a.co_lin = ? AND a.anulado = 0
        ORDER BY a.art_des
    """

    /** Parámetros: 1 patrón código, 2 patrón nombre/rif, 3 patrón rif */
    const val BUSCAR_CLIENTES = """
        SELECT TOP 50 RTRIM(c.co_cli) AS co_cli, RTRIM(c.cli_des) AS cli_des, RTRIM(ISNULL(c.rif, '')) AS rif
        FROM saCliente c
        WHERE c.inactivo = 0 AND (c.co_cli LIKE ? OR c.cli_des LIKE ? OR c.rif LIKE ?)
        ORDER BY c.cli_des
    """
}
