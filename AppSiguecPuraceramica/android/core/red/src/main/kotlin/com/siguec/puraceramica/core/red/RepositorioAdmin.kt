// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Fechas de la bitácora
import java.time.LocalDate

// =====================================================================
// Administración de usuarios, permisos y bitácora (solo administradores),
// directo contra SIGUEC_PURACERAMICA. Misma lógica de AppSighecMovil:
//   - La clave se guarda con PBKDF2 (se calcula en el teléfono).
//   - Nadie se quita a sí mismo el administrador ni se desactiva.
//   - Cada cambio queda en la bitácora con la acción ADMIN.
// =====================================================================

private val PATRON_USUARIO = Regex("^[A-Z0-9_.\\-]{1,30}$")
private val PATRON_PROFIT = Regex("^[A-Z0-9_.\\-]{0,10}$")
private const val LARGO_NOMBRE = 100

/** Nombres de los permisos (columnas puede_* de ambientes), en el orden de la pantalla. */
val NOMBRES_PERMISOS = listOf("consultar_articulos", "sincronizar_articulos", "contar_inventario", "cerrar_inventario")

private const val SQL_LISTA = """SELECT id, usuario, nombre, usuario_profit, es_administrador, activo, intentos_fallidos,
    CONVERT(varchar(19), bloqueado_hasta, 120) AS bloqueado_hasta,
    CASE WHEN bloqueado_hasta > GETDATE() THEN 1 ELSE 0 END AS bloqueado,
    CONVERT(varchar(19), ultimo_acceso, 120) AS ultimo_acceso FROM dbo.usuarios"""
private const val SQL_AMBIENTES = """SELECT a.*, e.nombre FROM dbo.ambientes a
    JOIN dbo.empresas e ON e.co_empresa = a.co_empresa WHERE a.usuario_id = ? ORDER BY e.nombre"""

/**
 * @param actor el usuario que está usando la App (debe ser administrador)
 */
class RepositorioAdmin(private val bd: BaseDatos, private val actor: UsuarioDto, private val dispositivo: String) {

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    /** Se revisa en la BASE (no en la sesión guardada) que siga siendo administrador. */
    private fun exigirAdmin(cx: Conexion) {
        val f = cx.consultarUno("SELECT es_administrador, activo FROM dbo.usuarios WHERE id = ?", actor.id)
        if (f == null || !f.logico("es_administrador") || !f.logico("activo")) {
            throw ErrorApp("Solo el administrador puede usar esta opción.", TipoError.PERMISO)
        }
    }

    private fun anotar(cx: Conexion, detalle: String, coEmpresa: String = "") =
        cx.anotar(Acciones.ADMIN, actor.id, actor.usuario, coEmpresa, detalle, dispositivo)

    private fun leerUsuario(cx: Conexion, id: Int): UsuarioAdminDto {
        val f = cx.consultarUno("$SQL_LISTA WHERE id = ?", id) ?: throw ErrorApp("Ese usuario no existe.", TipoError.NO_ENCONTRADO)
        return usuarioDesdeFila(cx, f)
    }

    private fun usuarioDesdeFila(cx: Conexion, f: Map<String, Any?>) = UsuarioAdminDto(
        id = f.entero("id"),
        usuario = f.texto("usuario"),
        nombre = f.texto("nombre"),
        usuarioProfit = f.texto("usuario_profit"),
        esAdministrador = f.logico("es_administrador"),
        activo = f.logico("activo"),
        bloqueado = f.logico("bloqueado"),
        bloqueadoHasta = f.texto("bloqueado_hasta").ifEmpty { null },
        intentosFallidos = f.entero("intentos_fallidos"),
        ultimoAcceso = f.texto("ultimo_acceso").ifEmpty { null },
        ambientes = cx.consultar(SQL_AMBIENTES, f.entero("id")).map {
            val a = ambienteDesdeFila(it)
            AmbienteAdminDto(a.coEmpresa, a.nombre, a.almacen, a.permisos)
        }
    )

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    suspend fun usuarios(): Resultado<List<UsuarioAdminDto>> = bd.usar { cx ->
        exigirAdmin(cx)
        cx.consultar("$SQL_LISTA ORDER BY usuario").map { usuarioDesdeFila(cx, it) }
    }

    suspend fun usuario(id: Int): Resultado<UsuarioAdminDto> = bd.usar { cx ->
        exigirAdmin(cx)
        leerUsuario(cx, id)
    }

    suspend fun empresas(): Resultado<List<EmpresaAppDto>> = bd.usar { cx ->
        exigirAdmin(cx)
        cx.consultar("SELECT co_empresa, nombre, activa FROM dbo.empresas ORDER BY nombre")
            .map { EmpresaAppDto(it.texto("co_empresa"), it.texto("nombre"), it.logico("activa")) }
    }

    // ------------------------------------------------------------------
    // Cambios
    // ------------------------------------------------------------------

