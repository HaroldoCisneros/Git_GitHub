"""
Repositorio de artículos: búsqueda, precio e IVA con el procedimiento
almacenado ppv_buscarart (el mismo que usa el punto de venta).

    EXEC ppv_buscarart @lco_art, @lco_usu, @lco_emp, @lco_cli,
                       @ltipo, @CO_ALMA, @TOTAL_ART

Qué resuelve el procedimiento (con @ltipo = 0):
  - Busca el código en co_art, ref, modelo y CODEB01 ... CODEB10
    (solo artículos no anulados).
  - Devuelve el precio en la columna "prec_vta1" según la lista de precios
    del ambiente (VD_LISTPREC = Lista01 ... Lista05 o PRECIO G). Si la
    cantidad (@TOTAL_ART) llega a la cantidad de mayor del artículo, usa las
    listas de mayor del ambiente (VB_LISPMAY / VB_LISPMAY2). Por eso el
    precio se vuelve a pedir cada vez que cambia la cantidad del renglón.
  - Devuelve en "factor" el % de IVA según tipo_imp y la tabla tab_enc.
  - Devuelve además unidad, costos, stock del almacén, etc., que se guardan
    en Articulo.datos para grabar luego el renglón de la factura.

Detalle a tener en cuenta: si el mismo código existe en varios artículos
(por ejemplo es co_art de uno y ref de otro) el procedimiento devuelve
varias filas. Aquí se prefiere la fila cuyo co_art es exactamente el código
escaneado; si no hay, la primera.
"""

from __future__ import annotations

import logging
from decimal import Decimal
from typing import Any, Dict, List, Optional

from ..base_datos import BaseDatos
from ..modelos import Articulo, Sesion

log = logging.getLogger("autoservicio.articulos")

# Llamada al procedimiento. SET NOCOUNT ON evita que SQL Server devuelva
# mensajes de "filas afectadas" antes del resultado.
SQL_BUSCAR_ARTICULO = (
    "SET NOCOUNT ON; "
    "EXEC dbo.ppv_buscarart @lco_art = ?, @lco_usu = ?, @lco_emp = ?, @lco_cli = ?, "
    "@ltipo = ?, @CO_ALMA = ?, @TOTAL_ART = ?"
)

# @ltipo = 0: el precio sale de la lista del ambiente (VD_LISTPREC).
# (@ltipo = 1 lo tomaría del tipo de cliente.)
TIPO_PRECIO_AMBIENTE = 0

# Listas de precios que entiende ppv_buscarart (comparación sin mayúsculas).
LISTAS_VALIDAS = {"LISTA01", "LISTA02", "LISTA03", "LISTA04", "LISTA05", "PRECIO G"}

# Largo máximo de un código (@lco_art es char(30)).
LARGO_MAXIMO_CODIGO = 30


class ErrorListaPrecios(Exception):
    """VD_LISTPREC del ambiente no es una lista que entienda ppv_buscarart."""


def validar_lista_precios(lista: str) -> None:
    """
    Comprueba la lista de precios del ambiente al arrancar. Con una lista
    desconocida ppv_buscarart devuelve precio 0 para todo, así que es mejor
    avisar de una vez.

    :raises ErrorListaPrecios: si no es Lista01 ... Lista05 ni PRECIO G.
    """
    if (lista or "").strip().upper() not in LISTAS_VALIDAS:
        raise ErrorListaPrecios(
            f"La lista de precios del ambiente (VD_LISTPREC = '{lista}') no es válida.\n"
            "Debe ser Lista01 ... Lista05 o PRECIO G.")


class RepositorioArticulos:
    """Búsqueda de artículos con su precio e IVA (ppv_buscarart)."""

    def __init__(self, bd: BaseDatos, sesion: Sesion):
        """
        :param sesion: de aquí salen el usuario, la empresa y el almacén
            (VD_ALMACEN) que se le pasan al procedimiento.
        :raises ErrorListaPrecios: si VD_LISTPREC no es válida.
        """
        validar_lista_precios(sesion.ambiente.lista_precios)
        self._bd = bd
        self._usuario = sesion.usuario.codigo
        self._empresa = sesion.ambiente.empresa
        self._almacen = sesion.ambiente.almacen or ""

    def buscar(self, codigo: str, codigo_cliente: str,
               cantidad: Decimal = Decimal("1")) -> Optional[Articulo]:
        """
        Busca el artículo y su precio para la cantidad indicada.

        :param codigo: lo escaneado (co_art, código de barras, ref o modelo),
            o el co_art cuando se vuelve a calcular el precio de un renglón.
        :param codigo_cliente: co_cli del cliente de la factura.
        :param cantidad: cantidad total del renglón (define si aplica precio de mayor).
        :return: el Articulo, o None si el código no existe.
        """
        codigo = (codigo or "").strip()
        if not codigo or len(codigo) > LARGO_MAXIMO_CODIGO:
            return None

        filas = self._bd.consultar(SQL_BUSCAR_ARTICULO, (
            codigo, self._usuario, self._empresa, codigo_cliente or "",
            TIPO_PRECIO_AMBIENTE, self._almacen, cantidad,
        ))
        fila = self._elegir_fila(filas, codigo)
        if fila is None:
            return None

        return Articulo(
            codigo=str(fila.get("co_art") or "").strip(),
            descripcion=str(fila.get("art_des") or "").strip(),
            # El procedimiento devuelve el precio calculado en la columna "prec_vta1".
            precio=Decimal(str(fila.get("prec_vta1") or 0)),
            tipo_impuesto=str(fila.get("tipo_imp") or "").strip(),
            porcentaje_impuesto=Decimal(str(fila.get("factor") or 0)),
            datos=fila,
        )

    @staticmethod
    def _elegir_fila(filas: List[Dict[str, Any]], codigo: str) -> Optional[Dict[str, Any]]:
        """
        Elige la fila a usar cuando el procedimiento devuelve varias.
        Las columnas se pasan a minúsculas para no depender de cómo vienen.
        """
        if not filas:
            return None
        filas = [{str(k).lower(): v for k, v in fila.items()} for fila in filas]
        if len(filas) > 1:
            log.warning("El código %s corresponde a %d artículos", codigo, len(filas))
        for fila in filas:
            if str(fila.get("co_art") or "").strip() == codigo:
                return fila
        return filas[0]
