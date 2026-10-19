// MÓDULO :core:red
// Paquete de la comunicación con la API
package com.siguec.puraceramica.core.red

// =====================================================================
// Repositorios: lo ÚNICO que usan las pantallas para hablar con el servidor.
// Cada función llama a una ruta de ApiSiguec a través de cliente.llamar { },
// que convierte cualquier falla (sin internet, 401, 409...) en un
// Resultado.Error con un mensaje en español. Así las pantallas nunca ven
// excepciones: solo Resultado.Exito o Resultado.Error.
// =====================================================================

/**
 * Sesión en el servidor: probar la conexión, entrar, revisar y salir.
 * @param cliente cliente HTTP de la dirección del servidor
 */
class RepositorioSesionRemota(private val cliente: ClienteSiguec) {

    /** Prueba si en esa dirección hay un servidor de AppSiguecPuraceramica. */
    suspend fun probarServidor(): Resultado<SaludDto> =
        // avisarSesionVencida = false: aquí todavía no hay sesión que vencer
        cliente.llamar(avisarSesionVencida = false) { salud() }

    /** Entrar. Si sale bien, el [SesionDto] trae el token para guardar. */
    suspend fun entrar(usuario: String, clave: String, dispositivo: String): Resultado<SesionDto> =
        // En el login un 401 significa "clave errada", no "sesión vencida"
        cliente.llamar(avisarSesionVencida = false) {
            // Se quitan los espacios del usuario; el modelo del teléfono se recorta a 100 letras
            entrar(EntradaSesionDto(usuario.trim(), clave, dispositivo.take(100)))
        }

    /** ¿El token guardado sigue sirviendo? Devuelve los datos actualizados (permisos). */
    suspend fun sesion(): Resultado<SesionDto> = cliente.llamar(avisarSesionVencida = false) { sesion() }

    /** Cierra la sesión en el servidor. */
    suspend fun salir(): Resultado<Unit> = cliente.llamar(avisarSesionVencida = false) {
        salir() // DELETE /api/v1/sesion
        Unit    // No hay nada que devolver
    }
}

/**
 * Artículos de la COPIA sincronizada (saArticulo de Profit 2K12) de UNA empresa.
 * @param cliente cliente HTTP con el token de la sesión
 * @param empresa co_empresa del ambiente elegido (nombre de la base de Profit)
 */
class RepositorioArticulos(private val cliente: ClienteSiguec, val empresa: String) {

    /**
     * Consulta: código, descripción y stock contado. El "*" funciona como comodín.
     * @param inventarioId de qué inventario sale el stock contado (null = no se muestra)
     * @param soloContados true = solo los que ya tienen conteo en ese inventario
     */
    suspend fun buscar(
        texto: String,
        inventarioId: Int? = null,
        soloContados: Boolean = false,
        pagina: Int = 1,
        tamano: Int = TAMANO_PAGINA
    ): Resultado<PaginaArticulosDto> = cliente.llamar {
        articulos(empresa, texto.trim(), inventarioId, soloContados && inventarioId != null, pagina, tamano)
    }

    /** El artículo de un código escaneado (Resultado.Error NO_ENCONTRADO si no está). */
    suspend fun porCodigo(codigo: String, inventarioId: Int? = null): Resultado<ArticuloDto> =
        cliente.llamar { porCodigo(empresa, codigo.trim(), inventarioId) }

    /** Baja los artículos de Profit a la copia (permiso "sincronizar artículos"). Puede tardar. */
    suspend fun sincronizar(): Resultado<SincronizacionDto> = cliente.llamar { sincronizar(empresa) }

    /** La última sincronización (null si nunca se ha hecho). */
    suspend fun ultimaSincronizacion(): Resultado<SincronizacionDto?> =
        cliente.llamar { ultimaSincronizacion(empresa).sincronizacion }

    companion object {
        /** Artículos por página. */
        const val TAMANO_PAGINA = 30
    }
}

/**
 * Inventarios por almacén de UNA empresa: preparar, consultar, cerrar y comparar.
 * Lo contado vive en SIGUEC_PURACERAMICA: nunca cambia el stock de Profit.
 */
class RepositorioInventarios(private val cliente: ClienteSiguec, val empresa: String) {

    /** Almacenes de Profit (para preparar) con su inventario abierto. */
    suspend fun almacenes(): Resultado<List<AlmacenDto>> = cliente.llamar { almacenes(empresa).almacenes }

    /** Los últimos 50 inventarios (abiertos primero). */
    suspend fun lista(soloAbiertos: Boolean = false): Resultado<List<InventarioDto>> =
        cliente.llamar { inventarios(empresa, soloAbiertos).inventarios }

