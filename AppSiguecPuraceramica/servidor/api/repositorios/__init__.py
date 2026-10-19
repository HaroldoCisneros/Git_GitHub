"""
Repositorios: TODO el SQL de la API está aquí, un archivo por tema.

Base de la App (SIGUEC_PURACERAMICA):  usuarios, ambientes, sesiones, bitacora,
                                       articulos (copia de Profit), inventarios (y conteos).
Base de la empresa (Profit Plus 2K12): profit (saArticulo, saAlmacen, saStockAlmacen; solo lectura).

Cada repositorio recibe una ``Conexion`` ya abierta (ver base_datos.py): así
varios repositorios trabajan en la MISMA transacción. Las "unidades" de abajo
juntan los repositorios de cada base.
"""

from __future__ import annotations

from ..base_datos import Conexion
from ..configuracion import ConfigProfit
from .ambientes import RepositorioAmbientes
from .articulos import RepositorioArticulos
from .bitacora import RepositorioBitacora
from .inventarios import RepositorioInventarios
from .profit import RepositorioProfit
from .sesiones import RepositorioSesiones
from .usuarios import RepositorioUsuarios


class UnidadApp:
    """Repositorios de la base SIGUEC_PURACERAMICA sobre una misma conexión."""

    def __init__(self, cx: Conexion):
        self.usuarios = RepositorioUsuarios(cx)
        self.ambientes = RepositorioAmbientes(cx)
        self.sesiones = RepositorioSesiones(cx)
        self.bitacora = RepositorioBitacora(cx)
        self.articulos = RepositorioArticulos(cx)
        self.inventarios = RepositorioInventarios(cx)


class UnidadProfit:
    """Lectura de la base de una empresa de Profit 2K12."""

    def __init__(self, cx: Conexion, cfg: ConfigProfit):
        self.co_empresa = cx.base
        self.profit = RepositorioProfit(cx, cfg)
