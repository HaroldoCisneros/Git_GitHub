// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Las de la sesión se guardan en el teléfono como JSON (para entrar sin WiFi)
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

// =====================================================================
// Clases de datos que usan las pantallas. Se mantienen los nombres "...Dto"
// de AppSighecMovil para que los módulos de pantallas (login, admin...) sean
// los mismos. Las cantidades van como TEXTO ("12.5") para no tener redondeos.
// =====================================================================

// ---------------------------- Sesión ----------------------------

/** Qué puede hacer el usuario en una empresa (tabla ambientes de SIGUEC_PURACERAMICA). */
@Serializable
data class PermisosDto(
    val consultarArticulos: Boolean = false,    // Código, descripción y contado
    val sincronizarArticulos: Boolean = false,  // Bajar los artículos de Profit al teléfono
    val contarInventario: Boolean = false,      // Traer los inventarios abiertos de Profit y contar
    val cerrarInventario: Boolean = false       // Enviar el conteo a la toma física de Profit
)

/** Una empresa de Profit 2K12 a la que el usuario tiene acceso. */
@Serializable
data class AmbienteDto(
    val coEmpresa: String,                      // Nombre de la base de Profit (ej. PURACERAMICA)
    val nombre: String,                         // Nombre que ve el usuario
    val almacen: String = "",                   // Almacén por defecto
    val permisos: PermisosDto = PermisosDto()
)

/** El usuario que entró. */
@Serializable
data class UsuarioDto(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val esAdministrador: Boolean = false,
    val usuarioProfit: String = ""              // Se graba en la toma física de Profit
)

/**
 * La sesión en el teléfono.
 * @property sinConexion true = entró sin WiFi (con la clave guardada de la última vez)
 */
@Serializable
data class SesionDto(
    val usuario: UsuarioDto,
    val ambientes: List<AmbienteDto> = emptyList(),
    val sinConexion: Boolean = false
)

// ---------------------------- Artículos ----------------------------

/**
 * Un artículo tal como se consulta: SOLO código, descripción y stock contado.
 * @property contado suma de los conteos en el inventario elegido; null = no se ha contado
 */
data class ArticuloDto(
    val coArt: String,
    val artDes: String = "",
    val contado: String? = null
)

/** Un artículo leído de Profit para la copia del teléfono. */
data class ArticuloCopia(val coArt: String, val artDes: String, val anulado: Boolean)

/** Un código que se puede escanear: co_art (prioridad 0), modelo, ref, código de barra... */
data class CodigoCopia(val codigo: String, val coArt: String, val origen: String, val prioridad: Int)

/** Lo leído de Profit en la sincronización, ya limpio. */
data class CopiaArticulos(val articulos: List<ArticuloCopia>, val codigos: List<CodigoCopia>)

/** Última sincronización de los artículos en ESTE teléfono. */
data class SincronizacionDto(
    val fecha: String? = null,
    val usuario: String = "",
    val total: Int = 0,          // Artículos activos en el teléfono
    val codigos: Int = 0         // Códigos para escanear
)

// ---------------------------- Inventarios (toma física) ----------------------------

/** Una toma física ABIERTA en Profit (se trae al teléfono para contar). */
data class TomaProfitDto(
    val numero: String,          // Número de la toma en Profit
    val coAlma: String,
    val desAlma: String = "",
    val descripcion: String = "",
    val fecha: String? = null
)

/** Un inventario guardado en el teléfono (traído de Profit). */
data class InventarioDto(
    val id: Int,                 // Número interno del teléfono
    val numero: String = "",     // Número de la toma física en Profit
    val coAlma: String = "",
    val desAlma: String = "",
    val descripcion: String = "",
    val fecha: String? = null,
    val abierto: Boolean = true, // false = ya se envió a Profit (no se puede contar)
    val enviadoPor: String = "",
    val fechaEnvio: String? = null,
    val articulosContados: Int = 0,
    val conteos: Int = 0
)

/** Un conteo individual guardado en el teléfono (quién contó qué y cuánto). */
data class ConteoDto(
    val id: Long,
    val coArt: String,
    val artDes: String = "",
    val codigoLeido: String = "",
    val cantidad: String = "0",
    val usuario: String = "",
    val fecha: String? = null,
    val puedeBorrar: Boolean = false
)

/** Un conteo que se manda a Profit al cerrar. */
data class RenglonEnvio(
    val coArt: String,
    val codigoLeido: String,
    val cantidad: BigDecimal,
    val usuario: String,
    val fecha: LocalDateTime
)

/** Lo que se envió a Profit. */
data class ResultadoEnvio(val envioId: Int, val articulos: Int, val conteos: Int)

// ---------------------------- Administración ----------------------------

/** Usuario nuevo. */
data class EntradaUsuarioNuevoDto(
    val usuario: String,
    val nombre: String,
    val clave: String,
    val usuarioProfit: String = "",
    val esAdministrador: Boolean = false
)

/** Cambios de un usuario: lo que vaya en null no se toca. */
data class EntradaUsuarioCambiosDto(
    val nombre: String? = null,
    val usuarioProfit: String? = null,
    val esAdministrador: Boolean? = null,
    val activo: Boolean? = null
)

/** Empresa, almacén y permisos de un usuario: {"contar_inventario": true, ...}. */
data class EntradaAmbienteDto(
    val almacen: String = "",
    val permisos: Map<String, Boolean>
)

data class EmpresaAppDto(val coEmpresa: String, val nombre: String = "", val activa: Boolean = true)

/** Una empresa del usuario, con sus permisos. */
data class AmbienteAdminDto(
    val coEmpresa: String,
    val nombreEmpresa: String = "",
    val almacen: String = "",
    val permisos: PermisosDto = PermisosDto()
)

/** Un usuario de la App visto por el administrador. */
data class UsuarioAdminDto(
    val id: Int,
    val usuario: String,
    val nombre: String = "",
    val usuarioProfit: String = "",
    val esAdministrador: Boolean = false,
    val activo: Boolean = true,
    val bloqueado: Boolean = false,              // Bloqueado ahora por claves erradas
    val bloqueadoHasta: String? = null,
    val intentosFallidos: Int = 0,
    val ultimoAcceso: String? = null,
    val ambientes: List<AmbienteAdminDto> = emptyList()
)

/** Un renglón de la bitácora. */
data class RenglonBitacoraDto(
    val id: Long,
    val fecha: String? = null,
    val usuario: String = "",
    val coEmpresa: String = "",
    val accion: String = "",
    val detalle: String = "",
    val dispositivo: String = ""
)

data class PaginaBitacoraDto(
    val renglones: List<RenglonBitacoraDto> = emptyList(),
    val pagina: Int = 1,
    val tamano: Int = 50,
    val hayMas: Boolean = false
)
