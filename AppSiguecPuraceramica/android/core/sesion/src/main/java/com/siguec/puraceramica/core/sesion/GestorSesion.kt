// MÓDULO :core:sesion
package com.siguec.puraceramica.core.sesion

// Contexto de Android (para el archivo de preferencias)
import android.content.Context
// Marca y modelo del teléfono
import android.os.Build
// Piezas de la red (módulo :core:red)
import com.siguec.puraceramica.core.red.AmbienteDto
import com.siguec.puraceramica.core.red.ClienteSiguec
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.RepositorioArticulos
import com.siguec.puraceramica.core.red.RepositorioConteos
import com.siguec.puraceramica.core.red.RepositorioInventarios
import com.siguec.puraceramica.core.red.RepositorioSesionRemota
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.SaludDto
import com.siguec.puraceramica.core.red.SesionDto
import com.siguec.puraceramica.core.red.TipoError
// Estado observable por las pantallas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dueño ÚNICO de la sesión en el teléfono. Lo crea :app una sola vez
 * (SiguecApp) y se lo pasa a las pantallas.
 *
 *  - entrar()        Usuario + clave en el servidor elegido; guarda el token.
 *  - restaurar()     Al abrir la App: si hay token guardado y sigue vigente, entra solo.
 *  - elegirEmpresa() El ambiente (empresa de Profit) con el que se trabaja.
 *  - articulos(), inventarios(), conteos()  Repositorios de la empresa elegida.
 *  - salir()         Cierra la sesión en el servidor y borra el token.
 *
 * Si en cualquier momento el servidor dice "sesión vencida" (401), se borra
 * el token y [sesionVencida] pasa a true: :app vuelve a la pantalla de entrada.
 */
class GestorSesion(contexto: Context) {

    // Lo que queda guardado en el teléfono
    private val almacen = AlmacenSesion(contexto.applicationContext)

    // Sesión actual (null = nadie ha entrado)
    private val _sesion = MutableStateFlow<SesionDto?>(null)
    val sesion: StateFlow<SesionDto?> = _sesion.asStateFlow()

    // Empresa elegida (null = falta elegirla)
    private val _ambiente = MutableStateFlow<AmbienteDto?>(null)
    val ambiente: StateFlow<AmbienteDto?> = _ambiente.asStateFlow()

    // true = el servidor rechazó el token: hay que volver a la pantalla de entrada
    private val _sesionVencida = MutableStateFlow(false)
    val sesionVencida: StateFlow<Boolean> = _sesionVencida.asStateFlow()

    // Cliente de la API para la dirección actual (se crea de nuevo si cambia la dirección)
    private var cliente: ClienteSiguec? = null

    /** Dirección del servidor guardada. */
    val urlServidor: String get() = almacen.urlServidor

    /** Último usuario que entró. */
    val ultimoUsuario: String get() = almacen.ultimoUsuario

    /** "Zebra TC21": queda en la bitácora del servidor. */
    private val dispositivo: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /**
     * Cliente para esa dirección.
     * @return null si la dirección no es válida
     */
    private fun cliente(url: String = almacen.urlServidor): ClienteSiguec? {
        val normalizada = ClienteSiguec.normalizarUrl(url) ?: return null
        cliente?.takeIf { it.urlBase == normalizada }?.let { return it }
        return ClienteSiguec(url, token = { almacen.token }, alVencerSesion = ::alVencerSesion)
            .also { cliente = it }
    }

    private fun errorDireccion() =
        Resultado.Error("Escriba una dirección de servidor válida (ej. siguec.miempresa.com).", TipoError.VALIDACION)

    /** Prueba si en esa dirección responde el servidor de AppSiguecPuraceramica. */
    suspend fun probarServidor(url: String): Resultado<SaludDto> {
        val c = cliente(url) ?: return errorDireccion()
        return RepositorioSesionRemota(c).probarServidor()
    }

    /**
     * Entra con usuario y clave. Si sale bien guarda la dirección, el usuario y
     * el token, y si el usuario tiene UNA sola empresa la deja elegida.
     */
    suspend fun entrar(url: String, usuario: String, clave: String): Resultado<SesionDto> {
        val c = cliente(url) ?: return errorDireccion()
        val resultado = RepositorioSesionRemota(c).entrar(usuario, clave, dispositivo)
        if (resultado is Resultado.Exito) {
            almacen.urlServidor = url.trim()
            almacen.ultimoUsuario = resultado.valor.usuario.usuario
            almacen.token = resultado.valor.token
            iniciar(resultado.valor)
        }
        return resultado
    }

