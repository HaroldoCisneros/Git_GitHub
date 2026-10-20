// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// =====================================================================
// Usuarios y accesos (base SIGUEC_PURACERAMICA), con la lógica de AppSighecMovil:
//   - Si el usuario no existe o la clave está mal, el mensaje es el MISMO.
//   - 5 claves erradas seguidas = bloqueado 15 minutos.
//   - Solo entra si está activo y tiene ambiente en alguna empresa activa.
// La hora del bloqueo es la del SERVIDOR (GETDATE()), no la del teléfono.
// =====================================================================

/** Acciones que se anotan en la bitácora. */
object Acciones {
    const val ENTRADA = "ENTRADA"
    const val CLAVE_ERRADA = "CLAVE_ERRADA"
    const val BLOQUEO = "BLOQUEO"
    const val SINCRONIZAR = "SINCRONIZAR"        // Bajó los artículos de Profit al teléfono
    const val ABRIR_INVENTARIO = "ABRIR_INVENTARIO" // Abrió en el teléfono un inventario físico de Profit
    const val ENVIAR_TOMA = "ENVIAR_TOMA"        // Envió lo contado a la toma física de Profit
    const val ADMIN = "ADMIN"                    // Cambios de usuarios y permisos
}

private const val INTENTOS_MAXIMOS = 5
private const val MINUTOS_BLOQUEO = 15
private const val MENSAJE_INCORRECTO = "Usuario o clave incorrectos."

// "a.*": si en el futuro se agregan permisos, la entrada no falla con una base vieja
private const val SQL_USUARIO = """SELECT id, usuario, nombre, clave_hash, usuario_profit, es_administrador, activo,
    intentos_fallidos, bloqueado_hasta, CASE WHEN bloqueado_hasta > GETDATE() THEN 1 ELSE 0 END AS bloqueado,
    CONVERT(varchar(5), bloqueado_hasta, 108) AS hora_desbloqueo
    FROM dbo.usuarios WHERE usuario = ?"""
private const val SQL_AMBIENTES = """SELECT a.*, e.nombre FROM dbo.ambientes a
    JOIN dbo.empresas e ON e.co_empresa = a.co_empresa
    WHERE a.usuario_id = ? AND a.activo = 1 AND e.activa = 1 ORDER BY e.nombre"""
private const val SQL_FALLO = """UPDATE dbo.usuarios SET intentos_fallidos = ?,
    bloqueado_hasta = CASE WHEN ? = 1 THEN DATEADD(minute, ?, GETDATE()) ELSE NULL END WHERE id = ?"""
private const val SQL_ACCESO =
    "UPDATE dbo.usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_acceso = GETDATE() WHERE id = ?"
private const val SQL_BITACORA = """INSERT INTO dbo.bitacora (fecha, usuario_id, usuario, co_empresa, accion, detalle, dispositivo)
    VALUES (GETDATE(), ?, ?, ?, ?, ?, ?)"""
private const val SQL_PARAMETROS = "SELECT clave, valor FROM dbo.parametros"

/** Usuario + clave correctos: la sesión y el hash (para poder entrar después sin WiFi). */
data class Entrada(val sesion: SesionDto, val claveHash: String)

/** Lee una fila de ambientes como [AmbienteDto]. */
internal fun ambienteDesdeFila(f: Map<String, Any?>) = AmbienteDto(
    coEmpresa = f.texto("co_empresa"),
    nombre = f.texto("nombre"),
    almacen = f.texto("almacen"),
    permisos = PermisosDto(
        consultarArticulos = f.logico("puede_consultar_articulos"),
        sincronizarArticulos = f.logico("puede_sincronizar_articulos"),
        contarInventario = f.logico("puede_contar_inventario"),
        cerrarInventario = f.logico("puede_cerrar_inventario")
    )
)

/** Anota en la bitácora (dentro de la transacción de la operación). */
internal fun Conexion.anotar(accion: String, usuarioId: Int?, usuario: String, coEmpresa: String, detalle: String,
                             dispositivo: String) {
    ejecutar(SQL_BITACORA, usuarioId, usuario.take(30), coEmpresa.ifEmpty { null }, accion,
        detalle.take(500).ifEmpty { null }, dispositivo.take(100))
}

/**
 * Entrar, revisar permisos y leer los parámetros, contra la base de la App.
 * @param bd conexión directa al SQL Server
 * @param dispositivo modelo del teléfono (queda en la bitácora)
 */
class RepositorioAcceso(private val bd: BaseDatos, val dispositivo: String) {

