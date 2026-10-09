"""
Repositorio de la tabla PPV_AMBIENTE.

Cada usuario debe tener un "ambiente" creado para la empresa: una fila de
PPV_AMBIENTE con la llave COD_EMP + COD_USU, que trae todos sus parámetros
(almacén, vendedor, moneda, lista de precios, caja, impresora, permisos...).

Al arrancar, después de validar el usuario en employee, se busca su
ambiente. Si no existe, la aplicación muestra el error y no entra.

La tabla está en la base de datos de la empresa (COD_EMP = nombre de esa
base, por ejemplo PRADO_25), que es la misma de la conexión.

Se leen TODAS las columnas (SELECT *) para que cualquier parámetro quede
disponible en el objeto Ambiente (ver app/modelos.py).
"""

from __future__ import annotations

import logging
from typing import Optional

from ..base_datos import BaseDatos
from ..configuracion import ConfigAmbiente
from ..modelos import Ambiente

log = logging.getLogger("autoservicio.ambiente")

# Nombre de la tabla (en la base de la empresa).
TABLA_AMBIENTE = "[dbo].[PPV_AMBIENTE]"

SQL_AMBIENTE = f"SELECT * FROM {TABLA_AMBIENTE} WHERE COD_EMP = ? AND COD_USU = ?"


class RepositorioAmbiente:
    """Lectura del ambiente del usuario."""

    def __init__(self, bd: BaseDatos, config: ConfigAmbiente):
        self._bd = bd
        self._config = config

    def obtener(self, codigo_usuario: str) -> Optional[Ambiente]:
        """
        Busca el ambiente del usuario para la empresa del config.xml.

        :param codigo_usuario: código del usuario ya validado (employee_i).
        :return: el Ambiente con todas sus columnas, o None si no tiene uno creado.
        """
        fila = self._bd.consultar_uno(SQL_AMBIENTE, (self._config.cod_emp, codigo_usuario.strip().upper()))
        if fila is None:
            return None
        ambiente = Ambiente(fila)
        log.info("Ambiente cargado: empresa=%s usuario=%s (%d parámetros)",
                 ambiente.empresa, ambiente.usuario, len(fila))
        return ambiente
