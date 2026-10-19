// MÓDULO :core:red (pruebas)
package com.siguec.puraceramica.core.red

// Corrutinas en las pruebas
import kotlinx.coroutines.test.runTest
// Servidor HTTP falso
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
// JUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del cliente HTTP contra un servidor falso (MockWebServer): que las
 * rutas, el token y el JSON sean los que espera servidor/api, y que los
 * errores lleguen como Resultado.Error con el mensaje del servidor.
 */
class ClienteSiguecTest {

    private lateinit var servidor: MockWebServer
    private var vencida = false

    private fun cliente() = ClienteSiguec(servidor.url("/").toString(), token = { "TOKEN-1" },
        alVencerSesion = { vencida = true })

    @Before
    fun antes() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun despues() {
        servidor.shutdown()
    }

    private fun responder(codigo: Int, json: String) =
        servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(json))

    @Test
    fun entrarLeeLosPermisosDeInventario() = runTest {
        responder(200, """{"token":"abc","usuario":{"usuario":"ANA","nombre":"Ana"},
            "ambientes":[{"co_empresa":"PURACERAMICA","nombre":"Pura Cerámica","almacen":"01",
            "permisos":{"consultar_articulos":true,"sincronizar_articulos":false,
            "preparar_inventario":false,"contar_inventario":true}}],"expira":"2026-10-19T21:00:00"}""")
        val r = RepositorioSesionRemota(cliente()).entrar(" ana ", "clave", "Zebra")
        val sesion = (r as Resultado.Exito).valor
        assertEquals("abc", sesion.token)
        assertTrue(sesion.ambientes[0].permisos.contarInventario)
        val peticion = servidor.takeRequest()
        assertEquals("/api/v1/sesion", peticion.path)
        assertTrue(peticion.body.readUtf8().contains("\"usuario\":\"ana\""))
    }

    @Test
    fun consultaMandaElInventarioYElToken() = runTest {
        responder(200, """{"articulos":[{"co_art":"CER-001","art_des":"CERAMICA","contado":"8.5"},
            {"co_art":"CER-002","art_des":"GRIS","contado":null}],"pagina":1,"tamano":30,"hay_mas":false}""")
        val r = RepositorioArticulos(cliente(), "PURACERAMICA").buscar("cer*", inventarioId = 3)
        val pagina = (r as Resultado.Exito).valor
        assertEquals("8.5", pagina.articulos[0].contado)
        assertNull(pagina.articulos[1].contado)
        val peticion = servidor.takeRequest()
        assertEquals("Bearer TOKEN-1", peticion.getHeader("Authorization"))
        assertEquals("/api/v1/empresas/PURACERAMICA/articulos?texto=cer*&inventario_id=3&solo_contados=false&pagina=1&tamano=30",
            peticion.path)
    }

    @Test
    fun anotarUnConteo() = runTest {
        responder(200, """{"conteo":{"id":7,"co_art":"CER-001","art_des":"CERAMICA","codigo_leido":"759111",
            "cantidad":"6","usuario":"ANA","puede_borrar":true},"contado_usuario":"6","contado_total":"8.5"}""")
        val r = RepositorioConteos(cliente(), "PURACERAMICA").anotar(3, " 759111 ", "6")
        assertEquals("8.5", (r as Resultado.Exito).valor.contadoTotal)
        val peticion = servidor.takeRequest()
        assertEquals("/api/v1/empresas/PURACERAMICA/inventarios/3/conteos", peticion.path)
        assertEquals("""{"codigo":"759111","cantidad":"6"}""", peticion.body.readUtf8())
    }

    @Test
    fun erroresConElMensajeDelServidor() = runTest {
        responder(409, """{"detalle":"El almacén 01 ya tiene abierto el inventario N° 3."}""")
        val r = RepositorioInventarios(cliente(), "PURACERAMICA").preparar("01") as Resultado.Error
        assertEquals(TipoError.CONFLICTO, r.tipo)
        assertEquals("El almacén 01 ya tiene abierto el inventario N° 3.", r.mensaje)

        responder(401, """{"detalle":"La sesión venció."}""")
        val vencio = RepositorioInventarios(cliente(), "PURACERAMICA").lista() as Resultado.Error
        assertEquals(TipoError.SESION, vencio.tipo)
        assertTrue(vencida)
    }

    @Test
    fun sinServidorEsErrorDeRed() = runTest {
        servidor.shutdown()
        val r = RepositorioSesionRemota(cliente()).probarServidor() as Resultado.Error
        assertEquals(TipoError.RED, r.tipo)
    }

    @Test
    fun normalizarUrl() {
        assertEquals("https://siguec.miempresa.com/", ClienteSiguec.normalizarUrl("siguec.miempresa.com").toString())
        assertEquals("http://192.168.1.10:8000/", ClienteSiguec.normalizarUrl("http://192.168.1.10:8000").toString())
        assertNull(ClienteSiguec.normalizarUrl("  "))
    }
}
