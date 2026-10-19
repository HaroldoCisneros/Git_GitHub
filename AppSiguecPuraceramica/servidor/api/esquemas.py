"""
Forma del JSON que viaja entre el teléfono y la API (Pydantic).

Es el "contrato": la App de Kotlin tiene las mismas clases en
android/core/red/.../Dto.kt. Si se cambia algo aquí, se cambia allá también.
"""

from __future__ import annotations

from datetime import datetime
from typing import Dict, List, Optional

from pydantic import BaseModel, Field

from . import modelos


# ---------------------------------------------------------------------------
# Sesión
# ---------------------------------------------------------------------------

class EntradaSesion(BaseModel):
    usuario: str = Field(max_length=30)
    clave: str = Field(max_length=128)
    dispositivo: str = Field(default="", max_length=100)   # Modelo del teléfono (para la bitácora)


class PermisosSalida(BaseModel):
    consultar_articulos: bool
    sincronizar_articulos: bool
    preparar_inventario: bool
    contar_inventario: bool


class AmbienteSalida(BaseModel):
    co_empresa: str
    nombre: str
    almacen: str
    permisos: PermisosSalida


class UsuarioSalida(BaseModel):
    usuario: str
    nombre: str
    es_administrador: bool


class SesionSalida(BaseModel):
    usuario: UsuarioSalida
    ambientes: List[AmbienteSalida]
    expira: datetime


class SesionNueva(SesionSalida):
    token: str


def sesion_salida(sesion: modelos.Sesion) -> dict:
    u = sesion.usuario
    return {
        "usuario": {"usuario": u.usuario, "nombre": u.nombre, "es_administrador": u.es_administrador},
        "ambientes": [
            {"co_empresa": a.co_empresa, "nombre": a.nombre_empresa, "almacen": a.almacen,
             "permisos": vars(a.permisos)}
            for a in sesion.ambientes
        ],
        "expira": sesion.expira,
    }


# ---------------------------------------------------------------------------
# Artículos (copia sincronizada)
# ---------------------------------------------------------------------------

class ArticuloSalida(BaseModel):
    co_art: str
    art_des: str
    contado: Optional[str] = None          # None = no se ha contado en el inventario elegido


class PaginaArticulosSalida(BaseModel):
    articulos: List[ArticuloSalida]
    pagina: int
    tamano: int
    hay_mas: bool
    inventario_id: Optional[int] = None


class SincronizacionSalida(BaseModel):
    fecha: Optional[str]
    usuario: str
    leidos: int
    nuevos: int
    actualizados: int
    retirados: int
    codigos: int
    total: int


class UltimaSincronizacionSalida(BaseModel):
    sincronizacion: Optional[SincronizacionSalida] = None   # None = nunca se ha sincronizado


# ---------------------------------------------------------------------------
# Inventarios y conteos
# ---------------------------------------------------------------------------

class AlmacenSalida(BaseModel):
    co_alma: str
    des_alma: str
    inventario_abierto: Optional[int] = None


class ListaAlmacenesSalida(BaseModel):
    almacenes: List[AlmacenSalida]


class EntradaInventario(BaseModel):
    co_alma: str = Field(max_length=6)
    descripcion: str = Field(default="", max_length=100)


class InventarioSalida(BaseModel):
    id: int
    co_alma: str
    des_alma: str
    descripcion: str
    abierto: bool
    creado_por: str
    fecha_creacion: Optional[str]
    cerrado_por: str
    fecha_cierre: Optional[str]
    articulos_stock: int
    articulos_contados: int
    conteos: int


class ListaInventariosSalida(BaseModel):
    inventarios: List[InventarioSalida]


class ConteoSalida(BaseModel):
    id: int
    co_art: str
    art_des: str
    codigo_leido: str
    cantidad: str
    usuario: str
    fecha: Optional[str]
    puede_borrar: bool


class ListaConteosSalida(BaseModel):
    conteos: List[ConteoSalida]


class DetalleInventarioSalida(BaseModel):
    inventario: InventarioSalida
    mis_conteos: List[ConteoSalida]
    contado_por_mi: int


class EntradaConteo(BaseModel):
    codigo: str = Field(max_length=100)             # Escaneado o escrito
    cantidad: str = Field(max_length=20)            # Texto: "12", "2,5" o "-1" para corregir


class ResultadoConteoSalida(BaseModel):
    conteo: ConteoSalida
    contado_usuario: str
    contado_total: str


class DiferenciaSalida(BaseModel):
    co_art: str
    art_des: str
    stock: str
    contado: str
    diferencia: str
    contado_si: bool


class ComparacionSalida(BaseModel):
    inventario: InventarioSalida
    diferencias: List[DiferenciaSalida]
    articulos_contados: int
    con_diferencia: int
    sobrantes: int
    faltantes: int
    no_contados: int
    pagina: int
    tamano: int
    hay_mas: bool


# ---------------------------------------------------------------------------
# Administración
# ---------------------------------------------------------------------------

class EntradaUsuarioNuevo(BaseModel):
    usuario: str = Field(max_length=30)
    nombre: str = Field(max_length=100)
    clave: str = Field(max_length=128)
    usuario_profit: str = Field(default="", max_length=10)
    es_administrador: bool = False


class EntradaUsuarioCambios(BaseModel):
    """Solo lo que se quiere cambiar (lo que no venga se deja igual)."""
    nombre: Optional[str] = Field(default=None, max_length=100)
    usuario_profit: Optional[str] = Field(default=None, max_length=10)
    es_administrador: Optional[bool] = None
    activo: Optional[bool] = None


class EntradaClave(BaseModel):
    clave: str = Field(max_length=128)


class EntradaAmbiente(BaseModel):
    almacen: str = Field(default="", max_length=6)
    permisos: Dict[str, bool]


class EmpresaAppSalida(BaseModel):
    co_empresa: str
    nombre: str
    activa: bool


class ListaEmpresasSalida(BaseModel):
    empresas: List[EmpresaAppSalida]


class AmbienteAdminSalida(BaseModel):
    co_empresa: str
    nombre_empresa: str
    almacen: str
    permisos: PermisosSalida


class UsuarioAdminSalida(BaseModel):
    id: int
    usuario: str
    nombre: str
    usuario_profit: str
    es_administrador: bool
    activo: bool
    bloqueado: bool
    bloqueado_hasta: Optional[str]
    intentos_fallidos: int
    ultimo_acceso: Optional[str]
    ambientes: List[AmbienteAdminSalida]


class ListaUsuariosSalida(BaseModel):
    usuarios: List[UsuarioAdminSalida]


class RenglonBitacoraSalida(BaseModel):
    id: int
    fecha: Optional[str]
    usuario: str
    co_empresa: str
    accion: str
    detalle: str
    ip: str


class PaginaBitacoraSalida(BaseModel):
    renglones: List[RenglonBitacoraSalida]
    pagina: int
    tamano: int
    hay_mas: bool
