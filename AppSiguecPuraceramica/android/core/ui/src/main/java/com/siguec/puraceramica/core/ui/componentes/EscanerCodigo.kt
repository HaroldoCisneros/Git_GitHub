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

    // Formatos de los productos (cerámicas, porcelanatos...: EAN/UPC de fábrica, Code 128/39 de
    // etiquetas propias) y QR por si acaso. Limitar los formatos hace la lectura más rápida.
    private val opciones = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,  // El más común en productos (13 dígitos)
            Barcode.FORMAT_EAN_8,   // EAN corto
            Barcode.FORMAT_UPC_A,   // Productos de EE. UU.
            Barcode.FORMAT_UPC_E,   // UPC corto
            Barcode.FORMAT_CODE_128, // Etiquetas impresas por la empresa
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_QR_CODE
        )
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
                val texto = codigo.rawValue?.trim().orEmpty()
                if (texto.isNotEmpty()) alLeer(texto) else alFallar("No se pudo leer el código. Intente de nuevo.")
            }
            // No se pudo abrir (teléfono sin Play Services, módulo descargándose...)
            .addOnFailureListener { error ->
                alFallar("No se pudo abrir el escáner: ${error.localizedMessage ?: "error desconocido"}. " +
                    "Revise que Google Play Services esté actualizado.")
            }
    }
}
