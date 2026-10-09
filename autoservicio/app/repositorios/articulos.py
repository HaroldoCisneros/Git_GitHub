"""
Repositorio de la tabla "art" (artículos) de Profit Plus 2K8.

Se usa cada vez que se escanea un código de barras.

Búsqueda del código escaneado, en este orden (gana el primero que encuentre):
  1. Por co_art (en cualquier estado), igual que el Sistema Web.
  2. Por los códigos de barras CODEB01 ... CODEB10, solo artículos activos.
  3. Por referencia (campo ref), solo artículos activos (Sistema Web).
  4. Por modelo (campo modelo), solo artículos activos (Sistema Web).

Precio: se usa la lista de precios del ambiente del usuario (VD_LISTPREC).
El número de lista (1 a 5) indica la columna de "art": prec_vta1 ... prec_vta5.

PENDIENTE DE CONFIRMAR:
  - El formato exacto de VD_LISTPREC. Se toma el dígito que contenga
    ("1", "PREC1", "LISTA 3"...).
  - Cómo se obtiene el % de IVA según tipo_imp. Por ahora 0 %.
  - Si se debe impedir vender artículos anulados encontrados por co_art.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Optional

from ..base_datos import BaseDatos
from ..modelos import Articulo

# Columnas de precio permitidas (lista blanca: la columna va dentro del SQL).
COLUMNAS_PRECIO = {n: f"prec_vta{n}" for n in range(1, 6)}


class ErrorListaPrecios(Exception):
    """VD_LISTPREC del ambiente no indica una lista de precios válida (1 a 5)."""


def columna_precio(lista_precios: str) -> str:
    """
    Convierte el VD_LISTPREC del ambiente en la columna de precio de "art".

    Ejemplos: "1" -> prec_vta1, "PREC3" -> prec_vta3.
    :raises ErrorListaPrecios: si no trae un número del 1 al 5.
    """
    digitos = re.findall(r"\d", lista_precios or "")
    if len(digitos) != 1 or int(digitos[0]) not in COLUMNAS_PRECIO:
        raise ErrorListaPrecios(
            f"La lista de precios del ambiente (VD_LISTPREC = '{lista_precios}') "
            "no es válida: debe indicar una lista del 1 al 5.")
    return COLUMNAS_PRECIO[int(digitos[0])]


# Columnas de códigos de barras de la tabla art.
COLUMNAS_CODIGO_BARRAS = [f"CODEB{n:02d}" for n in range(1, 11)]

# Búsqueda de un artículo por código: co_art, códigos de barras, ref y modelo.
# El mismo código se pasa en cada "?" (ver CANTIDAD_PARAMETROS).
# {precio} es la columna de la lista de precios (prec_vta1 ... prec_vta5).
SQL_ARTICULO_POR_CODIGO = """
    SELECT TOP 1 RTRIM(A.co_art)   AS codigo,
                 RTRIM(A.art_des)  AS descripcion,
                 A.{precio}        AS precio,
                 RTRIM(A.tipo_imp) AS tipo_impuesto
      FROM (SELECT co_art, 1 AS prioridad FROM art WHERE co_art = ?
            UNION ALL
            SELECT co_art, 2 FROM art WHERE anulado = 0 AND ? IN ({codigos_barras})
            UNION ALL
            SELECT co_art, 3 FROM art WHERE anulado = 0 AND ref = ?
            UNION ALL
            SELECT co_art, 4 FROM art WHERE anulado = 0 AND modelo = ?) T
      JOIN art A ON A.co_art = T.co_art
     ORDER BY T.prioridad
"""

# Cantidad de "?" de la consulta (el mismo código va en todos).
CANTIDAD_PARAMETROS = 4

# Largo máximo de un código (co_art es char(30)); uno más largo es una
# lectura errónea del lector.
LARGO_MAXIMO_CODIGO = 30


class RepositorioArticulos:
    """Consultas sobre la tabla de artículos."""

    def __init__(self, bd: BaseDatos, lista_precios: str):
        """
        :param lista_precios: VD_LISTPREC del ambiente.
        :raises ErrorListaPrecios: si la lista no es válida.
        """
        self._bd = bd
        self._sql = SQL_ARTICULO_POR_CODIGO.format(
            precio=columna_precio(lista_precios),
            codigos_barras=", ".join(COLUMNAS_CODIGO_BARRAS))

    def buscar_por_codigo(self, codigo: str) -> Optional[Articulo]:
        """
        Busca el artículo que corresponde al código escaneado.

        :return: el Articulo, o None si el código no existe.
        """
        codigo = (codigo or "").strip()
        if not codigo or len(codigo) > LARGO_MAXIMO_CODIGO:
            return None

        fila = self._bd.consultar_uno(self._sql, (codigo,) * CANTIDAD_PARAMETROS)
        if fila is None:
            return None

        return Articulo(
            codigo=fila["codigo"],
            descripcion=fila["descripcion"] or "",
            precio=Decimal(str(fila["precio"] or 0)),
            tipo_impuesto=fila["tipo_impuesto"] or "",
            porcentaje_impuesto=Decimal("0"),  # PENDIENTE: % según tipo_imp.
        )
