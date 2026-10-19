"""
Repositorio de usuarios de la App (SIGUEC_PURACERAMICA.dbo.usuarios).

Estos usuarios son PROPIOS de la App (no los de MasterProfit.dbo.employee):
así se le puede dar acceso desde el teléfono a quien no usa Profit, y quitarlo
sin tocar Profit. La columna usuario_profit enlaza con el usuario de Profit
como referencia (quién es en Profit).
"""

from __future__ import annotations

from datetime import datetime
from typing import List, Optional

from ..base_datos import Conexion
from ..modelos import Usuario

SQL_POR_USUARIO = (
    "SELECT id, usuario, nombre, clave_hash, usuario_profit, es_administrador, activo, "
    "intentos_fallidos, bloqueado_hasta FROM dbo.usuarios WHERE usuario = ?"
)
SQL_POR_ID = SQL_POR_USUARIO.replace("WHERE usuario = ?", "WHERE id = ?")

SQL_FALLO = "UPDATE dbo.usuarios SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id = ?"

SQL_ACCESO = (
    "UPDATE dbo.usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_acceso = ? "
    "WHERE id = ?"
)

SQL_CREAR = (
    "INSERT INTO dbo.usuarios (usuario, nombre, clave_hash, usuario_profit, es_administrador, activo) "
    "VALUES (?, ?, ?, ?, ?, 1)"
)

SQL_CAMBIAR_CLAVE = (
    "UPDATE dbo.usuarios SET clave_hash = ?, intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?"
)

SQL_ACTIVAR = "UPDATE dbo.usuarios SET activo = ? WHERE id = ?"

SQL_LISTA = (
    "SELECT id, usuario, nombre, usuario_profit, es_administrador, activo, intentos_fallidos, "
    "bloqueado_hasta, fecha_creacion, ultimo_acceso FROM dbo.usuarios ORDER BY usuario"
)

SQL_ACTUALIZAR = (
    "UPDATE dbo.usuarios SET nombre = ?, usuario_profit = ?, es_administrador = ? WHERE id = ?"
)

SQL_DESBLOQUEAR = "UPDATE dbo.usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?"


def normalizar_codigo(codigo: str) -> str:
    """Los usuarios se guardan en mayúsculas y sin espacios (igual que Profit)."""
    return (codigo or "").strip().upper()


def usuario_desde_fila(fila: dict) -> Usuario:
    return Usuario(
        id=int(fila["id"]),
        usuario=(fila.get("usuario") or "").strip(),
        nombre=(fila.get("nombre") or "").strip(),
        clave_hash=(fila.get("clave_hash") or "").strip(),
        usuario_profit=(fila.get("usuario_profit") or "").strip(),
        es_administrador=bool(fila.get("es_administrador")),
        activo=bool(fila.get("activo")),
        intentos_fallidos=int(fila.get("intentos_fallidos") or 0),
        bloqueado_hasta=fila.get("bloqueado_hasta"),
    )


class RepositorioUsuarios:

    def __init__(self, cx: Conexion):
        self._cx = cx

    def por_usuario(self, codigo: str) -> Optional[Usuario]:
        fila = self._cx.consultar_uno(SQL_POR_USUARIO, (normalizar_codigo(codigo),))
        return usuario_desde_fila(fila) if fila else None

    def por_id(self, usuario_id: int) -> Optional[Usuario]:
        fila = self._cx.consultar_uno(SQL_POR_ID, (usuario_id,))
        return usuario_desde_fila(fila) if fila else None

    def registrar_fallo(self, usuario_id: int, intentos: int, bloqueado_hasta: Optional[datetime]) -> None:
        """Clave errada: guarda el contador (y el bloqueo, si llegó al máximo)."""
        self._cx.ejecutar(SQL_FALLO, (intentos, bloqueado_hasta, usuario_id))

    def registrar_acceso(self, usuario_id: int, fecha: datetime) -> None:
        """Entrada correcta: borra el contador de fallos y anota la fecha."""
        self._cx.ejecutar(SQL_ACCESO, (fecha, usuario_id))

    def crear(self, codigo: str, nombre: str, clave_hash: str,
              usuario_profit: str = "", es_administrador: bool = False) -> None:
        self._cx.ejecutar(SQL_CREAR, (normalizar_codigo(codigo), nombre.strip(), clave_hash,
                                      normalizar_codigo(usuario_profit) or None, es_administrador))

    def cambiar_clave(self, usuario_id: int, clave_hash: str) -> None:
        self._cx.ejecutar(SQL_CAMBIAR_CLAVE, (clave_hash, usuario_id))

    def activar(self, usuario_id: int, activo: bool) -> None:
        self._cx.ejecutar(SQL_ACTIVAR, (activo, usuario_id))

    # ---- Para la administración desde la App ----

    def lista(self) -> List[dict]:
        """Todos los usuarios (sin la clave), con su último acceso."""
        return self._cx.consultar(SQL_LISTA)

    def actualizar(self, usuario_id: int, nombre: str, usuario_profit: str, es_administrador: bool) -> None:
        self._cx.ejecutar(SQL_ACTUALIZAR, (nombre.strip(), normalizar_codigo(usuario_profit) or None,
                                           es_administrador, usuario_id))

    def desbloquear(self, usuario_id: int) -> None:
        """Quita el bloqueo por claves erradas."""
        self._cx.ejecutar(SQL_DESBLOQUEAR, (usuario_id,))
