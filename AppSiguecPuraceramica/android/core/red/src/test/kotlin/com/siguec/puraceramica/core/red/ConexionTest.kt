// MÓDULO :core:red (pruebas)
package com.siguec.puraceramica.core.red

// Cantidades
import java.math.BigDecimal
import java.time.LocalDateTime
// Corrutinas en las pruebas
import kotlinx.coroutines.test.runTest
// JUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Configuración de la conexión, nombres de Profit, sincronización y totales del envío. */
class ConexionTest {

    @Test
    fun direccionJdbc() {
        val c = ConfigConexion(servidor = "192.168.1.10", puerto = "1433", clave = "x")
        assertEquals("jdbc:jtds:sqlserver://192.168.1.10:1433/PURACERAMICA;appName=AppSiguecPuraceramica;" +
            "loginTimeout=8;socketTimeout=180", c.urlJdbc("PURACERAMICA"))
        val instancia = ConfigConexion(servidor = "APPSRV", puerto = "", instancia = "SQLEXPRESS")
        assertEquals("jdbc:jtds:sqlserver://APPSRV/SIGUEC_PURACERAMICA;appName=AppSiguecPuraceramica;" +
            "loginTimeout=8;socketTimeout=180;instance=SQLEXPRESS", instancia.urlJdbc("SIGUEC_PURACERAMICA"))
    }

    @Test
    fun validarConfiguracion() {
        assertNull(ConfigConexion(servidor = "192.168.1.10").validar())
        assertNotNull(ConfigConexion(servidor = "").validar())
        assertNotNull(ConfigConexion(servidor = "a;b").validar())
        assertNotNull(ConfigConexion(servidor = "x", puerto = "abc").validar())
        assertNotNull(ConfigConexion(servidor = "x", puerto = "", instancia = "").validar())
        assertNotNull(ConfigConexion(servidor = "x", baseApp = "BASE; DROP").validar())
    }

    @Test
    fun sinServidorEsErrorDeConfiguracion() = runTest {
        val r = BaseDatos(ConfigConexion()).usar { "nunca" }
        assertEquals(TipoError.CONEXION, (r as Resultado.Error).tipo)
    }

    @Test
    fun nombresDeProfitDesdeParametros() {
        val cfg = ConfigProfit.desdeParametros(mapOf("profit.columnas_codigo" to "modelo, ref ,cod_barra",
            "profit.tabla_codigos_barra" to "saArtCodBarra"))
        assertEquals(listOf("modelo", "ref", "cod_barra"), cfg.columnasCodigo)
        assertEquals("saArtCodBarra", cfg.tablaCodigosBarra)
        assertEquals("saArticulo", cfg.tablaArticulos)
        assertNull(cfg.validar())
        assertNotNull(ConfigProfit(tablaArticulos = "saArticulo; DROP TABLE x").validar())
    }

    @Test
    fun sqlDeProfit2K12() {
        assertEquals("SELECT RTRIM([co_art]) AS co_art, RTRIM([art_des]) AS art_des, [anulado] AS anulado, " +
            "RTRIM(CAST([modelo] AS varchar(60))) AS [x_modelo] FROM dbo.[saArticulo]",
            sqlArticulos(ConfigProfit(), true, listOf("modelo")))
        assertTrue(sqlArticulos(ConfigProfit(), false, emptyList()).contains("0 AS anulado"))
        // saArticulo.tipo: los servicios (S) no se cuentan
        assertTrue(sqlArticulos(ConfigProfit(), true, emptyList(), conTipo = true).endsWith(" WHERE [tipo] NOT IN ('S')"))
        assertNotNull(ConfigProfit(tiposExcluidos = listOf("S') OR 1=1 --")).validar())
    }

    @Test
    fun prepararCopiaLimpiaYArmaCodigos() {
        val filas = listOf(
            mapOf("co_art" to "A1  ", "art_des" to "UNO ", "anulado" to 0, "x_modelo" to "759001", "x_ref" to "R1"),
            mapOf("co_art" to "A1", "art_des" to "REPETIDO", "anulado" to 0),
            mapOf("co_art" to "", "art_des" to "SIN CODIGO", "anulado" to 0),
            mapOf("co_art" to "B2", "art_des" to "", "anulado" to true, "x_modelo" to "B2", "x_ref" to " ")
        )
        val barras = listOf(mapOf("codigo" to "759999", "co_art" to "B2"), mapOf("codigo" to "111", "co_art" to "NO"))
        val copia = prepararCopia(filas, listOf("modelo", "ref"), barras)
        assertEquals(listOf(ArticuloCopia("A1", "UNO", false), ArticuloCopia("B2", "B2", true)), copia.articulos)
        assertEquals(
            setOf(CodigoCopia("A1", "A1", "co_art", 0), CodigoCopia("759001", "A1", "modelo", 1),
                CodigoCopia("R1", "A1", "ref", 2), CodigoCopia("B2", "B2", "co_art", 0),
                CodigoCopia("759999", "B2", "barra", 3)),
            copia.codigos.toSet()
        )
    }

    @Test
    fun numeroDelResultadoDeInventario() {
        // saResInventario.num_resinv es char(20)
        assertEquals("APP0000012", TomaFisica2K12.numeroResultado("APP", 12))
        assertTrue(TomaFisica2K12.numeroResultado("ABCDEFGHIJ", Int.MAX_VALUE).length <= 20)
        assertNotNull(ConfigProfit(prefijoResultado = "AP P").validar())
        assertNotNull(ConfigProfit(tasa = "0").validar())
        assertNull(ConfigProfit.desdeParametros(mapOf("profit.co_mone" to "BS")).validar())
    }

    @Test
    fun totalesDelEnvio() {
        val ahora = LocalDateTime.of(2026, 10, 19, 9, 0)
        val renglones = listOf(
            RenglonEnvio("A", "759", BigDecimal("6"), "ANA", ahora),
            RenglonEnvio("A", "A", BigDecimal("2.5"), "LUIS", ahora),
            RenglonEnvio("B", "B", BigDecimal("-1"), "ANA", ahora),
            RenglonEnvio("B", "B", BigDecimal("1"), "ANA", ahora)
        )
        val totales = RepositorioInventarioProfit.totalesPorArticulo(renglones)
        assertEquals(0, BigDecimal("8.5").compareTo(totales["A"]))
        assertEquals(0, BigDecimal.ZERO.compareTo(totales["B"]))
    }
}
