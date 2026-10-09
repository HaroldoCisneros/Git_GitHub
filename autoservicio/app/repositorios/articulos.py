"""
Repositorio de la tabla "art" (artículos) de Profit Plus 2K8.

Se usa cada vez que se escanea un código de barras.

Búsqueda del código (igual que el Sistema Web, inventario/servicios.py):
  1. Por co_art (en cualquier estado).
  2. Si no existe, por referencia (campo ref), solo artículos activos.
  3. Si no existe, por modelo (campo modelo), solo artículos activos.
La referencia y el modelo son los que permiten encontrar el artículo al
escanear su código de barras.

PENDIENTE DE CONFIRMAR:
  - Qué lista de precios se usa. Por ahora prec_vta1.
  - Cómo se obtiene el % de IVA según tipo_imp. Por ahora 0 %.
  - Si se debe impedir vender artículos anulados encontrados por co_art.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Optional

from ..base_datos import BaseDatos
from ..modelos import Articulo

# Búsqueda de un artículo por código: co_art, luego ref, luego modelo.
# El mismo código se pasa tres veces (uno por cada "?").
SQL_ARTICULO_POR_CODIGO = """
    SELECT TOP 1 RTRIM(A.co_art)   AS codigo,
                 RTRIM(A.art_des)  AS descripcion,
                 A.prec_vta1       AS precio,
                 RTRIM(A.tipo_imp) AS tipo_impuesto
      FROM (SELECT co_art, 1 AS prioridad FROM art WHERE co_art = ?
            UNION ALL
            SELECT co_art, 2 FROM art WHERE anulado = 0 AND ref = ?
            UNION ALL
            SELECT co_art, 3 FROM art WHERE anulado = 0 AND modelo = ?) T
      JOIN art A ON A.co_art = T.co_art
     ORDER BY T.prioridad
"""

# Largo máximo de un código; uno más largo es una lectura errónea del lector.
LARGO_MAXIMO_CODIGO = 40


class RepositorioArticulos:
    """Consultas sobre la tabla de artículos."""

    def __init__(self, bd: BaseDatos):
        self._bd = bd

    def buscar_por_codigo(self, codigo: str) -> Optional[Articulo]:
        """
        Busca el artículo que corresponde al código escaneado.

        :return: el Articulo, o None si el código no existe.
        """
        codigo = (codigo or "").strip()
        if not codigo or len(codigo) > LARGO_MAXIMO_CODIGO:
            return None

        fila = self._bd.consultar_uno(SQL_ARTICULO_POR_CODIGO, (codigo, codigo, codigo))
        if fila is None:
            return None

        return Articulo(
            codigo=fila["codigo"],
            descripcion=fila["descripcion"] or "",
            precio=Decimal(str(fila["precio"] or 0)),
            tipo_impuesto=fila["tipo_impuesto"] or "",
            porcentaje_impuesto=Decimal("0"),  # PENDIENTE: % según tipo_imp.
        )
