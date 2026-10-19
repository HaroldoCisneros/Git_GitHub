"""
Repositorio de los inventarios y sus conteos (tablas PROPIAS de la App):

    inventarios          Un inventario preparado por almacén (A abierto / C cerrado).
    inventario_stock     Foto del stock de Profit del almacén al preparar.
    inventario_conteos   Conteos individuales: quién contó qué artículo y cuánto.

Lo contado NUNCA se escribe en Profit: la oficina compara y ajusta en Profit.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Any, Dict, List, Optional, Sequence, Tuple

from ..base_datos import Conexion

ABIERTO = "A"
CERRADO = "C"

# Datos del inventario con sus contadores
_COLUMNAS = (
    "SELECT {top} i.id, i.co_empresa, RTRIM(i.co_alma) AS co_alma, i.des_alma, i.descripcion, i.estado, "
    " i.creado_por, i.fecha_creacion, i.cerrado_por, i.fecha_cierre, "
    " (SELECT COUNT(*) FROM dbo.inventario_stock s WHERE s.inventario_id = i.id AND s.stock <> 0) AS articulos_stock, "
    " (SELECT COUNT(DISTINCT c.co_art) FROM dbo.inventario_conteos c WHERE c.inventario_id = i.id) AS articulos_contados, "
    " (SELECT COUNT(*) FROM dbo.inventario_conteos c WHERE c.inventario_id = i.id) AS conteos "
    "FROM dbo.inventarios i "
)
SQL_LISTA = _COLUMNAS.format(top="TOP 50") + (
    "WHERE i.co_empresa = ? AND (i.estado = ? OR ? = '') ORDER BY i.estado, i.id DESC"
)
SQL_POR_ID = _COLUMNAS.format(top="") + "WHERE i.id = ? AND i.co_empresa = ?"

SQL_ABIERTO_DEL_ALMACEN = (
    "SELECT id FROM dbo.inventarios WITH (UPDLOCK, HOLDLOCK) WHERE co_empresa = ? AND co_alma = ? AND estado = 'A'"
)
SQL_ABIERTOS_POR_ALMACEN = (
    "SELECT RTRIM(co_alma) AS co_alma, MAX(id) AS id FROM dbo.inventarios "
    "WHERE co_empresa = ? AND estado = 'A' GROUP BY co_alma"
)
SQL_CREAR = (
    "SET NOCOUNT ON; INSERT INTO dbo.inventarios (co_empresa, co_alma, des_alma, descripcion, estado, creado_por) "
    "VALUES (?, ?, ?, ?, 'A', ?); SELECT CAST(SCOPE_IDENTITY() AS int) AS id"
)
SQL_GUARDAR_STOCK = "INSERT INTO dbo.inventario_stock (inventario_id, co_art, stock) VALUES (?, ?, ?)"
SQL_CERRAR = (
    "UPDATE dbo.inventarios SET estado = 'C', cerrado_por = ?, fecha_cierre = GETDATE() WHERE id = ? AND estado = 'A'"
)
SQL_STOCK = "SELECT co_art, stock FROM dbo.inventario_stock WHERE inventario_id = ?"

# ---------- Conteos ----------
SQL_ANOTAR = (
    "SET NOCOUNT ON; INSERT INTO dbo.inventario_conteos (inventario_id, co_art, codigo_leido, cantidad, usuario) "
    "VALUES (?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS bigint) AS id"
)
SQL_CONTEO = (
    "SELECT id, inventario_id, co_art, codigo_leido, cantidad, usuario, fecha FROM dbo.inventario_conteos WHERE id = ?"
)
SQL_BORRAR = "DELETE FROM dbo.inventario_conteos WHERE id = ? AND inventario_id = ?"
SQL_CONTADO = (
    "SELECT ISNULL(SUM(cantidad), 0) AS total, COUNT(*) AS veces, "
    " ISNULL(SUM(CASE WHEN usuario = ? THEN cantidad ELSE 0 END), 0) AS usuario "
    "FROM dbo.inventario_conteos WHERE inventario_id = ? AND co_art = ?"
)
SQL_TOTALES = (
    "SELECT co_art, SUM(cantidad) AS cantidad FROM dbo.inventario_conteos WHERE inventario_id = ? GROUP BY co_art"
)
SQL_ULTIMOS = (
    "SELECT TOP (?) c.id, c.co_art, a.art_des, c.codigo_leido, c.cantidad, c.usuario, c.fecha "
    "FROM dbo.inventario_conteos c "
    "LEFT JOIN dbo.inventarios i ON i.id = c.inventario_id "
    "LEFT JOIN dbo.articulos a ON a.co_empresa = i.co_empresa AND a.co_art = c.co_art "
    "WHERE c.inventario_id = ? AND (? = '' OR c.usuario = ?) ORDER BY c.id DESC"
)
SQL_CUANTOS_DE_USUARIO = (
    "SELECT COUNT(*) AS cuantos FROM dbo.inventario_conteos WHERE inventario_id = ? AND usuario = ?"
)


class RepositorioInventarios:

    def __init__(self, cx: Conexion):
        self._cx = cx

    # ---------- Inventarios ----------

    def lista(self, co_empresa: str, estado: str = "") -> List[Dict[str, Any]]:
        """Los últimos 50 inventarios ("" = abiertos y cerrados; los abiertos primero)."""
        return self._cx.consultar(SQL_LISTA, (co_empresa, estado, estado))

    def por_id(self, inventario_id: int, co_empresa: str) -> Optional[Dict[str, Any]]:
        return self._cx.consultar_uno(SQL_POR_ID, (inventario_id, co_empresa))

    def abierto_del_almacen(self, co_empresa: str, co_alma: str) -> Optional[int]:
        """
        Id del inventario abierto en ese almacén (o None). UPDLOCK + HOLDLOCK: si dos
        personas preparan el mismo almacén a la vez, la segunda espera y lo ve abierto.
        """
        fila = self._cx.consultar_uno(SQL_ABIERTO_DEL_ALMACEN, (co_empresa, co_alma))
        return int(fila["id"]) if fila else None

    def abiertos_por_almacen(self, co_empresa: str) -> Dict[str, int]:
        """{co_alma: id del inventario abierto}."""
        return {(f["co_alma"] or "").strip(): int(f["id"])
                for f in self._cx.consultar(SQL_ABIERTOS_POR_ALMACEN, (co_empresa,))}

    def crear(self, co_empresa: str, co_alma: str, des_alma: str, descripcion: str, usuario: str,
              stock: Sequence[Tuple[str, Decimal]]) -> int:
        """Prepara el inventario y guarda la foto del stock {co_art, stock}."""
        inventario_id = int(self._cx.consultar_uno(
            SQL_CREAR, (co_empresa, co_alma, des_alma or None, descripcion, usuario))["id"])
        self._cx.ejecutar_muchos(SQL_GUARDAR_STOCK, [(inventario_id, co_art, cantidad) for co_art, cantidad in stock])
        return inventario_id

    def cerrar(self, inventario_id: int, usuario: str) -> int:
        """:return: renglones cambiados (0 = ya estaba cerrado)."""
        return self._cx.ejecutar(SQL_CERRAR, (usuario, inventario_id))

    def stock(self, inventario_id: int) -> Dict[str, Decimal]:
        """La foto del stock: {co_art: stock}."""
        return {(f["co_art"] or "").strip(): Decimal(str(f["stock"]))
                for f in self._cx.consultar(SQL_STOCK, (inventario_id,))}

    # ---------- Conteos individuales ----------

    def anotar(self, inventario_id: int, co_art: str, codigo_leido: str, cantidad: Decimal, usuario: str) -> int:
        return int(self._cx.consultar_uno(SQL_ANOTAR, (inventario_id, co_art, codigo_leido, cantidad, usuario))["id"])

    def conteo(self, conteo_id: int) -> Optional[Dict[str, Any]]:
        return self._cx.consultar_uno(SQL_CONTEO, (conteo_id,))

    def borrar(self, conteo_id: int, inventario_id: int) -> int:
        return self._cx.ejecutar(SQL_BORRAR, (conteo_id, inventario_id))

    def contado(self, inventario_id: int, co_art: str, usuario: str) -> Tuple[Decimal, Decimal, int]:
        """(total contado por todos, contado por ese usuario, cuántos conteos tiene) de un artículo."""
        fila = self._cx.consultar_uno(SQL_CONTADO, (usuario, inventario_id, co_art)) or {}
        return (Decimal(str(fila.get("total") or 0)), Decimal(str(fila.get("usuario") or 0)),
                int(fila.get("veces") or 0))

    def totales(self, inventario_id: int) -> Dict[str, Decimal]:
        """{co_art: stock contado} (suma de los conteos de todos)."""
        return {(f["co_art"] or "").strip(): Decimal(str(f["cantidad"]))
                for f in self._cx.consultar(SQL_TOTALES, (inventario_id,))}

    def ultimos(self, inventario_id: int, usuario: str = "", cuantos: int = 30) -> List[Dict[str, Any]]:
        """Los últimos conteos (de ese usuario; "" = de todos)."""
        return self._cx.consultar(SQL_ULTIMOS, (cuantos, inventario_id, usuario, usuario))

    def cuantos_de_usuario(self, inventario_id: int, usuario: str) -> int:
        fila = self._cx.consultar_uno(SQL_CUANTOS_DE_USUARIO, (inventario_id, usuario))
        return int(fila["cuantos"]) if fila else 0