    suspend fun crear(entrada: EntradaUsuarioNuevoDto): Resultado<UsuarioAdminDto> {
        val codigo = entrada.usuario.trim().uppercase()
        val nombre = entrada.nombre.trim()
        val profit = entrada.usuarioProfit.trim().uppercase()
        val error = when {
            !PATRON_USUARIO.matches(codigo) -> "El usuario admite letras, números, punto, guion y guion bajo (máximo 30)."
            nombre.isEmpty() || nombre.length > LARGO_NOMBRE -> "Escriba el nombre (máximo $LARGO_NOMBRE caracteres)."
            !PATRON_PROFIT.matches(profit) -> "El usuario de Profit admite máximo 10 letras o números."
            else -> Seguridad.validarClaveNueva(entrada.clave)
        }
        if (error != null) return Resultado.Error(error, TipoError.VALIDACION)
        val hash = Seguridad.cifrarClave(entrada.clave) // Antes de abrir la conexión (tarda ~1 s)
        return bd.usar { cx ->
            exigirAdmin(cx)
            if (cx.consultarUno("SELECT id FROM dbo.usuarios WHERE usuario = ?", codigo) != null) {
                throw ErrorApp("El usuario $codigo ya existe.", TipoError.CONFLICTO)
            }
            val id = cx.consultarUno(
                """SET NOCOUNT ON; INSERT INTO dbo.usuarios (usuario, nombre, clave_hash, usuario_profit, es_administrador, activo)
                   VALUES (?, ?, ?, ?, ?, 1); SELECT CAST(SCOPE_IDENTITY() AS int) AS id""",
                codigo, nombre, hash, profit.ifEmpty { null }, entrada.esAdministrador
            )!!.entero("id")
            anotar(cx, "Creó el usuario $codigo" + if (entrada.esAdministrador) " (administrador)" else "")
            leerUsuario(cx, id)
        }
    }

    /** Solo cambia lo que no venga en null. */
    suspend fun cambiar(id: Int, cambios: EntradaUsuarioCambiosDto): Resultado<UsuarioAdminDto> {
        val propio = id == actor.id
        if (propio && cambios.esAdministrador == false) {
            return Resultado.Error("No se puede quitar el administrador a usted mismo.", TipoError.VALIDACION)
        }
        if (propio && cambios.activo == false) return Resultado.Error("No se puede desactivar a usted mismo.", TipoError.VALIDACION)
        return bd.usar { cx ->
            exigirAdmin(cx)
            val actual = leerUsuario(cx, id)
            val nombre = cambios.nombre?.trim() ?: actual.nombre
            val profit = cambios.usuarioProfit?.trim()?.uppercase() ?: actual.usuarioProfit
            val admin = cambios.esAdministrador ?: actual.esAdministrador
            val activo = cambios.activo ?: actual.activo
            if (nombre.isEmpty() || nombre.length > LARGO_NOMBRE) throw ErrorApp("Escriba el nombre (máximo $LARGO_NOMBRE caracteres).")
            if (!PATRON_PROFIT.matches(profit)) throw ErrorApp("El usuario de Profit admite máximo 10 letras o números.")
            cx.ejecutar("UPDATE dbo.usuarios SET nombre = ?, usuario_profit = ?, es_administrador = ?, activo = ? WHERE id = ?",
                nombre, profit.ifEmpty { null }, admin, activo, id)
            val detalle = listOfNotNull(
                "nombre '$nombre'".takeIf { nombre != actual.nombre },
                "usuario Profit '$profit'".takeIf { profit != actual.usuarioProfit },
                ("administrador " + if (admin) "SÍ" else "NO").takeIf { admin != actual.esAdministrador },
                (if (activo) "activado" else "desactivado").takeIf { activo != actual.activo }
            )
            if (detalle.isNotEmpty()) anotar(cx, "Usuario ${actual.usuario}: " + detalle.joinToString(", "))
            leerUsuario(cx, id)
        }
    }

    suspend fun cambiarClave(id: Int, clave: String): Resultado<Unit> {
        Seguridad.validarClaveNueva(clave)?.let { return Resultado.Error(it, TipoError.VALIDACION) }
        val hash = Seguridad.cifrarClave(clave)
        return bd.usar { cx ->
            exigirAdmin(cx)
            val u = leerUsuario(cx, id)
            cx.ejecutar("UPDATE dbo.usuarios SET clave_hash = ?, intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?",
                hash, id)
            anotar(cx, "Cambió la clave de ${u.usuario}")
        }
    }

    suspend fun desbloquear(id: Int): Resultado<UsuarioAdminDto> = bd.usar { cx ->
        exigirAdmin(cx)
        val u = leerUsuario(cx, id)
        cx.ejecutar("UPDATE dbo.usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?", id)
        anotar(cx, "Desbloqueó a ${u.usuario}")
        leerUsuario(cx, id)
    }

