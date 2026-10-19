// MÓDULO :core:red (pruebas)
package com.siguec.puraceramica.core.red

// JUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de Formato, Inventario y Administracion (Logica.kt). */
class LogicaTest {

    @Test
    fun formatoVenezolano() {
        assertEquals("1.250", Formato.cantidad("1250"))
        assertEquals("8,5", Formato.cantidad("8.5"))
        assertEquals("—", Formato.cantidad("abc"))
        assertEquals("19/10/2026 14:30", Formato.fecha("2026-10-19 14:30:00"))
        assertEquals("19/10/2026", Formato.fecha("2026-10-19"))
    }

    @Test
    fun cantidadesDelInventario() {
        assertNull(Inventario.validarCantidad("2,5"))
        assertNull(Inventario.validarCantidad("0"))
        assertNull(Inventario.validarCantidad("-1"))
        assertNotNull(Inventario.validarCantidad(""))
        assertNotNull(Inventario.validarCantidad("2000000"))
        assertEquals("+2", Inventario.diferencia("2"))
        assertEquals("-3,5", Inventario.diferencia("-3.5"))
        assertEquals(-1, Inventario.signo("-1"))
        assertEquals("Sin contar", Inventario.contado(null))
        assertEquals("Contado: 8,5", Inventario.contado("8.5"))
        assertEquals("Toma N° 15 · Almacén 01 PRINCIPAL",
            Inventario.titulo(InventarioDto(id = 3, numero = "15", coAlma = "01", desAlma = "PRINCIPAL")))
    }

    @Test
    fun sumasExactasYBusqueda() {
        assertEquals("8.5", Inventario.sumar(listOf("6", "2.5")))
        assertEquals("0.3", Inventario.sumar(listOf("0.1", "0.2")))   // Con Double daría 0.30000000000000004
        assertEquals("0", Inventario.sumar(listOf("1", "-1")))
        assertEquals("%tubo%pvc%", Inventario.patronLike(" tubo*pvc "))
        assertEquals("%50\\%\\_%", Inventario.patronLike("50%_"))
        assertEquals("", Inventario.patronLike(""))
        // Fuente Code 39 (C39HrP48DhTt): los asteriscos de inicio y fin no son parte del código
        assertEquals("CER-001", Inventario.limpiarCodigo(" *CER-001* "))
        assertEquals("CER-001", Inventario.limpiarCodigo("CER-001"))
        assertNotNull(Inventario.validarCantidad("0,0000001"))
    }

    @Test
    fun permisosDelAdministrador() {
        val p = PermisosDto(consultarArticulos = true, contarInventario = true)
        assertEquals("Consulta · Cuenta", Administracion.resumen(p))
        assertEquals(NOMBRES_PERMISOS, Administracion.PERMISOS.map { it.first })
        assertEquals(Administracion.PERMISOS.map { it.first }, Administracion.mapa(p).keys.toList())
        assertEquals("Sincronizó artículos", Administracion.accion("SINCRONIZAR"))
        assertNull(Administracion.validarNuevo("ANA", "Ana", "secreta", "secreta"))
        assertNotNull(Administracion.validarNuevo("a b", "Ana", "secreta", "secreta"))
    }
}