    /**
     * "Probar conexión" y "Guardar" en Configuración: entra a la base de la App, revisa que la
     * base de Profit exista, que el usuario de SQL tenga acceso y que sea una empresa de Profit.
     * Si la empresa no está registrada en la App, la registra y se la da a los administradores
     * con todos los permisos (así no hay que hacerlo a mano en SQL).
     */
    suspend fun probar(baseProfit: String): Resultado<String> = bd.usar { cx ->
        val base = baseProfit.trim().uppercase()
        val f = cx.consultarUno("SELECT CAST(SERVERPROPERTY('ProductVersion') AS varchar(30)) AS version, " +
            "DB_ID(?) AS id, HAS_DBACCESS(?) AS acceso", base, base)
        if (f?.get("id") == null) throw ErrorApp("La base de Profit $base no existe en el servidor.", TipoError.NO_ENCONTRADO)
        if (f.entero("acceso") != 1) {
            throw ErrorApp("El usuario de SQL no tiene acceso a la base $base: ejecute sql/03_permisos_usuario_sql.sql " +
                "con USE $base.", TipoError.PERMISO)
        }
        if (cx.consultarUno("SELECT OBJECT_ID(?) AS id", "$base.dbo.saArticulo")?.get("id") == null) {
            throw ErrorApp("La base $base no es una empresa de Profit 2K12 (no tiene saArticulo) o el usuario de SQL " +
                "no tiene permiso de leerla (sql/03).", TipoError.NO_ENCONTRADO)
        }
        // Empresa y permisos de los administradores en la App (solo si faltan)
        val nueva = cx.consultarUno("SELECT 1 AS hay FROM dbo.empresas WHERE co_empresa = ?", base) == null
        if (nueva) cx.ejecutar("INSERT INTO dbo.empresas (co_empresa, nombre) VALUES (?, ?)", base, base)
        cx.ejecutar(SQL_AMBIENTE_ADMINISTRADORES, base, base)
        "Conexión correcta (SQL Server ${f.texto("version")}): base de la App ${bd.baseApp} y empresa de Profit $base" +
            if (nueva) ". Empresa $base registrada en la App para los administradores." else "."
    }

    /**
     * Monedas de Profit (saMoneda: co_mone, mone_des, cambio) de la base de Profit de
     * Configuración, para elegir la del resultado de inventario.
     */
    suspend fun monedas(baseProfit: String): Resultado<List<MonedaDto>> = bd.usar(baseProfit.trim()) { cx ->
        cx.consultar("SELECT RTRIM(co_mone) AS co_mone, RTRIM(mone_des) AS mone_des, cambio FROM dbo.saMoneda ORDER BY co_mone")
            .map { MonedaDto(it.texto("co_mone"), it.texto("mone_des"), it.decimal("cambio").stripTrailingZeros().toPlainString()) }
            .ifEmpty { throw ErrorApp("La tabla saMoneda de ${baseProfit.trim()} no tiene monedas.", TipoError.NO_ENCONTRADO) }
    }

    /**
     * Usuario + clave.
     * El error se lanza DESPUÉS de grabar el contador de fallos y la bitácora: por eso
     * el bloque devuelve el mensaje en vez de lanzar ErrorApp (lanzarlo desharía todo).
     */
    suspend fun entrar(usuario: String, clave: String): Resultado<Entrada> {
        val codigo = usuario.trim().uppercase()
        if (codigo.isEmpty() || clave.isEmpty()) return Resultado.Error("Escriba el usuario y la clave.", TipoError.VALIDACION)
        val r = bd.usar { cx ->
            val f = cx.consultarUno(SQL_USUARIO, codigo)
            when {
                f == null -> {
                    Seguridad.claveCoincide(clave, HASH_RELLENO) // Tarda lo mismo que con un usuario real
                    cx.anotar(Acciones.CLAVE_ERRADA, null, codigo, "", "Usuario no existe", dispositivo)
                    MENSAJE_INCORRECTO
                }
                f.logico("bloqueado") ->
                    "Usuario bloqueado por varias claves erradas. Intente después de las ${f.texto("hora_desbloqueo")}."
                !Seguridad.claveCoincide(clave, f.texto("clave_hash")) -> {
                    val id = f.entero("id")
                    var intentos = f.entero("intentos_fallidos") + 1
                    val bloquear = intentos >= INTENTOS_MAXIMOS
                    if (bloquear) {
                        intentos = 0
                        cx.anotar(Acciones.BLOQUEO, id, codigo, "", "Bloqueado $MINUTOS_BLOQUEO minutos", dispositivo)
                    }
                    cx.ejecutar(SQL_FALLO, intentos, if (bloquear) 1 else 0, MINUTOS_BLOQUEO, id)
                    cx.anotar(Acciones.CLAVE_ERRADA, id, codigo, "", "", dispositivo)
                    MENSAJE_INCORRECTO
                }
                !f.logico("activo") -> "El usuario está inactivo. Hable con el administrador."
                else -> {
                    val id = f.entero("id")
                    val ambientes = cx.consultar(SQL_AMBIENTES, id).map(::ambienteDesdeFila)
                    if (ambientes.isEmpty()) {
                        "El usuario no tiene acceso a ninguna empresa. Hable con el administrador."
                    } else {
                        cx.ejecutar(SQL_ACCESO, id)
                        cx.anotar(Acciones.ENTRADA, id, codigo, "", dispositivo, dispositivo)
                        Entrada(
                            SesionDto(UsuarioDto(id, codigo, f.texto("nombre"), f.logico("es_administrador"),
                                f.texto("usuario_profit")), ambientes),
                            f.texto("clave_hash")
                        )
                    }
                }
            }
        }
        return when (r) {
            is Resultado.Error -> r
            is Resultado.Exito -> when (val v = r.valor) {
                is Entrada -> Resultado.Exito(v)
                else -> Resultado.Error(v.toString(), TipoError.CLAVE)
            }
        }
    }

