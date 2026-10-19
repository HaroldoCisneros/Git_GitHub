// MÓDULO :feature:admin
// Paquete de la administración de la App
package com.siguec.puraceramica.feature.admin

// ViewModel y sus corrutinas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Red: repositorio, DTO, validaciones y resultado
import com.siguec.puraceramica.core.red.Administracion
import com.siguec.puraceramica.core.red.EntradaUsuarioNuevoDto
import com.siguec.puraceramica.core.red.RepositorioAdmin
import com.siguec.puraceramica.core.red.Resultado
import com.siguec.puraceramica.core.red.UsuarioAdminDto
// Estado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la lista de usuarios. */
data class EstadoUsuarios(
    val usuarios: List<UsuarioAdminDto> = emptyList(),
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    val creando: Boolean = false,
    val errorNuevo: String? = null,          // Error del diálogo de usuario nuevo
    val abrirUsuario: Int? = null,           // Usuario recién creado: la pantalla lo abre
    val aviso: String? = null
)

/** Lista de usuarios y alta de uno nuevo. */
class UsuariosViewModel(private val repositorio: RepositorioAdmin) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoUsuarios())
    val estado: StateFlow<EstadoUsuarios> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            when (val r = repositorio.usuarios()) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, usuarios = r.valor) }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, mensajeError = r.mensaje) }
            }
        }
    }

    /** Diálogo "Nuevo usuario" -> Guardar. Si sale bien, se abre su ficha para darle empresas. */
    fun crear(usuario: String, nombre: String, usuarioProfit: String, clave: String, repetida: String,
              esAdministrador: Boolean) {
        Administracion.validarNuevo(usuario, nombre, clave, repetida)?.let { error ->
            _estado.update { it.copy(errorNuevo = error) }
            return
        }
        _estado.update { it.copy(creando = true, errorNuevo = null) }
        viewModelScope.launch {
            val entrada = EntradaUsuarioNuevoDto(usuario.trim(), nombre.trim(), clave, usuarioProfit.trim(), esAdministrador)
            when (val r = repositorio.crear(entrada)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(creando = false, abrirUsuario = r.valor.id,
                        aviso = "Usuario ${r.valor.usuario} creado. Ahora dele una empresa.") }
                    cargar()
                }
                is Resultado.Error -> _estado.update { it.copy(creando = false, errorNuevo = r.mensaje) }
            }
        }
    }

    fun limpiarErrorNuevo() = _estado.update { it.copy(errorNuevo = null) }

    fun usuarioAbierto() = _estado.update { it.copy(abrirUsuario = null) }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }
}
