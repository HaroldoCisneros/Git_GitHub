"""
Repositorio de la tabla "cajas" de Profit Plus 2K8.

Se usa al arrancar para comprobar que el código de caja del config.xml
existe en Profit.

PENDIENTE DE CONFIRMAR: nombres de columnas (se asumen cod_caja y descrip).
"""

from __future__ import annotations

from typing import Optional

from ..base_datos import BaseDatos
from ..modelos import Caja

# Consulta de una caja por su código.
SQL_CAJA_POR_CODIGO = """
    SELECT RTRIM(cod_caja) AS codigo,
           RTRIM(descrip)  AS descripcion
      FROM cajas
     WHERE RTRIM(cod_caja) = ?
"""


class RepositorioCajas:
    """Consultas sobre la tabla de cajas."""

    def __init__(self, bd: BaseDatos):
        self._bd = bd

    def obtener(self, codigo: str) -> Optional[Caja]:
        """
        Busca la caja por su código.

        :return: la Caja, o None si el código no existe en Profit.
        """
        fila = self._bd.consultar_uno(SQL_CAJA_POR_CODIGO, (codigo.strip(),))
        if fila is None:
            return None
        return Caja(codigo=fila["codigo"], descripcion=fila["descripcion"] or "")
