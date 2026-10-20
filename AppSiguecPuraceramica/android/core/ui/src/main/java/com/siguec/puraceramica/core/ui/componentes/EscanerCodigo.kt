// MÓDULO :core:ui
// Paquete de componentes compartidos (lo usan Artículos y Conteo)
package com.siguec.puraceramica.core.ui.componentes

// Contexto de Android (para abrir el escáner)
import android.content.Context
// Tipos de código de barras que se aceptan
import com.google.mlkit.vision.barcode.common.Barcode
// Escáner de Google: opciones y cliente
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Lee un código de barras con la cámara usando el escáner de Google
 * (Google Code Scanner, de Play Services).
 *
 * La pantalla de la cámara la pone Google Play Services: la App NO necesita
 * pedir el permiso de cámara ni dibujar nada. El usuario apunta, el escáner
 * lee y devuelve el texto del código.
 */
object EscanerCodigo {

    // Las etiquetas propias se imprimen con la fuente "C39HrP48DhTt" = CODE 39
    // (ej. *3065600601201*, confirmado con la foto de una etiqueta real).
    // SOLO formatos con estructura fuerte: antes también se aceptaban ITF, Codabar, UPC-E,
    // EAN-8, Code 93 y QR, y con la etiqueta de lado o leída a medias el escáner devolvía
    // números FALSOS (sobre todo ITF, que acepta pedazos de cualquier código numérico).
    private val opciones = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_CODE_39,  // Etiquetas propias (fuente C39HrP48DhTt)
            Barcode.FORMAT_EAN_13,   // Código de fábrica de los productos (tiene dígito de control)
            Barcode.FORMAT_CODE_128  // Otras etiquetas impresas (tiene dígito de control)
        )
        .enableAutoZoom()            // Acerca la cámara sola si la etiqueta se ve pequeña
        .build()

    /**
     * Abre la cámara y espera un código.
     * @param alLeer se llama con el texto del código leído
     * @param alFallar se llama con un mensaje si el escáner no se pudo abrir
     * Si el usuario cancela (botón atrás) no se llama a ninguna de las dos.
     */
    fun escanear(contexto: Context, alLeer: (String) -> Unit, alFallar: (String) -> Unit) {
        GmsBarcodeScanning.getClient(contexto, opciones)
            .startScan()
            // Leyó un código: se le quitan los espacios a los lados
            .addOnSuccessListener { codigo ->
                // Sin los asteriscos de inicio/fin de Code 39 (*CER-001*) ni espacios
                val texto = codigo.rawValue?.trim()?.trim('*')?.trim().orEmpty()
                if (texto.isNotEmpty()) alLeer(texto) else alFallar("No se pudo leer el código. Intente de nuevo.")
            }
            // No se pudo abrir (teléfono sin Play Services, módulo descargándose...)
            .addOnFailureListener { error ->
                alFallar("No se pudo abrir el escáner: ${error.localizedMessage ?: "error desconocido"}. " +
                    "Revise que Google Play Services esté actualizado.")
            }
    }
}
