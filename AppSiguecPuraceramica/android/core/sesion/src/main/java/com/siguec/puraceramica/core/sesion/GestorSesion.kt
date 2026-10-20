// MÓDULO :core:sesion
package com.siguec.puraceramica.core.sesion

// Contexto de Android y modelo del teléfono
import android.content.Context
import android.os.Build
// Base del teléfono (módulo :core:local)
import com.siguec.puraceramica.core.local.AlmacenLocal
// Conexión directa al SQL Server (módulo :core:red)
import com.siguec.puraceramica.core.red.AmbienteDto
import com.siguec.puraceramica.core.red.BaseDatos
import com.siguec.puraceramica.core.red.ConfigConexion
import com.siguec.puraceramica.core.red.MonedaDto
import com.siguec.puraceramica.core.red.RepositorioAcceso
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.RepositorioInventarioProfit
import com.siguec.puraceramica.core.red.RepositorioProfit
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.Seguridad
import com.siguec.puraceramica.core.red.SesionDto
import com.siguec.puraceramica.core.red.TipoError
import com.siguec.puraceramica.core.red.UsuarioDto
// Estado observable por las pantallas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Dueño ÚNICO de la sesión en el teléfono. Lo crea :app una sola vez (SiguecApp).
 *
 *  - conexion / guardarConexion() / probarConexion()   Pantalla Configuración.
 *  - entrar()        SIN conectarse: con la clave guardada en el teléfono. Solo la primera
 *                    vez de cada usuario en el teléfono va al SQL Server (WiFi una vez).
 *  - restaurar()     Al abrir la App: si quedó una sesión abierta, entra solo.
 *  - elegirEmpresa() La empresa de Profit con la que se trabaja.
 *  - local           La base del teléfono (artículos, inventarios y conteos): sin WiFi.
 *  - profit(), inventarioProfit(), admin()   Lo que necesita WiFi (SQL Server).
 *  - salir()         Cierra la sesión en el teléfono.
 */
class GestorSesion(contexto: Context) {

    private companion object {
        const val INTENTOS_LOCALES = 5        // Claves erradas en el teléfono antes de bloquear
        const val MINUTOS_BLOQUEO_LOCAL = 15
    }

    private val almacen = AlmacenSesion(contexto.applicationContext)

    /** Base del teléfono: se cuenta aquí, sin WiFi. */
    val local = AlmacenLocal(contexto.applicationContext)

    // Sesión actual (null = nadie ha entrado)
    private val _sesion = MutableStateFlow<SesionDto?>(null)
    val sesion: StateFlow<SesionDto?> = _sesion.asStateFlow()

    // Empresa elegida (null = falta elegirla)
    private val _ambiente = MutableStateFlow<AmbienteDto?>(null)
    val ambiente: StateFlow<AmbienteDto?> = _ambiente.asStateFlow()

    /** "Zebra TC21": queda en la bitácora. */
    val dispositivo: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** Último usuario que entró. */
    val ultimoUsuario: String get() = almacen.ultimoUsuario

    /** El usuario que está usando la App (null sin sesión). */
    val usuario: UsuarioDto? get() = _sesion.value?.usuario

    // ---------- Configuración de la conexión ----------

    /** Datos de conexión guardados. */
    val conexion: ConfigConexion get() = almacen.conexion

    /** ¿Ya se configuró la conexión? (la primera vez se abre Configuración). */
    val conexionConfigurada: Boolean get() = almacen.conexion.completa

    /**
     * Guarda la Configuración. Si se cambió la base de Profit (otra empresa) y hay una sesión
     * abierta, la cierra: al volver a entrar se trabaja con la empresa nueva.
     * @return true = se cerró la sesión
     */
    fun guardarConexion(config: ConfigConexion): Boolean {
        val otraEmpresa = !config.baseProfit.trim().equals(almacen.conexion.baseProfit.trim(), ignoreCase = true)
        almacen.conexion = config
        if (otraEmpresa && _sesion.value != null) {
            salir()
            return true
        }
        return false
    }

