"""
Repositorio de la tabla "art" (artículos) de Profit Plus 2K8.

Se usa cada vez que se escanea un código de barras.

PENDIENTE DE CONFIRMAR (con la consulta del Visor de Precios):
  - En qué campo/tabla está el código de barras. Por ahora se busca en co_art.
  - Qué lista de precios se usa. Por ahora prec_vta1.
  - Cómo se obtiene el % de IVA según tipo_imp. Por ahora 0 %.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Optional

from ..base_datos import BaseDatos
from ..modelos import Articulo

# Búsqueda de un artículo por código.
SQL_ARTICULO_POR_CODIGO = """
    SELECT RTRIM(co_art)   AS codigo,
           RTRIM(art_des)  AS descripcion,
           prec_vta1       AS precio,
           RTRIM(tipo_imp) AS tipo_impuesto
      FROM art
     WHERE RTRIM(co_art) = ?
"""


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
        if not codigo:
            return None

        fila = self._bd.consultar_uno(SQL_ARTICULO_POR_CODIGO, (codigo,))
        if fila is None:
            return None

        return Articulo(
            codigo=fila["codigo"],
            descripcion=fila["descripcion"] or "",
            precio=Decimal(str(fila["precio"] or 0)),
            tipo_impuesto=fila["tipo_impuesto"] or "",
            porcentaje_impuesto=Decimal("0"),  # PENDIENTE: % según tipo_imp.
        )
