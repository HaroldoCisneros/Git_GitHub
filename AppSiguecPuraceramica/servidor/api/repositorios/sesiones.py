"""
Repositorio de sesiones (SIGUEC_PURACERAMICA.dbo.sesiones).

Cada entrada desde un teléfono crea un renglón con el SHA-256 de su token.
"Salir" lo marca como revocado; también vence solo en ``expira``.
"""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from ..base_datos import Conexion

SQL_CREAR = (
    "INSERT INTO dbo.sesiones (token_hash, usuario_id, creada, expira, ultimo_uso, dispositivo, ip) "
    "VALUES (?, ?, ?, ?, ?, ?, ?)"
)

SQL_BUSCAR = (
    "SELECT usuario_id, expira, ultimo_uso, revocada, dispositivo FROM dbo.sesiones WHERE token_hash = ?"
)

SQL_USO = "UPDATE dbo.sesiones SET ultimo_uso = ? WHERE token_hash = ?"

SQL_REVOCAR = "UPDATE dbo.sesiones SET revocada = 1 WHERE token_hash = ?"

SQL_REVOCAR_USUARIO = "UPDATE dbo.sesiones SET revocada = 1 WHERE usuario_id = ? AND revocada = 0"

# Largo de la columna dispositivo.
LARGO_DISPOSITIVO = 100


class RepositorioSesiones:

    def __init__(self, cx: Conexion):
        self._cx = cx

    def crear(self, token_hash: str, usuario_id: int, creada: datetime, expira: datetime,
              dispositivo: str, ip: str) -> None:
        self._cx.ejecutar(SQL_CREAR, (token_hash, usuario_id, creada, expira, creada,
                                      (dispositivo or "")[:LARGO_DISPOSITIVO], (ip or "")[:45]))

    def buscar(self, token_hash: str) -> Optional[dict]:
        """La sesión del token (vencida o no) o None si no existe."""
        return self._cx.consultar_uno(SQL_BUSCAR, (token_hash,))

    def anotar_uso(self, token_hash: str, fecha: datetime) -> None:
        self._cx.ejecutar(SQL_USO, (fecha, token_hash))

    def revocar(self, token_hash: str) -> None:
        self._cx.ejecutar(SQL_REVOCAR, (token_hash,))

    def revocar_de_usuario(self, usuario_id: int) -> None:
        """Cierra todas las sesiones del usuario (al cambiarle la clave o desactivarlo)."""
        self._cx.ejecutar(SQL_REVOCAR_USUARIO, (usuario_id,))
