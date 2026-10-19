// MÓDULO :core:local
package com.siguec.puraceramica.core.local

// Contexto de Android (carpeta privada de la App)
import android.content.Context

/**
 * Puerta de entrada a la base del teléfono. Se crea UNA vez (GestorSesion).
 *   articulos    Copia de los artículos de Profit.
 *   inventarios  Tomas físicas traídas de Profit y sus conteos.
 */
class AlmacenLocal(contexto: Context) {
    private val base = BaseLocal(contexto)
    val articulos = ArticulosLocales(base)
    val inventarios = InventariosLocales(base)
}
