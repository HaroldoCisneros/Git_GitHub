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
 *  - entrar()        Con WiFi: contra el SQL Server. Sin WiFi: con la clave guardada de la
 *                    última vez que ese usuario entró CON WiFi en este teléfono.
 *  - restaurar()     Al abrir la App: si quedó una sesión abierta, entra solo.
 *  - elegirEmpresa() La empresa de Profit con la que se trabaja.
 *  - local           La base del teléfono (artículos, inventarios y conteos): sin WiFi.
 *  - profit(), inventarioProfit(), admin()   Lo que necesita WiFi (SQL Server).
 *  - salir()         Cierra la sesión en el teléfono.
 */
class GestorSesion(contexto: Context) {

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

    fun guardarConexion(config: ConfigConexion) {
        almacen.conexion = config
    }

    /** Prueba una configuración (sin guardarla). */
    suspend fun probarConexion(config: ConfigConexion): Resultado<String> =
        RepositorioAcceso(BaseDatos(config), dispositivo).probar()

    private fun bd() = BaseDatos(almacen.conexion)
    private fun acceso() = RepositorioAcceso(bd(), dispositivo)

    // ---------- Entrar y salir ----------

    /**
     * Entra con usuario y clave. Primero intenta con el SQL Server; si no hay WiFi,
     * usa la clave guardada de la última entrada con WiFi en este teléfono.
     */
    suspend fun entrar(usuario: String, clave: String): Resultado<SesionDto> {
        val codigo = usuario.trim().uppercase()
        return when (val r = acceso().entrar(codigo, clave)) {
            is Resultado.Exito -> {
                // Se recuerda el HASH (no la clave) para poder entrar después sin WiFi
                almacen.recordar(codigo, UsuarioRecordado(r.valor.claveHash, r.valor.sesion))
                iniciar(r.valor.sesion)
                Resultado.Exito(r.valor.sesion)
            }
            is Resultado.Error -> when (r.tipo) {
                TipoError.SIN_CONEXION -> entrarSinConexion(codigo, clave, r.mensaje)
                // Inactivo o sin empresas: ya no puede entrar tampoco sin WiFi
                TipoError.CLAVE -> {
                    if (r.mensaje.contains("inactivo") || r.mensaje.contains("ninguna empresa")) almacen.recordar(codigo, null)
                    r
                }
                else -> r
            }
        }
    }

    private suspend fun entrarSinConexion(codigo: String, clave: String, motivo: String): Resultado<SesionDto> {
        val recordado = almacen.recordado(codigo)
            ?: return Resultado.Error("$motivo\nSin WiFi solo pueden entrar los usuarios que ya entraron " +
                "una vez con WiFi en este teléfono.", TipoError.SIN_CONEXION)
        // PBKDF2 tarda ~1 s: fuera del hilo de la pantalla
        val coincide = withContext(Dispatchers.Default) { Seguridad.claveCoincide(clave, recordado.claveHash) }
        if (!coincide) return Resultado.Error("Usuario o clave incorrectos.", TipoError.CLAVE)
        val sesion = recordado.sesion.copy(sinConexion = true)
        iniciar(sesion)
        return Resultado.Exito(sesion)
    }

    /** Al abrir la App: la sesión que quedó abierta (null = hay que entrar). */
    fun restaurar(): SesionDto? = almacen.sesion?.also { iniciar(it) }

    /** Deja la sesión en memoria y elige la empresa si se puede. */
    private fun iniciar(sesion: SesionDto) {
        almacen.sesion = sesion
        almacen.ultimoUsuario = sesion.usuario.usuario
        _sesion.value = sesion
        // La empresa de la vez anterior, si todavía la tiene; o la única que tiene
        val anterior = sesion.ambientes.firstOrNull { it.coEmpresa == almacen.empresa }
        _ambiente.value = anterior ?: sesion.ambientes.singleOrNull()
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

    /** Traer las tomas físicas abiertas y enviar lo contado. */
    fun inventarioProfit(): RepositorioInventarioProfit =
        bd().let { RepositorioInventarioProfit(it, RepositorioAcceso(it, dispositivo)) }

    /** Administración de usuarios (null sin sesión). */
    fun admin(): RepositorioAdmin? = usuario?.let { RepositorioAdmin(bd(), it, dispositivo) }
}
