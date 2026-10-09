"""
Repositorio de usuarios: validación del usuario del config.xml.

El usuario y la clave NO se piden en pantalla: vienen en la sección
<usuario> del config.xml y se validan al arrancar contra la tabla de
usuarios de Profit (por defecto MasterProfit.dbo.employee), igual que lo
hace el Sistema Web:

    1. El código se pasa a mayúsculas (así se guardan en Profit).
    2. Se lee el usuario en la tabla employee.
    3. Debe estar activo (activo = 1) y con Estado = 'A'.
    4. La clave debe coincidir usando la encriptación de Profit
       (ver app/encriptacion_profit.py).

Si algo no se cumple, la aplicación muestra el error y no entra.

La tabla está en OTRA base de datos (MasterProfit), por eso la consulta usa
el nombre completo [base].[dbo].[tabla]. El usuario de SQL del config.xml
necesita permiso de lectura sobre esa tabla.
"""

from __future__ import annotations

import logging

from ..base_datos import BaseDatos
from ..configuracion import ConfigUsuario
from ..encriptacion_profit import clave_coincide
from ..modelos import Usuario

log = logging.getLogger("autoservicio.usuarios")

# Consulta del usuario. {base} y {tabla} vienen del config.xml y ya se
# validó que solo tengan letras, números y "_" (ver configuracion.py), por
# eso pueden ir dentro del texto SQL. El código va como parámetro (?).
# La clave se lee como VARBINARY para obtener los bytes exactos.
SQL_EMPLEADO = (
    "SELECT employee_i, last_name, CAST(password AS VARBINARY(15)) AS clave, "
    "prioridad, mapa, activo, Estado AS estado "
    "FROM [{base}].[dbo].[{tabla}] WHERE employee_i = ?"
)


class ErrorUsuario(Exception):
    """El usuario del config.xml no existe, está inactivo o la clave no coincide."""


class RepositorioUsuarios:
    """Validación del usuario contra la tabla employee de Profit."""

    def __init__(self, bd: BaseDatos, config: ConfigUsuario):
        self._bd = bd
        self._config = config

    def validar(self) -> Usuario:
        """
        Valida el usuario y la clave del config.xml.

        :return: el Usuario si todo es correcto.
        :raises ErrorUsuario: con el motivo, si no es válido.
        :raises ErrorBaseDatos: si no se pudo consultar la tabla.
        """
        codigo = self._config.codigo.strip().upper()
        sql = SQL_EMPLEADO.format(base=self._config.base, tabla=self._config.tabla)
        fila = self._bd.consultar_uno(sql, (codigo,))

        # Se dice el motivo exacto porque este error lo ve quien instala la
        # caja (no un cliente) y le sirve para corregir el config.xml.
        if fila is None:
            raise ErrorUsuario(f"El usuario '{codigo}' no existe en "
                               f"{self._config.base}.dbo.{self._config.tabla}.")

        estado = (fila.get("estado") or "").strip().upper()
        if not fila.get("activo") or estado != "A":
            raise ErrorUsuario(f"El usuario '{codigo}' no está activo en Profit.")

        if not clave_coincide(self._config.clave, fila.get("clave"),
                              fila.get("prioridad"), fila.get("mapa") or ""):
            log.warning("Clave incorrecta para el usuario %s", codigo)
            raise ErrorUsuario(f"La clave del usuario '{codigo}' no es correcta.")

        nombre = (fila.get("last_name") or "").strip()
        log.info("Usuario validado: %s - %s", codigo, nombre)
        return Usuario(codigo=codigo, nombre=nombre)
