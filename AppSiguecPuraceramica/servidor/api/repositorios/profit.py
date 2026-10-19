"""
Lectura de Profit Plus 2K12 (SOLO LECTURA: la App nunca escribe en Profit).

En 2K12 las tablas cambian respecto a 2K8:

    2K8            2K12              Para qué la usa la App
    ART        ->  saArticulo        Sincronizar artículos (co_art, art_des, anulado, modelo, ref)
    ALMACEN    ->  saAlmacen         Elegir el almacén al preparar el inventario
    ST_ALMAC   ->  saStockAlmacen    Foto del stock del almacén al preparar
                                     (un renglón por artículo, almacén y TIPO de stock:
                                     ACT actual, COM comprometido, LLE por llegar...)

Los nombres de tablas y columnas salen de la sección <profit> del config.xml
(ConfigProfit), por si en la base real alguno es distinto. Como no pueden ir
como parámetros "?", se validan al leer el config.xml (solo letras, números y
guion bajo) y además se escriben entre corchetes.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Dict, List, Sequence, Set

from ..base_datos import Conexion
from ..configuracion import ConfigProfit

SQL_COLUMNAS = "SELECT LOWER(COLUMN_NAME) AS columna FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?"


def _n(nombre: str) -> str:
    """Nombre entre corchetes: [saArticulo]."""
    return f"[{nombre}]"


def sql_articulos(cfg: ConfigProfit, con_anulado: bool, extras: Sequence[str]) -> str:
    """SELECT de todos los artículos con las columnas de código que existan."""
    anulado = f"{_n(cfg.col_anulado)} AS anulado" if con_anulado else "0 AS anulado"
    columnas = "".join(f", RTRIM(CAST({_n(c)} AS varchar(60))) AS {_n('x_' + c.lower())}" for c in extras)
    return (f"SELECT RTRIM({_n(cfg.col_codigo)}) AS co_art, RTRIM({_n(cfg.col_descripcion)}) AS art_des, "
            f"{anulado}{columnas} FROM dbo.{_n(cfg.tabla_articulos)}")


def sql_codigos_barra(cfg: ConfigProfit) -> str:
    return (f"SELECT RTRIM(CAST({_n(cfg.col_codigo_barra)} AS varchar(60))) AS codigo, "
            f"RTRIM({_n(cfg.col_codigo_barra_articulo)}) AS co_art FROM dbo.{_n(cfg.tabla_codigos_barra)}")


def sql_almacenes(cfg: ConfigProfit) -> str:
    descripcion = (f"RTRIM({_n(cfg.col_almacen_descripcion)})" if cfg.col_almacen_descripcion else "''")
    return (f"SELECT RTRIM({_n(cfg.col_almacen)}) AS co_alma, {descripcion} AS des_alma "
            f"FROM dbo.{_n(cfg.tabla_almacenes)} ORDER BY {_n(cfg.col_almacen)}")


def sql_stock_almacen(cfg: ConfigProfit) -> str:
    """Stock actual de cada artículo en UN almacén (suma por si hay varias unidades)."""
    tipo = f" AND {_n(cfg.col_stock_tipo)} = ?" if cfg.col_stock_tipo else ""
    return (f"SELECT RTRIM({_n(cfg.col_stock_articulo)}) AS co_art, SUM({_n(cfg.col_stock_cantidad)}) AS stock "
            f"FROM dbo.{_n(cfg.tabla_stock)} WHERE {_n(cfg.col_stock_almacen)} = ?{tipo} "
            f"GROUP BY {_n(cfg.col_stock_articulo)}")


class RepositorioProfit:
    """Consultas a la base de UNA empresa de Profit Plus 2K12."""

    def __init__(self, cx: Conexion, cfg: ConfigProfit):
        self._cx = cx
        self._cfg = cfg

    def columnas(self, tabla: str) -> Set[str]:
        """Columnas (en minúsculas) que tiene la tabla en esta base; vacío si no existe."""
        return {f["columna"] for f in self._cx.consultar(SQL_COLUMNAS, (tabla,))}

    def articulos(self, con_anulado: bool, extras: Sequence[str]) -> List[Dict]:
        """
        Todos los artículos: co_art, art_des, anulado y x_<columna> por cada columna extra
        (modelo, ref...) que también sirve como código para escanear.
        """
        return self._cx.consultar(sql_articulos(self._cfg, con_anulado, extras))

    def codigos_barra(self) -> List[Dict]:
        """Códigos de la tabla aparte de códigos de barra (si está configurada)."""
        return self._cx.consultar(sql_codigos_barra(self._cfg))

    def almacenes(self) -> List[Dict]:
        return self._cx.consultar(sql_almacenes(self._cfg))

    def stock_almacen(self, co_alma: str) -> Dict[str, Decimal]:
        """{co_art: stock actual} de ese almacén."""
        parametros = (co_alma, self._cfg.tipo_stock_actual) if self._cfg.col_stock_tipo else (co_alma,)
        return {(f.get("co_art") or "").strip(): Decimal(str(f.get("stock") or 0))
                for f in self._cx.consultar(sql_stock_almacen(self._cfg), parametros)}
