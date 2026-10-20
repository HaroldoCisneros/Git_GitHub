// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Formato de números con separadores fijos
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

// =====================================================================
// Lógica que NO depende de Android (se prueba en LogicaTest.kt con
// "gradlew :core:red:test"). La usan los módulos de pantallas.
// =====================================================================

/** Cómo se muestran números y fechas (formato venezolano: 1.234,5). */
object Formato {

    // Punto para miles y coma para decimales (fijo, sin depender del idioma del teléfono)
    private val simbolos = DecimalFormatSymbols().apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    /** Cantidad: sin decimales si es entera ("1.250"), si no con hasta 3 ("2,5"). null -> "—". */
    fun cantidad(valor: Double?): String =
        if (valor == null) "—" else DecimalFormat("#,##0.###", simbolos).format(valor)

    /** Cantidad que viene como texto del servidor ("8.5" -> "8,5"; basura -> "—"). */
    fun cantidad(texto: String?): String = cantidad(texto?.trim()?.toDoubleOrNull())

    /** Fecha y hora del teléfono: "2026-10-19 14:30:05". */
    fun ahora(): String = java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')

    /** "2026-10-19" -> "19/10/2026"; "2026-10-19 14:30:00" -> "19/10/2026 14:30". */
    fun fecha(texto: String): String {
        val partes = texto.trim().split(" ", "T")
        val f = partes[0].split("-")
        if (f.size != 3) return texto
        val hora = partes.getOrNull(1)?.take(5)?.let { " $it" }.orEmpty()
        return "${f[2]}/${f[1]}/${f[0]}$hora"
    }
}

/** Reglas de la toma de inventario. */
object Inventario {

    /** Diferencia con signo: "+2", "-3", "0". */
    fun diferencia(texto: String?): String {
        val valor = texto?.trim()?.toDoubleOrNull() ?: return "—"
        return (if (valor > 0) "+" else "") + Formato.cantidad(valor)
    }

    /** -1 falta, 0 cuadra, 1 sobra (para el color del renglón). */
    fun signo(texto: String?): Int = (texto?.trim()?.toDoubleOrNull() ?: 0.0).compareTo(0.0)

    /**
     * Revisa la cantidad escrita antes de mandarla (el servidor vuelve a revisar).
     * El 0 se acepta ("lo busqué y no hay"); negativo sirve para corregir.
     * @return null si está bien; si no, el mensaje
     */
    fun validarCantidad(texto: String): String? {
        val valor = leer(texto) ?: return "Escriba la cantidad contada"
        if (valor.abs() >= java.math.BigDecimal(1_000_000)) return "Cantidad demasiado grande"
        if (valor.scale() > 5) return "Máximo 5 decimales"
        return null
    }

    /**
     * Lo escaneado o escrito -> código para buscar.
     * Las etiquetas se imprimen con la fuente Code 39 "C39HrP48DhTt": el código va entre
     * asteriscos (*CER-001*), que son las marcas de inicio y fin. Algunos escáneres los
     * entregan dentro del texto: se quitan, igual que los espacios de los lados.
     */
    fun limpiarCodigo(texto: String): String = texto.trim().trim('*').trim()

    /** Suma exacta de cantidades guardadas como texto ("2.5" + "6" = "8.5"). */
    fun sumar(cantidades: List<String>): String =
        texto(cantidades.fold(java.math.BigDecimal.ZERO) { suma, c -> suma + (c.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) })

    /** BigDecimal -> "8.5" (punto decimal, sin ceros de más; 0 -> "0"). */
    fun texto(valor: java.math.BigDecimal): String =
        if (valor.signum() == 0) "0" else valor.stripTrailingZeros().toPlainString()

    /**
     * Cantidad escrita (ya validada con [validarCantidad]) -> BigDecimal. Acepta coma o punto.
     * @return null si no es un número
     */
    fun leer(texto: String): java.math.BigDecimal? = texto.trim().replace(',', '.').toBigDecimalOrNull()

