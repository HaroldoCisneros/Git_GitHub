"""
Repositorio de ambientes (SIGUEC_PURACERAMICA.dbo.ambientes + empresas).

Un ambiente dice en qué empresa de Profit 2K12 puede entrar el usuario y qué
puede hacer ahí. Solo cuentan los ambientes activos de empresas activas.
"""

from __future__ import annotations

from typing import List

from ..base_datos import Conexion
from ..modelos import Ambiente, Permisos

SQL_DE_USUARIO = (
    "SELECT a.*, e.nombre "
    "FROM dbo.ambientes a JOIN dbo.empresas e ON e.co_empresa = a.co_empresa "
    "WHERE a.usuario_id = ? AND a.activo = 1 AND e.activa = 1 ORDER BY e.nombre"
)

SQL_EXISTE_EMPRESA = "SELECT co_empresa FROM dbo.empresas WHERE co_empresa = ?"
SQL_EMPRESAS = "SELECT co_empresa, nombre, activa FROM dbo.empresas ORDER BY nombre"
SQL_QUITAR = "DELETE FROM dbo.ambientes WHERE usuario_id = ? AND co_empresa = ?"
SQL_CREAR_EMPRESA = "INSERT INTO dbo.empresas (co_empresa, nombre) VALUES (?, ?)"

SQL_EXISTE_AMBIENTE = "SELECT id FROM dbo.ambientes WHERE usuario_id = ? AND co_empresa = ?"
SQL_CREAR_AMBIENTE = (
    "INSERT INTO dbo.ambientes (usuario_id, co_empresa, almacen, puede_consultar_articulos, "
    "puede_sincronizar_articulos, puede_preparar_inventario, puede_contar_inventario, activo) "
    "VALUES (?, ?, ?, ?, ?, ?, ?, 1)"
)
SQL_ACTUALIZAR_AMBIENTE = (
    "UPDATE dbo.ambientes SET almacen = ?, puede_consultar_articulos = ?, puede_sincronizar_articulos = ?, "
    "puede_preparar_inventario = ?, puede_contar_inventario = ?, activo = 1 WHERE id = ?"
)


def _texto(valor) -> str:
    return "" if valor is None else str(valor).strip()


class RepositorioAmbientes:

    def __init__(self, cx: Conexion):
        self._cx = cx

    def de_usuario(self, usuario_id: int) -> List[Ambiente]:
        return [
            Ambiente(
                co_empresa=_texto(f.get("co_empresa")),
                nombre_empresa=_texto(f.get("nombre")),
                almacen=_texto(f.get("almacen")),
                permisos=Permisos(
                    consultar_articulos=bool(f.get("puede_consultar_articulos")),
                    sincronizar_articulos=bool(f.get("puede_sincronizar_articulos")),
                    preparar_inventario=bool(f.get("puede_preparar_inventario")),
                    contar_inventario=bool(f.get("puede_contar_inventario")),
                ),
            )
            for f in self._cx.consultar(SQL_DE_USUARIO, (usuario_id,))
        ]

    def empresas(self) -> List[dict]:
        """Todas las empresas (para darle acceso a un usuario desde la App)."""
        return self._cx.consultar(SQL_EMPRESAS)

    def existe_empresa(self, co_empresa: str) -> bool:
        return self._cx.consultar_uno(SQL_EXISTE_EMPRESA, (co_empresa,)) is not None

    def quitar(self, usuario_id: int, co_empresa: str) -> int:
        """Le quita al usuario el acceso a esa empresa."""
        return self._cx.ejecutar(SQL_QUITAR, (usuario_id, co_empresa))

    def guardar_empresa(self, co_empresa: str, nombre: str) -> None:
        """Crea la empresa si no existe."""
        if self._cx.consultar_uno(SQL_EXISTE_EMPRESA, (co_empresa,)) is None:
            self._cx.ejecutar(SQL_CREAR_EMPRESA, (co_empresa, nombre))

    def guardar(self, usuario_id: int, co_empresa: str, almacen: str, permisos: Permisos) -> None:
        """Crea el ambiente o, si ya existe, lo reemplaza con estos datos."""
        valores = (almacen or None, permisos.consultar_articulos, permisos.sincronizar_articulos,
                   permisos.preparar_inventario, permisos.contar_inventario)
        fila = self._cx.consultar_uno(SQL_EXISTE_AMBIENTE, (usuario_id, co_empresa))
        if fila is None:
            self._cx.ejecutar(SQL_CREAR_AMBIENTE, (usuario_id, co_empresa) + valores)
        else:
            self._cx.ejecutar(SQL_ACTUALIZAR_AMBIENTE, valores + (fila["id"],))
