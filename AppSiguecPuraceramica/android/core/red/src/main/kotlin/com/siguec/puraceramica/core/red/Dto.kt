// MÓDULO :core:red
// Paquete de la comunicación con la API
package com.siguec.puraceramica.core.red

// Nombre del campo en el JSON cuando es distinto al de Kotlin (co_art -> coArt)
import kotlinx.serialization.SerialName
// Marca la clase para convertirla a JSON y desde JSON
import kotlinx.serialization.Serializable

// =====================================================================
// "DTO" = Data Transfer Object: la forma EXACTA del JSON que viaja entre
// el teléfono y la API. Son las mismas clases de servidor/api/esquemas.py:
// si se cambia algo allá, se cambia aquí también.
// Los valores por defecto (= "") hacen que, si el servidor deja de mandar
// un campo, la App no se caiga.
// Las cantidades viajan como TEXTO ("12.5") para no tener redondeos.
// =====================================================================

// ---------------------------- Sesión ----------------------------

/** Lo que se manda al entrar. */
@Serializable
data class EntradaSesionDto(
    val usuario: String,
    val clave: String,
    val dispositivo: String = "" // Modelo del teléfono (queda en la bitácora del servidor)
)

/** Qué puede hacer el usuario en una empresa (tabla ambientes de SIGUEC_PURACERAMICA). */
@Serializable
data class PermisosDto(
    @SerialName("consultar_articulos") val consultarArticulos: Boolean = false,     // Código, descripción y contado
    @SerialName("sincronizar_articulos") val sincronizarArticulos: Boolean = false, // Bajar artículos de Profit
    @SerialName("preparar_inventario") val prepararInventario: Boolean = false,     // Abrir/cerrar y comparar
    @SerialName("contar_inventario") val contarInventario: Boolean = false          // Escanear y anotar
)

/** Una empresa de Profit 2K12 a la que el usuario tiene acceso. */
@Serializable
data class AmbienteDto(
    @SerialName("co_empresa") val coEmpresa: String, // Nombre de la base de Profit (ej. PURACERAMICA)
    val nombre: String,                              // Nombre que ve el usuario
    val almacen: String = "",                        // Almacén por defecto al preparar
    val permisos: PermisosDto = PermisosDto()
)

/** Datos del usuario que entró. */
@Serializable
data class UsuarioDto(
    val usuario: String,
    val nombre: String,
    @SerialName("es_administrador") val esAdministrador: Boolean = false
)

/**
 * Respuesta de POST /sesion (con token) y de GET /sesion (sin token).
 * @property expira fecha y hora en que vence el token (texto ISO del servidor)
 */
@Serializable
data class SesionDto(
    val usuario: UsuarioDto,
    val ambientes: List<AmbienteDto> = emptyList(),
    val expira: String = "",
    val token: String? = null
)

/** Respuesta de GET /salud. */
@Serializable
data class SaludDto(
    val estado: String = "",
    val aplicacion: String = "",
    val version: String = ""
)

// ---------------------------- Artículos ----------------------------

/**
 * Un artículo tal como se consulta: SOLO código, descripción y stock contado.
 * @property contado suma de los conteos en el inventario elegido; null = no se ha contado
 */
@Serializable
data class ArticuloDto(
    @SerialName("co_art") val coArt: String,
    @SerialName("art_des") val artDes: String = "",
    val contado: String? = null
)

/** Una página de la consulta. hayMas = true si se puede pedir la siguiente. */
@Serializable
data class PaginaArticulosDto(
    val articulos: List<ArticuloDto> = emptyList(),
    val pagina: Int = 1,
    val tamano: Int = 0,
    @SerialName("hay_mas") val hayMas: Boolean = false,
    @SerialName("inventario_id") val inventarioId: Int? = null
)

