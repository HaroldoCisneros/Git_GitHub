// MÓDULO :feature:admin
package com.siguec.puraceramica.feature.admin

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red: repositorio, DTO y resultado
import com.siguec.puraceramica.core.red.EmpresaAppDto
import com.siguec.puraceramica.core.red.EntradaAmbienteDto
import com.siguec.puraceramica.core.red.EntradaUsuarioCambiosDto
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.UsuarioAdminDto
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la ficha de un usuario. */
data class EstadoUsuario(
    val usuario: UsuarioAdminDto? = null,
    val empresas: List<EmpresaAppDto> = emptyList(),   // Para darle una empresa nueva
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    val guardando: Boolean = false,
    val aviso: String? = null
)

/**
 * Ficha de un usuario: datos, activo, administrador, desbloquear, clave y
 * sus empresas con permisos. Cada cambio se guarda en el momento.
 *
 * @param id usuario que se está viendo
 */
class UsuarioViewModel(
    private val repositorio: RepositorioAdmin,
    private val id: Int
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUsuario())
    val estado: StateFlow<EstadoUsuario> = _estado.asStateFlow()

    init {
        cargar()
        viewModelScope.launch {
            (repositorio.empresas() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(empresas = r.valor) } }
        }
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            when (val r = repositorio.usuario(id)) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, usuario = r.valor) }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }

    /**
     * Ejecuta un cambio que devuelve el usuario actualizado y muestra el aviso.
     * Si falla, el aviso es el mensaje del servidor y la ficha queda como estaba.
     */
    private fun guardar(aviso: String, accion: suspend () -> Resultado<UsuarioAdminDto>) {
        if (_estado.value.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            when (val r = accion()) {
                is Resultado.Exito -> _estado.update { it.copy(guardando = false, usuario = r.valor, aviso = aviso) }
                is Resultado.Error -> _estado.update { it.copy(guardando = false, aviso = r.mensaje) }
            }
        }
    }

    fun cambiarDatos(nombre: String, usuarioProfit: String) =
        guardar("Datos guardados") { repositorio.cambiar(id, EntradaUsuarioCambiosDto(nombre = nombre, usuarioProfit = usuarioProfit)) }

    fun activar(activo: Boolean) =
        guardar(if (activo) "Usuario activado" else "Usuario desactivado: se cerraron sus sesiones") {
            repositorio.cambiar(id, EntradaUsuarioCambiosDto(activo = activo))
        }

    fun administrador(esAdministrador: Boolean) =
        guardar(if (esAdministrador) "Ahora es administrador" else "Ya no es administrador") {
            repositorio.cambiar(id, EntradaUsuarioCambiosDto(esAdministrador = esAdministrador))
        }

    fun desbloquear() = guardar("Usuario desbloqueado") { repositorio.desbloquear(id) }

    fun guardarAmbiente(coEmpresa: String, almacen: String, permisos: Map<String, Boolean>) =
        guardar("Permisos de $coEmpresa guardados") {
            repositorio.guardarAmbiente(id, coEmpresa, EntradaAmbienteDto(almacen.trim(), permisos))
        }

    fun quitarAmbiente(coEmpresa: String) = guardar("Se quitó la empresa $coEmpresa") { repositorio.quitarAmbiente(id, coEmpresa) }

    /** Clave nueva (el servidor cierra las sesiones de ese usuario). */
    fun cambiarClave(clave: String) {
        if (_estado.value.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            when (val r = repositorio.cambiarClave(id, clave)) {
                is Resultado.Exito -> _estado.update { it.copy(guardando = false,
                    aviso = "Clave cambiada: tendrá que entrar de nuevo en sus teléfonos") }
                is Resultado.Error -> _estado.update { it.copy(guardando = false, aviso = r.mensaje) }
            }
        }
    }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
