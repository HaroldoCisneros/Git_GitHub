"""
Piezas que comparten los servicios de artículos, inventarios y conteos.

    ambiente_con_permiso()   El ambiente de la empresa si tiene ALGUNO de los permisos (si no, 403).
    anotar()                 Anota en la bitácora (en su propia conexión; si falla, no molesta).
"""

from __future__ import annotations

import logging
from contextlib import AbstractContextManager
from typing import Callable

from ..errores import ErrorPermiso
from ..modelos import Ambiente, Sesion

log = logging.getLogger("siguec.comun")

AbrirApp = Callable[[], AbstractContextManager]
AbrirProfit = Callable[[str], AbstractContextManager]


def ambiente_con_permiso(sesion: Sesion, co_empresa: str, *permisos: str, mensaje: str) -> Ambiente:
    """
    :param permisos: nombres de permiso (consultar_articulos, contar_inventario...); basta con uno.
    :param mensaje: lo que ve el usuario si no tiene ninguno.
    """
    ambiente = sesion.ambiente(co_empresa)
    if ambiente is None:
        raise ErrorPermiso(f"No tiene ambiente en la empresa {co_empresa}.")
    if not any(ambiente.permisos.tiene(p) for p in permisos):
        raise ErrorPermiso(mensaje)
    return ambiente


def anotar(abrir_app: AbrirApp, accion: str, sesion: Sesion, co_empresa: str, detalle: str, ip: str = "") -> None:
    """Anota en la bitácora; si falla solo queda en el log (lo importante ya se hizo)."""
    try:
        with abrir_app() as app:
            app.bitacora.registrar(accion, sesion.usuario.id, sesion.usuario.usuario, co_empresa, detalle[:500], ip)
    except Exception:  # noqa: BLE001
        log.exception("No se pudo anotar %s en la bitácora", accion)
