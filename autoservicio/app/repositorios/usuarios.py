"""
Repositorio de usuarios (validación del inicio de sesión).

El usuario se valida contra la MISMA tabla de usuarios que usa la
aplicación web.

PENDIENTE: todavía no se conoce esa tabla. Cuando se tenga hay que:
  1. Escribir la consulta en SQL_USUARIO (nombre de tabla y columnas).
  2. Ajustar ``_clave_coincide`` según cómo se guarda la contraseña
     (texto plano, MD5, SHA-256, bcrypt...).
Mientras SQL_USUARIO esté vacío, el login muestra un aviso claro en pantalla.
"""

from __future__ import annotations

import logging
from typing import Optional

from ..base_datos import BaseDatos, ErrorBaseDatos
from ..modelos import Usuario

log = logging.getLogger("autoservicio.usuarios")

# Consulta que debe devolver las columnas: codigo, nombre, clave
# filtrando por el usuario (un solo parámetro "?").
# Ejemplo (A CONFIRMAR):
#   SELECT RTRIM(usuario) AS codigo, RTRIM(nombre) AS nombre, clave
#     FROM usuarios WHERE usuario = ? AND activo = 1
SQL_USUARIO = ""


class RepositorioUsuarios:
    """Validación de usuarios contra la tabla de la aplicación web."""

    def __init__(self, bd: BaseDatos):
        self._bd = bd

    def validar(self, usuario: str, clave: str) -> Optional[Usuario]:
        """
        Comprueba usuario y contraseña.

        :return: el Usuario si los datos son correctos, None si no lo son.
        :raises ErrorBaseDatos: si la consulta todavía no está definida o falla.
        """
        if not SQL_USUARIO.strip():
            raise ErrorBaseDatos(
                "La validación de usuarios aún no está configurada.\n"
                "Falta definir la tabla de usuarios de la aplicación web."
            )

        fila = self._bd.consultar_uno(SQL_USUARIO, (usuario.strip(),))
        if fila is None or not self._clave_coincide(clave, fila.get("clave")):
            log.info("Intento de inicio de sesión fallido: %s", usuario)
            return None

        return Usuario(codigo=str(fila["codigo"]).strip(),
                       nombre=str(fila.get("nombre") or "").strip())

    @staticmethod
    def _clave_coincide(clave_escrita: str, clave_guardada) -> bool:
        """
        Compara la clave escrita con la guardada en la tabla.

        PENDIENTE: hoy compara texto plano. Si la web guarda la clave cifrada
        (hash), aquí se debe aplicar el mismo cifrado antes de comparar.
        """
        if clave_guardada is None:
            return False
        return clave_escrita == str(clave_guardada).strip()