    /**
     * Antes de abrir Configuración (cuando ya está configurada): usuario y clave (o huella) de
     * alguien con permiso "Puede cambiar la Configuración" o administrador.
     * SIN conectarse si ese usuario ya entró antes en este teléfono (clave guardada). Solo va al
     * servidor si el teléfono no lo conoce, o si aquí no tiene el permiso (por si se lo dieron).
     */
    suspend fun autorizarConfiguracion(usuario: String, clave: String): Resultado<UsuarioDto> {
        val codigo = usuario.trim().uppercase()
        if (codigo.isEmpty() || clave.isEmpty()) return Resultado.Error("Escriba el usuario y la clave.", TipoError.VALIDACION)
        val recordado = almacen.recordado(codigo)
        val local = recordado != null && claveLocalCorrecta(codigo, clave, recordado)
        if (local && recordado!!.sesion.usuario.autorizaConfiguracion) return Resultado.Exito(recordado.sesion.usuario)
        if (recordado != null && !local && almacen.bloqueoLocal(codigo) > System.currentTimeMillis()) {
            return Resultado.Error(mensajeBloqueo(codigo), TipoError.CLAVE)
        }
        val u = when (val r = acceso().entrar(codigo, clave)) {
            is Resultado.Exito -> {
                almacen.recordar(codigo, UsuarioRecordado(r.valor.claveHash, r.valor.sesion))
                r.valor.sesion.usuario
            }
            is Resultado.Error -> when {
                // Sin servidor: vale lo del teléfono
                local && r.tipo in listOf(TipoError.SIN_CONEXION, TipoError.CONEXION, TipoError.SERVIDOR) ->
                    recordado!!.sesion.usuario
                recordado == null && r.tipo == TipoError.SIN_CONEXION -> return Resultado.Error(
                    "${r.mensaje}\n$codigo nunca ha entrado en este teléfono: la primera vez necesita el WiFi.", r.tipo)
                !local && recordado != null && r.tipo in listOf(TipoError.SIN_CONEXION, TipoError.CONEXION) -> {
                    registrarFalloLocal(codigo)
                    return Resultado.Error("Usuario o clave incorrectos.", TipoError.CLAVE)
                }
                else -> return r
            }
        }
        return if (u.autorizaConfiguracion) Resultado.Exito(u) else Resultado.Error(
            "El usuario $codigo no tiene permiso para cambiar la Configuración. El administrador se lo da en " +
                "Usuarios y permisos.", TipoError.PERMISO)
    }

    // ---------- Huella (por usuario, en este teléfono) ----------

    /** La clave cifrada con la huella de ese usuario (null = no activó la huella aquí). */
    fun huella(usuario: String): HuellaGuardada? = almacen.huella(usuario.trim())

    /** Activa (o quita, con null) la entrada con huella de ese usuario. */
    fun guardarHuella(usuario: String, huella: HuellaGuardada?) {
        almacen.guardarHuella(usuario.trim(), huella)
        if (huella != null) almacen.rechazarHuella(usuario.trim(), false)
    }

    /** ¿Se le ofrece activar la huella después de entrar con la clave? */
    fun ofrecerHuella(usuario: String): Boolean = huella(usuario) == null && !almacen.huellaRechazada(usuario.trim())

    /** Dijo que no: no se le vuelve a preguntar en este teléfono. */
    fun noOfrecerHuella(usuario: String) = almacen.rechazarHuella(usuario.trim(), true)

    /** Prueba una configuración (sin guardarla). */
    suspend fun probarConexion(config: ConfigConexion): Resultado<String> =
        RepositorioAcceso(BaseDatos(config), dispositivo).probar(config.baseProfit)

    /** Monedas de Profit para elegir en Configuración (con lo escrito, sin guardarlo). */
    suspend fun monedas(config: ConfigConexion): Resultado<List<MonedaDto>> =
        RepositorioAcceso(BaseDatos(config), dispositivo).monedas(config.baseProfit)

