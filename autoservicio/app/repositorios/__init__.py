"""
Repositorios: acceso a las tablas de Profit Plus 2K8.

Cada módulo de este paquete se encarga de UNA tabla y contiene TODAS las
consultas SQL sobre ella. Si Profit cambia un campo, o se descubre que un
dato está en otra columna, solo hay que tocar el repositorio correspondiente.

    usuarios.py   -> tabla employee de MasterProfit (usuario del config.xml)
    ambiente.py   -> tabla PPV_AMBIENTE (parámetros del usuario en la empresa)
    cajas.py      -> tabla "cajas" de Profit
    clientes.py   -> tabla "clientes" de Profit
    articulos.py  -> tabla "art" de Profit (búsqueda por código de barras)

Los campos de Profit son de tipo CHAR (rellenos con espacios a la derecha),
por eso en las consultas se usa RTRIM() y en Python .strip().
"""

from .usuarios import RepositorioUsuarios
from .ambiente import RepositorioAmbiente
from .cajas import RepositorioCajas
from .clientes import RepositorioClientes
from .articulos import RepositorioArticulos

__all__ = [
    "RepositorioUsuarios",
    "RepositorioAmbiente",
    "RepositorioCajas",
    "RepositorioClientes",
    "RepositorioArticulos",
]