    /**
     * Lo escrito -> patrón de LIKE: "tubo*pvc" -> "%tubo%pvc%". Se escapan \ % _ con "\"
     * (la consulta usa ESCAPE '\') para que se busquen como letras.
     */
    fun patronLike(texto: String): String {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return ""
        val escapado = limpio.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%" + escapado.replace('*', '%') + "%"
    }

    /** Texto del stock contado en la consulta: "Contado: 8,5" o "Sin contar". */
    fun contado(texto: String?): String = if (texto == null) "Sin contar" else "Contado: ${Formato.cantidad(texto)}"

    /** "Inventario INV-0015 · Almacén 01 PRINCIPAL" (co_invfisico de Profit) */
    fun titulo(i: InventarioDto): String = "Inventario ${i.numero} · " + almacen(i.coAlma, i.desAlma)

    /** "Almacén 01 PRINCIPAL" ("Todos los almacenes" si el inventario no tiene almacén). */
    fun almacen(coAlma: String, desAlma: String): String =
        if (coAlma.isBlank()) "Todos los almacenes" else "Almacén $coAlma" + (if (desAlma.isNotBlank()) " $desAlma" else "")
}

/** Nombres que ve el administrador. */
object Administracion {

    /** Los permisos de un ambiente: nombre de la columna (sin "puede_") -> texto para el usuario (en este orden). */
    val PERMISOS: List<Pair<String, String>> = listOf(
        "consultar_articulos" to "Consultar artículos (código, descripción y contado)",
        "sincronizar_articulos" to "Sincronizar artículos con Profit",
        "contar_inventario" to "Traer inventarios abiertos y contar",
        "cerrar_inventario" to "Cerrar: enviar el conteo a Profit"
    )

    /** PermisosDto -> {"consultar_articulos": true, ...} (lo que espera el servidor al guardar). */
    fun mapa(p: PermisosDto): Map<String, Boolean> = mapOf(
        "consultar_articulos" to p.consultarArticulos,
        "sincronizar_articulos" to p.sincronizarArticulos,
        "contar_inventario" to p.contarInventario,
        "cerrar_inventario" to p.cerrarInventario
    )

    /** Resumen corto de los permisos para la lista ("Consulta · Cuenta · Cierra"). */
    fun resumen(p: PermisosDto): String = listOfNotNull(
        "Consulta".takeIf { p.consultarArticulos },
        "Sincroniza".takeIf { p.sincronizarArticulos },
        "Cuenta".takeIf { p.contarInventario },
        "Cierra".takeIf { p.cerrarInventario }
    ).joinToString(" · ").ifEmpty { "Sin permisos" }

    /** Acciones de la bitácora: código -> texto (las que no estén se muestran tal cual). */
    val ACCIONES: List<Pair<String, String>> = listOf(
        "ENTRADA" to "Entró",
        "SALIDA" to "Salió",
        "CLAVE_ERRADA" to "Clave errada",
        "BLOQUEO" to "Bloqueado",
        "SINCRONIZAR" to "Sincronizó artículos",
        "ABRIR_INVENTARIO" to "Abrió inventario",
        "ENVIAR_TOMA" to "Envió conteo a Profit",
        "ADMIN" to "Administración"
    )

    fun accion(codigo: String): String = ACCIONES.firstOrNull { it.first == codigo.trim() }?.second ?: codigo

    /** Revisa un usuario nuevo antes de mandarlo. null = está bien. */
    fun validarNuevo(usuario: String, nombre: String, clave: String, repetida: String): String? = when {
        !Regex("^[A-Za-z0-9_.\\-]{1,30}$").matches(usuario.trim()) ->
            "El usuario admite letras, números, punto y guion (máximo 30)"
        nombre.isBlank() -> "Escriba el nombre"
        clave.length < 6 -> "La clave debe tener al menos 6 caracteres"
        clave != repetida -> "Las claves no coinciden"
        else -> null
    }
}