    /** Da (o cambia) el acceso a una empresa con su almacén y permisos. */
    suspend fun guardarAmbiente(id: Int, coEmpresa: String, entrada: EntradaAmbienteDto): Resultado<UsuarioAdminDto> {
        val empresa = coEmpresa.trim().uppercase()
        val almacen = entrada.almacen.trim().uppercase()
        if (almacen.length > 6) return Resultado.Error("El almacén admite máximo 6 caracteres.", TipoError.VALIDACION)
        val desconocidos = entrada.permisos.keys - NOMBRES_PERMISOS.toSet()
        if (desconocidos.isNotEmpty()) {
            return Resultado.Error("Permisos no válidos: ${desconocidos.joinToString()}", TipoError.VALIDACION)
        }
        val p = NOMBRES_PERMISOS.map { entrada.permisos[it] == true }
        return bd.usar { cx ->
            exigirAdmin(cx)
            val u = leerUsuario(cx, id)
            if (cx.consultarUno("SELECT co_empresa FROM dbo.empresas WHERE co_empresa = ?", empresa) == null) {
                throw ErrorApp("La empresa $empresa no está registrada.", TipoError.NO_ENCONTRADO)
            }
            val existente = cx.consultarUno("SELECT id FROM dbo.ambientes WHERE usuario_id = ? AND co_empresa = ?", id, empresa)
            if (existente == null) {
                cx.ejecutar("""INSERT INTO dbo.ambientes (usuario_id, co_empresa, almacen, puede_consultar_articulos,
                    puede_sincronizar_articulos, puede_contar_inventario, puede_cerrar_inventario, activo)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1)""", id, empresa, almacen.ifEmpty { null }, p[0], p[1], p[2], p[3])
            } else {
                cx.ejecutar("""UPDATE dbo.ambientes SET almacen = ?, puede_consultar_articulos = ?, puede_sincronizar_articulos = ?,
                    puede_contar_inventario = ?, puede_cerrar_inventario = ?, activo = 1 WHERE id = ?""",
                    almacen.ifEmpty { null }, p[0], p[1], p[2], p[3], existente.entero("id"))
            }
            val activos = NOMBRES_PERMISOS.filterIndexed { i, _ -> p[i] }
            anotar(cx, "Ambiente de ${u.usuario} en $empresa: " + activos.joinToString().ifEmpty { "sin permisos" }, empresa)
            leerUsuario(cx, id)
        }
    }

    suspend fun quitarAmbiente(id: Int, coEmpresa: String): Resultado<UsuarioAdminDto> = bd.usar { cx ->
        exigirAdmin(cx)
        val u = leerUsuario(cx, id)
        val empresa = coEmpresa.trim().uppercase()
        if (cx.ejecutar("DELETE FROM dbo.ambientes WHERE usuario_id = ? AND co_empresa = ?", id, empresa) == 0) {
            throw ErrorApp("${u.usuario} no tiene acceso a $empresa.", TipoError.NO_ENCONTRADO)
        }
        anotar(cx, "Quitó a ${u.usuario} la empresa $empresa", empresa)
        leerUsuario(cx, id)
    }

    // ------------------------------------------------------------------
    // Bitácora
    // ------------------------------------------------------------------

    /**
     * Lo más reciente primero. Filtros vacíos = todos.
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
    ): Resultado<PaginaBitacoraDto> = bd.usar { cx ->
        exigirAdmin(cx)
        val p = maxOf(1, pagina)
        val t = tamano.coerceIn(1, 100)
        val inicio = desde?.let { LocalDate.parse(it).atStartOfDay() }
        val fin = hasta?.let { LocalDate.parse(it).plusDays(1).atStartOfDay() }
        // ROW_NUMBER(): funciona desde SQL Server 2008 (OFFSET/FETCH no)
        val filas = cx.consultar(
            """SET NOCOUNT ON; DECLARE @u varchar(30) = ?, @e varchar(30) = ?, @a varchar(30) = ?, @d datetime = ?, @h datetime = ?;
               SELECT TOP (?) * FROM (
                 SELECT ROW_NUMBER() OVER (ORDER BY b.id DESC) AS fila, b.id, CONVERT(varchar(19), b.fecha, 120) AS fecha,
                        b.usuario, b.co_empresa, b.accion, b.detalle, b.dispositivo FROM dbo.bitacora b
                 WHERE (@u = '' OR b.usuario = @u) AND (@e = '' OR b.co_empresa = @e) AND (@a = '' OR b.accion = @a)
                   AND (@d IS NULL OR b.fecha >= @d) AND (@h IS NULL OR b.fecha < @h)
               ) AS t WHERE t.fila > ? ORDER BY t.fila""",
            usuario.trim().uppercase(), coEmpresa.trim().uppercase(), accion.trim().uppercase(), inicio, fin, t + 1, (p - 1) * t
        )
        PaginaBitacoraDto(
            renglones = filas.take(t).map {
                RenglonBitacoraDto(it.entero("id").toLong(), it.texto("fecha").ifEmpty { null }, it.texto("usuario"),
                    it.texto("co_empresa"), it.texto("accion"), it.texto("detalle"), it.texto("dispositivo"))
            },
            pagina = p, tamano = t, hayMas = filas.size > t
        )
    }

    companion object {
        const val TAMANO_PAGINA = 50
    }
}
