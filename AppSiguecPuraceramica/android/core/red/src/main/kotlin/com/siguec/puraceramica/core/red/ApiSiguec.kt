// MÓDULO :core:red
// Paquete de la comunicación con la API
package com.siguec.puraceramica.core.red

// Respuesta HTTP completa (se usa en los DELETE, que no traen cuerpo)
import retrofit2.Response
// @Body = el objeto se manda como JSON en el cuerpo de la petición
import retrofit2.http.Body
// Verbos HTTP: cada anotación dice qué tipo de petición es
import retrofit2.http.DELETE // Borrar (cerrar la sesión, borrar un conteo)
import retrofit2.http.GET    // Consultar
import retrofit2.http.PATCH  // Modificar una parte de un registro (usuario)
import retrofit2.http.POST   // Crear (entrar, preparar inventario, anotar un conteo)
import retrofit2.http.PUT    // Reemplazar (los permisos de un usuario en una empresa)
// @Path = valor que va DENTRO de la ruta: /inventarios/{id}
import retrofit2.http.Path
// @Query = valor que va al final: ?texto=ceramica&pagina=1
import retrofit2.http.Query

/**
 * Las rutas de la API (servidor/api/rutas) escritas como funciones de Kotlin.
 *
 * Retrofit crea el código que arma la petición HTTP, la envía y convierte el
 * JSON de la respuesta en los DTO (Dto.kt). Ver ClienteSiguec.kt, donde se crea.
 *
 * - Las rutas NO empiezan con "/": así funcionan aunque la API esté en una
 *   subcarpeta del dominio (ej. https://miempresa.com/siguec/).
 * - "suspend" = la función espera la respuesta sin congelar la pantalla.
 * - "internal" = solo este módulo la usa; las pantallas usan Repositorios.kt.
 */
internal interface ApiSiguec {

    // ============================ Servidor ============================

    /** ¿La API está encendida? (no necesita sesión). GET /api/v1/salud */
    @GET("api/v1/salud")
    suspend fun salud(): SaludDto

    // ============================ Sesión ============================

    /** Entrar con usuario y clave: devuelve el token y las empresas del usuario. */
    @POST("api/v1/sesion")
    suspend fun entrar(@Body entrada: EntradaSesionDto): SesionDto

    /** ¿Sigue vigente el token guardado? (al abrir la App) */
    @GET("api/v1/sesion")
    suspend fun sesion(): SesionDto

    /** Cerrar la sesión: el token deja de servir. Responde 204 (sin cuerpo). */
    @DELETE("api/v1/sesion")
    suspend fun salir(): Response<Unit>

    // ============================ Artículos ============================

    /**
     * Consulta de artículos de la copia sincronizada: código, descripción y stock contado.
     * Ejemplo: GET api/v1/empresas/PURACERAMICA/articulos?texto=ceramica&inventario_id=3&pagina=1
     */
    @GET("api/v1/empresas/{empresa}/articulos")
    suspend fun articulos(
        @Path("empresa") empresa: String,                   // Base de Profit 2K12 (co_empresa)
        @Query("texto") texto: String,                      // Código, descripción o código de barra ("*" = comodín)
        @Query("inventario_id") inventarioId: Int?,         // De qué inventario sale el contado (null = no se manda)
        @Query("solo_contados") soloContados: Boolean,      // true = solo los ya contados
        @Query("pagina") pagina: Int,
        @Query("tamano") tamano: Int
    ): PaginaArticulosDto

    /** El artículo de un código escaneado (404 si no está en la copia). */
    @GET("api/v1/empresas/{empresa}/articulos/por-codigo")
    suspend fun porCodigo(
        @Path("empresa") empresa: String,
        @Query("codigo") codigo: String,
        @Query("inventario_id") inventarioId: Int?
    ): ArticuloDto

    /** Baja los artículos de Profit (saArticulo) a la copia de la App. */
    @POST("api/v1/empresas/{empresa}/articulos/sincronizar")
    suspend fun sincronizar(@Path("empresa") empresa: String): SincronizacionDto

    /** La última sincronización (null si nunca se ha hecho). */
    @GET("api/v1/empresas/{empresa}/articulos/sincronizacion")
    suspend fun ultimaSincronizacion(@Path("empresa") empresa: String): UltimaSincronizacionDto

    // ============================ Inventarios ============================

    /** Almacenes de Profit y su inventario abierto (si lo tienen). */
    @GET("api/v1/empresas/{empresa}/almacenes")
    suspend fun almacenes(@Path("empresa") empresa: String): ListaAlmacenesDto

    /** Inventarios de la empresa (los últimos 50, abiertos primero). */
    @GET("api/v1/empresas/{empresa}/inventarios")
    suspend fun inventarios(
        @Path("empresa") empresa: String,
        @Query("solo_abiertos") soloAbiertos: Boolean
    ): ListaInventariosDto