    /**
     * Prueba de conexión antes de enviar: ¿responde el SQL Server y se puede abrir la base de
     * Profit de la empresa? (no graba nada).
     */
    suspend fun probarServidor(coEmpresa: String): Resultado<Unit> =
        bd().usar(coEmpresa) { cx -> cx.consultarUno("SELECT 1 AS uno"); Unit }

    private fun bd() = BaseDatos(almacen.conexion)
    private fun acceso() = RepositorioAcceso(bd(), dispositivo)

    // ---------- Entrar y salir ----------

    /**
     * Entra con usuario y clave SIN conectarse: el teléfono guarda el HASH de la clave (nunca la
     * clave) y los permisos de cada usuario que ya entró aquí. Solo se va al servidor:
     *  - la PRIMERA vez que ese usuario entra en este teléfono (necesita el WiFi una vez), o
     *  - si la clave no coincide con la guardada (el administrador pudo cambiarla) y hay WiFi.
     * Los permisos se refrescan solos cuando el teléfono se conecta para sincronizar, abrir o
     * enviar (ver actualizarSesion). 5 claves erradas en el teléfono = 15 minutos bloqueado.
     */
    suspend fun entrar(usuario: String, clave: String): Resultado<SesionDto> {
        val codigo = usuario.trim().uppercase()
        if (codigo.isEmpty() || clave.isEmpty()) return Resultado.Error("Escriba el usuario y la clave.", TipoError.VALIDACION)
        val recordado = almacen.recordado(codigo)
            ?: return when (val r = entrarEnServidor(codigo, clave)) {
                // Primera vez en este teléfono
                is Resultado.Error -> if (r.tipo != TipoError.SIN_CONEXION) r else Resultado.Error(
                    "${r.mensaje}\nEs la primera vez que $codigo entra en este teléfono: necesita el WiFi de la " +
                        "empresa UNA vez; después entra y cuenta sin conexión.", TipoError.SIN_CONEXION)
                else -> r
            }
        if (almacen.bloqueoLocal(codigo) > System.currentTimeMillis()) return Resultado.Error(mensajeBloqueo(codigo), TipoError.CLAVE)
        if (claveLocalCorrecta(codigo, clave, recordado)) {
            iniciar(recordado.sesion)
            return Resultado.Exito(recordado.sesion)
        }
        // No coincide con la guardada: si hay WiFi se pregunta al servidor (quizá cambió la clave)
        return when (val r = entrarEnServidor(codigo, clave)) {
            is Resultado.Error -> if (r.tipo == TipoError.SIN_CONEXION || r.tipo == TipoError.CONEXION) {
                registrarFalloLocal(codigo)
                Resultado.Error("Usuario o clave incorrectos.", TipoError.CLAVE)
            } else r
            else -> r
        }
    }

    /** Entrada contra el SQL Server; si entra, se recuerda el HASH para las próximas entradas sin conexión. */
    private suspend fun entrarEnServidor(codigo: String, clave: String): Resultado<SesionDto> =
        when (val r = acceso().entrar(codigo, clave)) {
            is Resultado.Exito -> {
                almacen.recordar(codigo, UsuarioRecordado(r.valor.claveHash, r.valor.sesion))
                almacen.limpiarFallosLocales(codigo)
                iniciar(r.valor.sesion)
                Resultado.Exito(r.valor.sesion)
            }
            is Resultado.Error -> {
                // Inactivo o sin empresas: ya no puede entrar tampoco sin conexión
                if (r.tipo == TipoError.CLAVE && (r.mensaje.contains("inactivo") || r.mensaje.contains("ninguna empresa"))) {
                    almacen.recordar(codigo, null)
                }
                r
            }
        }

    /** Compara con el hash guardado (PBKDF2 tarda ~1 s: fuera del hilo de la pantalla). */
    private suspend fun claveLocalCorrecta(codigo: String, clave: String, recordado: UsuarioRecordado): Boolean {
        val coincide = withContext(Dispatchers.Default) { Seguridad.claveCoincide(clave, recordado.claveHash) }
        if (coincide) almacen.limpiarFallosLocales(codigo)
        return coincide
    }

