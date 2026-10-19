"""
Clases de datos que se pasan entre repositorios, servicios y rutas.

No saben nada de SQL ni de HTTP: son solo "fichas" con datos.
Las cantidades viajan como TEXTO ("12.5") para no tener redondeos de punto flotante.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Optional


# ---------------------------------------------------------------------------
# Usuarios y ambientes (misma lógica de AppSighecMovil)
# ---------------------------------------------------------------------------

@dataclass
class Usuario:
    """Un renglón de SIGUEC_PURACERAMICA.dbo.usuarios."""
    id: int
    usuario: str
    nombre: str
    clave_hash: str = ""
    usuario_profit: str = ""          # Referencia al usuario de Profit (opcional).
    es_administrador: bool = False
    activo: bool = True
    intentos_fallidos: int = 0
    bloqueado_hasta: Optional[datetime] = None


@dataclass
class Permisos:
    """Lo que el usuario puede hacer en una empresa (columnas puede_* de ambientes)."""
    consultar_articulos: bool = True      # Código, descripción y stock contado
    sincronizar_articulos: bool = False   # Bajar los artículos de Profit a la copia de la App
    preparar_inventario: bool = False     # Abrir (preparar) y cerrar inventarios, ver diferencias
    contar_inventario: bool = True        # Escanear y anotar en un inventario abierto

    def tiene(self, nombre: str) -> bool:
        """``tiene("contar_inventario")`` -> True / False."""
        return bool(getattr(self, nombre, False))


@dataclass
class Ambiente:
    """El ambiente de un usuario en una empresa (ambientes + empresas)."""
    co_empresa: str                   # = nombre de la base de Profit 2K12
    nombre_empresa: str
    almacen: str = ""                 # Almacén por defecto al preparar
    permisos: Permisos = field(default_factory=Permisos)


@dataclass
class Sesion:
    """Quién está usando la App en esta petición."""
    usuario: Usuario
    ambientes: List[Ambiente]
    expira: datetime
    dispositivo: str = ""

    def ambiente(self, co_empresa: str) -> Optional[Ambiente]:
        """El ambiente de esa empresa (sin importar mayúsculas) o None si no tiene."""
        buscado = (co_empresa or "").strip().upper()
        for ambiente in self.ambientes:
            if ambiente.co_empresa.strip().upper() == buscado:
                return ambiente
        return None


# ---------------------------------------------------------------------------
# Artículos (copia de saArticulo en la base de la App)
# ---------------------------------------------------------------------------

@dataclass
class ArticuloConsulta:
    """Lo único que se muestra de un artículo: código, descripción y stock contado."""
    co_art: str
    art_des: str
    contado: Optional[str] = None     # Suma de los conteos en el inventario elegido (None = no se ha contado)


@dataclass
class PaginaArticulos:
    articulos: List[ArticuloConsulta]
    pagina: int
    tamano: int
    hay_mas: bool
    inventario_id: Optional[int] = None


@dataclass
class ResultadoSincronizacion:
    """Lo que hizo la acción "Sincronizar artículos"."""
    fecha: Optional[str]
    usuario: str
    leidos: int                       # Artículos leídos de Profit
    nuevos: int                       # No estaban en la copia
    actualizados: int                 # Cambió la descripción o el estado
    retirados: int                    # Estaban en la copia y ya no están en Profit
    codigos: int                      # Códigos para escanear (co_art, modelo, ref, barras)
    total: int = 0                    # Artículos en la copia después de sincronizar


# ---------------------------------------------------------------------------
# Inventarios (preparados por almacén) y conteos
# ---------------------------------------------------------------------------

@dataclass
class Almacen:
    co_alma: str
    des_alma: str
    inventario_abierto: Optional[int] = None    # Id del inventario abierto en ese almacén


@dataclass
class Inventario:
    id: int
    co_alma: str
    des_alma: str
    descripcion: str
    abierto: bool
    creado_por: str
    fecha_creacion: Optional[str]
    cerrado_por: str = ""
    fecha_cierre: Optional[str] = None
    articulos_stock: int = 0          # Artículos con stock en la foto de Profit
    articulos_contados: int = 0       # Artículos distintos contados
    conteos: int = 0                  # Conteos individuales anotados


@dataclass
class ConteoIndividual:
    """Un conteo anotado por una persona (para poder borrarlo si se equivocó)."""
    id: int
    co_art: str
    art_des: str
    codigo_leido: str
    cantidad: str
    usuario: str
    fecha: Optional[str]
    puede_borrar: bool = False


@dataclass
class ResultadoConteo:
    """Lo que ve el que cuenta después de anotar."""
    conteo: ConteoIndividual
    contado_usuario: str              # Lo que ESTE usuario lleva contado de ese artículo
    contado_total: str                # Lo que llevan todos (stock contado)


@dataclass
class DetalleInventario:
    inventario: Inventario
    mis_conteos: List[ConteoIndividual]   # Los últimos del usuario que consulta
    contado_por_mi: int                   # Cuántos conteos lleva este usuario


@dataclass
class DiferenciaInventario:
    co_art: str
    art_des: str
    stock: str                        # Foto del stock de Profit al preparar
    contado: str                      # Suma de los conteos
    diferencia: str                   # contado - stock (+ sobra, - falta)
    contado_si: bool                  # False = tiene stock y nadie lo contó


@dataclass
class ComparacionInventario:
    inventario: Inventario
    diferencias: List[DiferenciaInventario]
    articulos_contados: int
    con_diferencia: int
    sobrantes: int
    faltantes: int
    no_contados: int
    pagina: int
    tamano: int
    hay_mas: bool


# ---------------------------------------------------------------------------
# Administración de la App
# ---------------------------------------------------------------------------

@dataclass
class EmpresaApp:
    co_empresa: str
    nombre: str
    activa: bool


@dataclass
class AmbienteAdmin:
    co_empresa: str
    nombre_empresa: str
    almacen: str
    permisos: Permisos


@dataclass
class UsuarioAdmin:
    id: int
    usuario: str
    nombre: str
    usuario_profit: str
    es_administrador: bool
    activo: bool
    bloqueado: bool                   # Bloqueado ahora por claves erradas
    bloqueado_hasta: Optional[str]
    intentos_fallidos: int
    ultimo_acceso: Optional[str]
    ambientes: List[AmbienteAdmin]


@dataclass
class RenglonBitacora:
    id: int
    fecha: Optional[str]
    usuario: str
    co_empresa: str
    accion: str
    detalle: str
    ip: str


@dataclass
class PaginaBitacora:
    renglones: List[RenglonBitacora]
    pagina: int
    tamano: int
    hay_mas: bool