    /** Prepara (abre) el inventario del almacén: toma la foto del stock de Profit. */
    suspend fun preparar(coAlma: String, descripcion: String = ""): Resultado<InventarioDto> =
        cliente.llamar { prepararInventario(empresa, EntradaInventarioDto(coAlma.trim(), descripcion.trim())) }

    /** Datos del inventario y los últimos conteos del usuario. */
    suspend fun detalle(id: Int): Resultado<DetalleInventarioDto> = cliente.llamar { inventario(empresa, id) }

    /** Cierra el inventario. */
    suspend fun cerrar(id: Int): Resultado<InventarioDto> = cliente.llamar { cerrarInventario(empresa, id) }

    /** Stock contado vs. la foto del stock (permiso "preparar inventario"). */
    suspend fun comparacion(
        id: Int,
        soloDiferencias: Boolean = true,
        incluirNoContados: Boolean = true,
        pagina: Int = 1,
        tamano: Int = TAMANO_PAGINA
    ): Resultado<ComparacionDto> = cliente.llamar {
        comparacion(empresa, id, soloDiferencias, incluirNoContados, pagina, tamano)
    }

    companion object {
        const val TAMANO_PAGINA = 50
    }
}

/**
 * Conteos individuales de UN inventario abierto (escaneando el código de barras).
 * Cada anotación queda a nombre del usuario y SE SUMA al stock contado.
 */
class RepositorioConteos(private val cliente: ClienteSiguec, val empresa: String) {

    /** Anota lo escaneado. @param codigo co_art, modelo, referencia o código de barra */
    suspend fun anotar(inventarioId: Int, codigo: String, cantidad: String): Resultado<ResultadoConteoDto> =
        cliente.llamar { anotar(empresa, inventarioId, EntradaConteoDto(codigo.trim(), cantidad.trim())) }

    /** Los últimos conteos propios (deTodos = true solo quien prepara). */
    suspend fun ultimos(inventarioId: Int, deTodos: Boolean = false, cuantos: Int = 50): Resultado<List<ConteoDto>> =
        cliente.llamar { conteos(empresa, inventarioId, deTodos, cuantos).conteos }

    /** Borra un conteo equivocado. */
    suspend fun borrar(inventarioId: Int, conteo: Long): Resultado<Unit> = cliente.llamar {
        borrarConteo(empresa, inventarioId, conteo)
        Unit
    }
}

/**
 * Administración de usuarios, permisos y bitácora (solo administradores).
 * No depende de la empresa elegida: los usuarios son de toda la App.
 */
class RepositorioAdmin(private val cliente: ClienteSiguec) {

    suspend fun usuarios(): Resultado<List<UsuarioAdminDto>> = cliente.llamar { usuarios().usuarios }

    suspend fun usuario(id: Int): Resultado<UsuarioAdminDto> = cliente.llamar { usuario(id) }

    suspend fun crear(entrada: EntradaUsuarioNuevoDto): Resultado<UsuarioAdminDto> =
        cliente.llamar { crearUsuario(entrada) }

    /** Solo cambia lo que no venga en null. */
    suspend fun cambiar(id: Int, cambios: EntradaUsuarioCambiosDto): Resultado<UsuarioAdminDto> =
        cliente.llamar { cambiarUsuario(id, cambios) }

    suspend fun cambiarClave(id: Int, clave: String): Resultado<Unit> = cliente.llamar {
        cambiarClave(id, EntradaClaveDto(clave))
        Unit
    }

    suspend fun desbloquear(id: Int): Resultado<UsuarioAdminDto> = cliente.llamar { desbloquear(id) }

    suspend fun guardarAmbiente(id: Int, coEmpresa: String, entrada: EntradaAmbienteDto): Resultado<UsuarioAdminDto> =
        cliente.llamar { guardarAmbiente(id, coEmpresa, entrada) }

    suspend fun quitarAmbiente(id: Int, coEmpresa: String): Resultado<UsuarioAdminDto> =
        cliente.llamar { quitarAmbiente(id, coEmpresa) }

    suspend fun empresas(): Resultado<List<EmpresaAppDto>> = cliente.llamar { empresas().empresas }

    /**
     * Bitácora (lo más reciente primero). Filtros vacíos = todos.
     * @param desde / hasta "AAAA-MM-DD" (hasta incluye todo ese día)
     */
    suspend fun bitacora(
        usuario: String = "",
        coEmpresa: String = "",
        accion: String = "",
        desde: String? = null,
        hasta: String? = null,
        pagina: Int = 1,
        tamano: Int = TAMANO_PAGINA
    ): Resultado<PaginaBitacoraDto> = cliente.llamar {
        bitacora(usuario.trim().ifEmpty { null }, coEmpresa.trim().ifEmpty { null }, accion.trim().ifEmpty { null },
            desde, hasta, pagina, tamano)
    }

    companion object {
        const val TAMANO_PAGINA = 50
    }
}
