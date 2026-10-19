// MÓDULO :app
// Paquete principal de la app
package com.siguec.puraceramica.app

// Clase base de la aplicación de Android
import android.app.Application
// Dueño de la sesión (módulo :core:sesion)
import com.siguec.puraceramica.core.sesion.GestorSesion

/**
 * Clase que Android crea PRIMERO al abrir la app (antes de cualquier pantalla).
 * Guarda las piezas que viven mientras la app esté abierta.
 */
class SiguecApp : Application() {

    /** La sesión del usuario: UNA sola para toda la app ("by lazy" = se crea al primer uso). */
    val gestorSesion: GestorSesion by lazy { GestorSesion(this) }
}