/** Resultado de "Sincronizar artículos" (y la última sincronización). */
@Serializable
data class SincronizacionDto(
    val fecha: String? = null,
    val usuario: String = "",
    val leidos: Int = 0,        // Artículos leídos de Profit
    val nuevos: Int = 0,
    val actualizados: Int = 0,
    val retirados: Int = 0,     // Ya no están en Profit
    val codigos: Int = 0,       // Códigos para escanear
    val total: Int = 0          // Artículos en la copia
)

/** Respuesta de GET .../articulos/sincronizacion. */
@Serializable
data class UltimaSincronizacionDto(val sincronizacion: SincronizacionDto? = null)

// ---------------------------- Inventarios ----------------------------

/** Un almacén de Profit (saAlmacen) y su inventario abierto, si lo tiene. */
@Serializable
data class AlmacenDto(
    @SerialName("co_alma") val coAlma: String,
    @SerialName("des_alma") val desAlma: String = "",
    @SerialName("inventario_abierto") val inventarioAbierto: Int? = null
)

@Serializable
data class ListaAlmacenesDto(val almacenes: List<AlmacenDto> = emptyList())

/** Lo que se manda para preparar (abrir) un inventario. */
@Serializable
data class EntradaInventarioDto(
    @SerialName("co_alma") val coAlma: String,
    val descripcion: String = ""
)

/** Un inventario preparado de un almacén. */
@Serializable
data class InventarioDto(
    val id: Int,
    @SerialName("co_alma") val coAlma: String = "",
    @SerialName("des_alma") val desAlma: String = "",
    val descripcion: String = "",
    val abierto: Boolean = true,
    @SerialName("creado_por") val creadoPor: String = "",
    @SerialName("fecha_creacion") val fechaCreacion: String? = null,
    @SerialName("cerrado_por") val cerradoPor: String = "",
    @SerialName("fecha_cierre") val fechaCierre: String? = null,
    @SerialName("articulos_stock") val articulosStock: Int = 0,       // Con stock en la foto de Profit
    @SerialName("articulos_contados") val articulosContados: Int = 0, // Distintos contados
    val conteos: Int = 0                                              // Conteos anotados
)

@Serializable
data class ListaInventariosDto(val inventarios: List<InventarioDto> = emptyList())

/** Un conteo individual (quién contó qué y cuánto). */
@Serializable
data class ConteoDto(
    val id: Long,
    @SerialName("co_art") val coArt: String,
    @SerialName("art_des") val artDes: String = "",
    @SerialName("codigo_leido") val codigoLeido: String = "",
    val cantidad: String = "0",
    val usuario: String = "",
    val fecha: String? = null,
    @SerialName("puede_borrar") val puedeBorrar: Boolean = false
)

@Serializable
data class ListaConteosDto(val conteos: List<ConteoDto> = emptyList())

/** Respuesta de GET .../inventarios/{id}. */
@Serializable
data class DetalleInventarioDto(
    val inventario: InventarioDto,
    @SerialName("mis_conteos") val misConteos: List<ConteoDto> = emptyList(),
    @SerialName("contado_por_mi") val contadoPorMi: Int = 0
)

/** Lo que se manda al anotar: lo escaneado y la cantidad contada. */
@Serializable
data class EntradaConteoDto(
    val codigo: String,
    val cantidad: String                          // Texto: "12" o "2,5" (negativo para corregir)
)

/** Respuesta al anotar. */
@Serializable
data class ResultadoConteoDto(
    val conteo: ConteoDto,
    @SerialName("contado_usuario") val contadoUsuario: String = "0", // Lo que lleva ESTE usuario
    @SerialName("contado_total") val contadoTotal: String = "0"      // Lo que llevan todos
)

/** Un artículo de la comparación contado vs. stock. */
@Serializable
data class DiferenciaDto(
    @SerialName("co_art") val coArt: String,
    @SerialName("art_des") val artDes: String = "",
    val stock: String = "0",
    val contado: String = "0",
    val diferencia: String = "0",                 // + sobra, - falta
    @SerialName("contado_si") val contadoSi: Boolean = true
)