    /**
     * Al abrir la App: si hay un token guardado pregunta al servidor si sigue vigente.
     * @return null si no había token; el resultado del servidor si lo había
     */
    suspend fun restaurar(): Resultado<SesionDto>? {
        if (almacen.token.isNullOrBlank()) return null
        val c = cliente() ?: return null
        val resultado = RepositorioSesionRemota(c).sesion()
        when {
            resultado is Resultado.Exito -> iniciar(resultado.valor)
            // Solo se borra el token si el servidor lo rechazó (sin internet se conserva)
            (resultado as Resultado.Error).tipo == TipoError.SESION -> almacen.token = null
        }
        return resultado
    }

    /** Deja la sesión en memoria y elige la empresa si se puede. */
    private fun iniciar(sesion: SesionDto) {
        _sesion.value = sesion
        _sesionVencida.value = false
        // La empresa de la vez anterior, si todavía la tiene; o la única que tiene
        val anterior = sesion.ambientes.firstOrNull { it.coEmpresa == almacen.empresa }
        _ambiente.value = anterior ?: sesion.ambientes.singleOrNull()
    }

    /** Empresa con la que se va a trabajar. */
    fun elegirEmpresa(ambiente: AmbienteDto) {
        almacen.empresa = ambiente.coEmpresa
        _ambiente.value = ambiente
    }

    /**
     * Repositorio de artículos (consulta y sincronización) de la empresa elegida.
     * @return null si todavía no hay empresa elegida
     */
    fun articulos(): RepositorioArticulos? {
        val empresa = _ambiente.value ?: return null
        val c = cliente() ?: return null
        return RepositorioArticulos(c, empresa.coEmpresa)
    }

    /** Repositorio de inventarios por almacén de la empresa elegida (null sin empresa). */
    fun inventarios(): RepositorioInventarios? {
        val empresa = _ambiente.value ?: return null
        val c = cliente() ?: return null
        return RepositorioInventarios(c, empresa.coEmpresa)
    }

    /** Repositorio de los conteos (escanear y anotar) de la empresa elegida (null sin empresa). */
    fun conteos(): RepositorioConteos? {
        val empresa = _ambiente.value ?: return null
        val c = cliente() ?: return null
        return RepositorioConteos(c, empresa.coEmpresa)
    }

    /** Último inventario en que se contó en la empresa elegida (null = ninguno). */
    fun ultimoInventario(): Int? = _ambiente.value?.let { almacen.inventario(it.coEmpresa) }

    /** Recuerda el inventario en que se está contando (null = olvidarlo, por ejemplo al cerrarse). */
    fun recordarInventario(id: Int?) {
        _ambiente.value?.let { almacen.guardarInventario(it.coEmpresa, id) }
    }

    /** Repositorio de administración (usuarios, permisos y bitácora); no depende de la empresa. */
    fun admin(): RepositorioAdmin? = cliente()?.let { RepositorioAdmin(it) }

    /**
     * Cierra la sesión. En el teléfono se borra DE UNA VEZ (la pantalla vuelve
     * a la entrada aunque no haya internet); después se avisa al servidor con
     * el token que se tenía, para que deje de servir.
     */
    suspend fun salir() {
        val token = almacen.token
        val url = almacen.urlServidor
        borrarSesion()
        if (token.isNullOrBlank() || ClienteSiguec.normalizarUrl(url) == null) return
        RepositorioSesionRemota(ClienteSiguec(url, token = { token })).salir()
    }

    /** La pantalla de entrada ya mostró el aviso de sesión vencida. */
    fun avisoVencidaMostrado() {
        _sesionVencida.value = false
    }

    // El servidor respondió 401 a una petición normal
    private fun alVencerSesion() {
        borrarSesion()
        _sesionVencida.value = true
    }

    private fun borrarSesion() {
        almacen.token = null
        _sesion.value = null
        _ambiente.value = null
    }
}