    /** Prepara (abre) el inventario de un almacén con la foto de su stock en Profit. */
    @POST("api/v1/empresas/{empresa}/inventarios")
    suspend fun prepararInventario(
        @Path("empresa") empresa: String,
        @Body entrada: EntradaInventarioDto
    ): InventarioDto

    /** Datos del inventario y los últimos conteos del usuario. */
    @GET("api/v1/empresas/{empresa}/inventarios/{id}")
    suspend fun inventario(@Path("empresa") empresa: String, @Path("id") id: Int): DetalleInventarioDto

    /** Cierra el inventario: ya no se puede contar. */
    @POST("api/v1/empresas/{empresa}/inventarios/{id}/cerrar")
    suspend fun cerrarInventario(@Path("empresa") empresa: String, @Path("id") id: Int): InventarioDto

    /** Stock contado vs. la foto del stock de Profit. */
    @GET("api/v1/empresas/{empresa}/inventarios/{id}/comparacion")
    suspend fun comparacion(
        @Path("empresa") empresa: String,
        @Path("id") id: Int,
        @Query("solo_diferencias") soloDiferencias: Boolean,
        @Query("incluir_no_contados") incluirNoContados: Boolean,
        @Query("pagina") pagina: Int,
        @Query("tamano") tamano: Int
    ): ComparacionDto

    // ============================ Conteos ============================

    /** Anota lo escaneado: POST .../inventarios/3/conteos {"codigo": "...", "cantidad": "6"} */
    @POST("api/v1/empresas/{empresa}/inventarios/{id}/conteos")
    suspend fun anotar(
        @Path("empresa") empresa: String,
        @Path("id") id: Int,
        @Body entrada: EntradaConteoDto
    ): ResultadoConteoDto

    /** Los últimos conteos propios (deTodos = true solo quien prepara). */
    @GET("api/v1/empresas/{empresa}/inventarios/{id}/conteos")
    suspend fun conteos(
        @Path("empresa") empresa: String,
        @Path("id") id: Int,
        @Query("de_todos") deTodos: Boolean,
        @Query("cuantos") cuantos: Int
    ): ListaConteosDto

    /** Borra un conteo equivocado. Responde 204. */
    @DELETE("api/v1/empresas/{empresa}/inventarios/{id}/conteos/{conteo}")
    suspend fun borrarConteo(
        @Path("empresa") empresa: String,
        @Path("id") id: Int,
        @Path("conteo") conteo: Long
    ): Response<Unit>

    // ============================ Administración ============================

    @GET("api/v1/admin/usuarios")
    suspend fun usuarios(): ListaUsuariosDto

    @POST("api/v1/admin/usuarios")
    suspend fun crearUsuario(@Body entrada: EntradaUsuarioNuevoDto): UsuarioAdminDto

    @GET("api/v1/admin/usuarios/{id}")
    suspend fun usuario(@Path("id") id: Int): UsuarioAdminDto

    @PATCH("api/v1/admin/usuarios/{id}")
    suspend fun cambiarUsuario(@Path("id") id: Int, @Body entrada: EntradaUsuarioCambiosDto): UsuarioAdminDto

    /** Clave nueva (cierra las sesiones del usuario). Responde 204. */
    @POST("api/v1/admin/usuarios/{id}/clave")
    suspend fun cambiarClave(@Path("id") id: Int, @Body entrada: EntradaClaveDto): Response<Unit>

    @POST("api/v1/admin/usuarios/{id}/desbloquear")
    suspend fun desbloquear(@Path("id") id: Int): UsuarioAdminDto

    /** Da o cambia el acceso a una empresa. PUT .../admin/usuarios/3/ambientes/PURACERAMICA */
    @PUT("api/v1/admin/usuarios/{id}/ambientes/{empresa}")
    suspend fun guardarAmbiente(
        @Path("id") id: Int,
        @Path("empresa") empresa: String,
        @Body entrada: EntradaAmbienteDto
    ): UsuarioAdminDto

    @DELETE("api/v1/admin/usuarios/{id}/ambientes/{empresa}")
    suspend fun quitarAmbiente(@Path("id") id: Int, @Path("empresa") empresa: String): UsuarioAdminDto

    @GET("api/v1/admin/empresas")
    suspend fun empresas(): ListaEmpresasDto

    /** Bitácora, lo más reciente primero. Los filtros en null no se mandan. */
    @GET("api/v1/admin/bitacora")
    suspend fun bitacora(
        @Query("usuario") usuario: String?,
        @Query("co_empresa") coEmpresa: String?,
        @Query("accion") accion: String?,
        @Query("desde") desde: String?,
        @Query("hasta") hasta: String?,
        @Query("pagina") pagina: Int,
        @Query("tamano") tamano: Int
    ): PaginaBitacoraDto
}