    private fun registrarFalloLocal(codigo: String) = almacen.sumarFalloLocal(codigo, INTENTOS_LOCALES, MINUTOS_BLOQUEO_LOCAL)

    private fun mensajeBloqueo(codigo: String): String {
        val hora = java.text.SimpleDateFormat("hh:mm a", java.util.Locale("es", "VE")).format(java.util.Date(almacen.bloqueoLocal(codigo)))
        return "Usuario bloqueado en este teléfono por varias claves erradas. Intente después de las $hora."
    }

    /**
     * Trae del servidor los permisos y la clave actuales del usuario que está usando la App.
     * Lo llaman las pantallas DESPUÉS de una operación que ya se conectó (sincronizar, abrir,
     * enviar): así los cambios del administrador llegan al teléfono sin conectarse al entrar.
     * Si falla no pasa nada; si el usuario ya no existe o está inactivo, se cierra la sesión.
     */
    suspend fun actualizarSesion() {
        val actual = _sesion.value ?: return
        val r = acceso().sesionDe(actual.usuario.usuario) as? Resultado.Exito ?: return
        val entrada = r.valor
        if (entrada == null || entrada.sesion.ambientes.isEmpty()) {
            almacen.recordar(actual.usuario.usuario, null)
            salir()
            return
        }
        almacen.recordar(actual.usuario.usuario, UsuarioRecordado(entrada.claveHash, entrada.sesion))
        val empresa = _ambiente.value?.coEmpresa
        iniciar(entrada.sesion)
        // Sigue en la misma empresa si todavía la tiene
        entrada.sesion.ambientes.firstOrNull { it.coEmpresa == empresa }?.let { _ambiente.value = it }
    }

    /** Al abrir la App: la sesión que quedó abierta (null = hay que entrar). */
    fun restaurar(): SesionDto? = almacen.sesion?.also { iniciar(it) }

    /** Deja la sesión en memoria y elige la empresa si se puede. */
    private fun iniciar(sesion: SesionDto) {
        almacen.sesion = sesion
        almacen.ultimoUsuario = sesion.usuario.usuario
        _sesion.value = sesion
        // La empresa de Profit de Configuración; si no la tiene, la de la vez anterior o la única que tiene
        val configurada = sesion.ambientes.firstOrNull { it.coEmpresa.equals(almacen.conexion.baseProfit.trim(), ignoreCase = true) }
        val anterior = sesion.ambientes.firstOrNull { it.coEmpresa == almacen.empresa }
        _ambiente.value = configurada ?: anterior ?: sesion.ambientes.singleOrNull()
    }

    /** Empresa con la que se va a trabajar. */
    fun elegirEmpresa(ambiente: AmbienteDto) {
        almacen.empresa = ambiente.coEmpresa
        _ambiente.value = ambiente
    }

    /** Cierra la sesión en el teléfono (los conteos guardados NO se borran). */
    fun salir() {
        almacen.sesion = null
        _sesion.value = null
        _ambiente.value = null
    }

    // ---------- Último inventario en que se contó ----------

    fun ultimoInventario(): Int? = _ambiente.value?.let { almacen.inventario(it.coEmpresa) }

    fun recordarInventario(id: Int?) {
        _ambiente.value?.let { almacen.guardarInventario(it.coEmpresa, id) }
    }

    // ---------- Lo que necesita WiFi (conexión directa al SQL Server) ----------

    /** Leer los artículos de Profit (sincronizar). */
    fun profit(): RepositorioProfit = bd().let { RepositorioProfit(it, RepositorioAcceso(it, dispositivo)) }

    /** Consultar y abrir los inventarios físicos de Profit, y enviar lo contado. */
    fun inventarioProfit(): RepositorioInventarioProfit =
        bd().let { RepositorioInventarioProfit(it, RepositorioAcceso(it, dispositivo), almacen.conexion.coMone) }

    /** Administración de usuarios (null sin sesión). */
    fun admin(): RepositorioAdmin? = usuario?.let { RepositorioAdmin(bd(), it, dispositivo) }
}
