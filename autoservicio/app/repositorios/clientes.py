"""
Repositorio de la tabla "clientes" de Profit Plus 2K8.

Al iniciar cada factura se pide la cédula del cliente y se busca aquí.

En Profit la cédula puede estar guardada de varias formas ("V-12345678",
"V12345678", "12345678") en el campo rif, o como código del cliente
(co_cli). Por eso se le quitan guiones, puntos y espacios y se comparan las
variantes con y sin la letra.

Si la cédula no está registrada, el cliente puede tocar el botón de
"cliente por defecto": se usa el cliente VD_CLIENTE del ambiente, que se
busca por su código con ``buscar_por_codigo``.
"""

from __future__ import annotations

from typing import Optional

from ..base_datos import BaseDatos
from ..modelos import Cliente
from ..utilidades import solo_digitos

# Expresión SQL que limpia un campo de guiones, puntos y espacios.
_LIMPIAR = "REPLACE(REPLACE(REPLACE(RTRIM({campo}), '-', ''), '.', ''), ' ', '')"

# Busca por rif o por código de cliente, en cualquiera de las variantes.
SQL_CLIENTE_POR_CEDULA = f"""
    SELECT TOP 1 RTRIM(co_cli)  AS codigo,
                 RTRIM(cli_des) AS nombre,
                 RTRIM(rif)     AS rif
      FROM clientes
     WHERE {_LIMPIAR.format(campo="rif")}    IN (?, ?, ?)
        OR {_LIMPIAR.format(campo="co_cli")} IN (?, ?, ?)
"""

# Busca un cliente por su código (co_cli).
SQL_CLIENTE_POR_CODIGO = """
    SELECT RTRIM(co_cli)  AS codigo,
           RTRIM(cli_des) AS nombre,
           RTRIM(rif)     AS rif
      FROM clientes
     WHERE co_cli = ?
"""


class RepositorioClientes:
    """Consultas sobre la tabla de clientes."""

    def __init__(self, bd: BaseDatos):
        self._bd = bd

    def buscar_por_cedula(self, cedula: str) -> Optional[Cliente]:
        """
        Busca un cliente por cédula o RIF.

        :param cedula: lo que escribió el cliente (puede traer letra y guiones).
        :return: el Cliente, o None si no está registrado.
        """
        numero = solo_digitos(cedula)
        if not numero:
            return None

        # Variantes: solo número, con V (venezolano) y con E (extranjero).
        variantes = (numero, "V" + numero, "E" + numero)
        fila = self._bd.consultar_uno(SQL_CLIENTE_POR_CEDULA, variantes + variantes)
        if fila is None:
            return None
        return Cliente(codigo=fila["codigo"], nombre=fila["nombre"] or "", rif=fila["rif"] or "")

    def buscar_por_codigo(self, codigo: str) -> Optional[Cliente]:
        """
        Busca un cliente por su código (se usa para el cliente por defecto del ambiente).

        :return: el Cliente, o None si el código no existe.
        """
        codigo = (codigo or "").strip()
        if not codigo:
            return None
        fila = self._bd.consultar_uno(SQL_CLIENTE_POR_CODIGO, (codigo,))
        if fila is None:
            return None
        return Cliente(codigo=fila["codigo"], nombre=fila["nombre"] or "", rif=fila["rif"] or "")