    /**
     * Antes de una operación con Profit (sincronizar, traer, enviar): el permiso se vuelve a
     * leer del servidor (si el administrador se lo quitó o lo desactivó, aplica de una vez).
     * Se usa DENTRO de la transacción de la operación.
     */
    internal fun exigirPermiso(cx: Conexion, usuario: UsuarioDto, coEmpresa: String, permiso: (PermisosDto) -> Boolean,
                               mensaje: String): AmbienteDto {
        val activo = cx.consultarUno("SELECT activo FROM ${bd.baseApp}.dbo.usuarios WHERE id = ?", usuario.id)
        if (activo == null || !activo.logico("activo")) {
            throw ErrorApp("El usuario está inactivo. Hable con el administrador.", TipoError.PERMISO)
        }
        val ambiente = cx.consultar(SQL_AMBIENTES.replace("dbo.", "${bd.baseApp}.dbo."), usuario.id)
            .map(::ambienteDesdeFila).firstOrNull { it.coEmpresa.equals(coEmpresa, ignoreCase = true) }
            ?: throw ErrorApp("No tiene acceso a la empresa $coEmpresa.", TipoError.PERMISO)
        if (!permiso(ambiente.permisos)) throw ErrorApp(mensaje, TipoError.PERMISO)
        return ambiente
    }

    /** Anota en la bitácora de la App desde una conexión a OTRA base (la de Profit). */
    internal fun anotarDesde(cx: Conexion, accion: String, usuario: UsuarioDto, coEmpresa: String, detalle: String) {
        cx.ejecutar(SQL_BITACORA.replace("dbo.bitacora", "${bd.baseApp}.dbo.bitacora"), usuario.id, usuario.usuario,
            coEmpresa, accion, detalle.take(500), dispositivo.take(100))
    }

    /** Los nombres de Profit 2K12 (de fábrica + los de la tabla parametros), desde otra base. */
    internal fun configProfit(cx: Conexion): ConfigProfit {
        val parametros = cx.consultar(SQL_PARAMETROS.replace("dbo.", "${bd.baseApp}.dbo."))
            .associate { it.texto("clave").lowercase() to it.texto("valor") }
        val config = ConfigProfit.desdeParametros(parametros)
        config.validar()?.let { throw ErrorApp(it, TipoError.SERVIDOR) }
        return config
    }

    private companion object {
        // Los administradores reciben la empresa con todos los permisos (solo los que no la tienen)
        const val SQL_AMBIENTE_ADMINISTRADORES = """INSERT INTO dbo.ambientes (usuario_id, co_empresa,
            puede_consultar_articulos, puede_sincronizar_articulos, puede_contar_inventario, puede_cerrar_inventario)
            SELECT u.id, ?, 1, 1, 1, 1 FROM dbo.usuarios u WHERE u.es_administrador = 1
              AND NOT EXISTS (SELECT 1 FROM dbo.ambientes a WHERE a.usuario_id = u.id AND a.co_empresa = ?)"""

        // Hash de relleno: si el usuario no existe se calcula igual un PBKDF2 (misma demora)
        val HASH_RELLENO: String by lazy { Seguridad.cifrarClave("relleno-sin-uso") }
    }
}