/** Respuesta de GET .../inventarios/{id}/comparacion. */
@Serializable
data class ComparacionDto(
    val inventario: InventarioDto,
    val diferencias: List<DiferenciaDto> = emptyList(),
    @SerialName("articulos_contados") val articulosContados: Int = 0,
    @SerialName("con_diferencia") val conDiferencia: Int = 0,
    val sobrantes: Int = 0,
    val faltantes: Int = 0,
    @SerialName("no_contados") val noContados: Int = 0,
    val pagina: Int = 1,
    val tamano: Int = 50,
    @SerialName("hay_mas") val hayMas: Boolean = false
)

// ---------------------------- Administración ----------------------------

/** Usuario nuevo. */
@Serializable
data class EntradaUsuarioNuevoDto(
    val usuario: String,
    val nombre: String,
    val clave: String,
    @SerialName("usuario_profit") val usuarioProfit: String = "",
    @SerialName("es_administrador") val esAdministrador: Boolean = false
)

/** Cambios de un usuario: lo que vaya en null no se toca. */
@Serializable
data class EntradaUsuarioCambiosDto(
    val nombre: String? = null,
    @SerialName("usuario_profit") val usuarioProfit: String? = null,
    @SerialName("es_administrador") val esAdministrador: Boolean? = null,
    val activo: Boolean? = null
)

@Serializable
data class EntradaClaveDto(val clave: String)

/** Empresa, almacén y permisos de un usuario. Los permisos van por nombre: {"contar_inventario": true, ...}. */
@Serializable
data class EntradaAmbienteDto(
    val almacen: String = "",
    val permisos: Map<String, Boolean>
)

@Serializable
data class EmpresaAppDto(
    @SerialName("co_empresa") val coEmpresa: String,
    val nombre: String = "",
    val activa: Boolean = true
)

@Serializable
data class ListaEmpresasDto(val empresas: List<EmpresaAppDto> = emptyList())

/** Una empresa del usuario, con sus permisos. */
@Serializable
data class AmbienteAdminDto(
    @SerialName("co_empresa") val coEmpresa: String,
    @SerialName("nombre_empresa") val nombreEmpresa: String = "",
    val almacen: String = "",
    val permisos: PermisosDto = PermisosDto()
)

/** Un usuario de la App visto por el administrador. */
@Serializable
data class UsuarioAdminDto(
    val id: Int,
    val usuario: String,
    val nombre: String = "",
    @SerialName("usuario_profit") val usuarioProfit: String = "",
    @SerialName("es_administrador") val esAdministrador: Boolean = false,
    val activo: Boolean = true,
    val bloqueado: Boolean = false,                         // Bloqueado ahora por claves erradas
    @SerialName("bloqueado_hasta") val bloqueadoHasta: String? = null,
    @SerialName("intentos_fallidos") val intentosFallidos: Int = 0,
    @SerialName("ultimo_acceso") val ultimoAcceso: String? = null,
    val ambientes: List<AmbienteAdminDto> = emptyList()
)

@Serializable
data class ListaUsuariosDto(val usuarios: List<UsuarioAdminDto> = emptyList())

/** Un renglón de la bitácora. */
@Serializable
data class RenglonBitacoraDto(
    val id: Long,
    val fecha: String? = null,
    val usuario: String = "",
    @SerialName("co_empresa") val coEmpresa: String = "",
    val accion: String = "",
    val detalle: String = "",
    val ip: String = ""
)

@Serializable
data class PaginaBitacoraDto(
    val renglones: List<RenglonBitacoraDto> = emptyList(),
    val pagina: Int = 1,
    val tamano: Int = 50,
    @SerialName("hay_mas") val hayMas: Boolean = false
)

/** Forma de todos los errores del servidor: {"detalle": "mensaje"}. */
@Serializable
data class ErrorDto(val detalle: String = "")
