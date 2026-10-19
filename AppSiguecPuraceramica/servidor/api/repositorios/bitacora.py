"""
Repositorio de la bitácora de la App (SIGUEC_PURACERAMICA.dbo.bitacora).

Anota lo que pasa desde los teléfonos: entradas, claves erradas, bloqueos,
sincronizaciones y lo que se hace con los inventarios.
"""

from __future__ import annotations

from typing import Optional

from ..base_datos import Conexion

# Acciones que se anotan.
ENTRADA = "ENTRADA"
CLAVE_ERRADA = "CLAVE_ERRADA"
BLOQUEO = "BLOQUEO"
SALIDA = "SALIDA"
SINCRONIZAR = "SINCRONIZAR"              # Bajó los artículos de Profit
INVENTARIO_ABRIR = "INVENTARIO_ABRIR"    # Preparó un inventario de un almacén
INVENTARIO_CERRAR = "INVENTARIO_CERRAR"
CONTEO_BORRAR = "CONTEO_BORRAR"          # Borró un conteo (los conteos en sí quedan en su tabla)
ADMIN = "ADMIN"                          # Cambios de usuarios y permisos desde la App

SQL_REGISTRAR = (
    "INSERT INTO dbo.bitacora (fecha, usuario_id, usuario, co_empresa, accion, detalle, ip) "
    "VALUES (GETDATE(), ?, ?, ?, ?, ?, ?)"
)

SQL_BUSCAR = (
    "SET NOCOUNT ON; DECLARE @u varchar(30) = ?, @e varchar(30) = ?, @a varchar(30) = ?, "
    "@d datetime = ?, @h datetime = ?; "
    "SELECT TOP (?) * FROM ("
    " SELECT ROW_NUMBER() OVER (ORDER BY b.id DESC) AS fila, b.id, b.fecha, b.usuario, b.co_empresa, "
    " b.accion, b.detalle, b.ip FROM dbo.bitacora b "
    " WHERE (@u = '' OR b.usuario = @u) AND (@e = '' OR b.co_empresa = @e) AND (@a = '' OR b.accion = @a) "
    " AND (@d IS NULL OR b.fecha >= @d) AND (@h IS NULL OR b.fecha < @h)"
    ") AS t WHERE t.fila > ? ORDER BY t.fila"
)


class RepositorioBitacora:

    def __init__(self, cx: Conexion):
        self._cx = cx

    def registrar(self, accion: str, usuario_id: Optional[int] = None, usuario: str = "",
                  co_empresa: str = "", detalle: str = "", ip: str = "") -> None:
        self._cx.ejecutar(SQL_REGISTRAR, (usuario_id, (usuario or "")[:30], co_empresa or None,
                                          accion, detalle or None, (ip or "")[:45]))

    def buscar(self, usuario: str, co_empresa: str, accion: str, desde, hasta_sin_incluir,
               pagina: int, tamano: int):
        """
        Lo más reciente primero. Filtros vacíos o None = sin filtro.
        :return: (filas de la página, hay_mas)
        """
        filas = self._cx.consultar(SQL_BUSCAR, (usuario or "", co_empresa or "", accion or "", desde,
                                                hasta_sin_incluir, tamano + 1, (pagina - 1) * tamano))
        return filas[:tamano], len(filas) > tamano
