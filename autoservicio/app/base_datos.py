"""
Conexión a SQL Server (base de datos de Profit Plus 2K8).

Se usa la librería pyodbc con el driver indicado en el XML
(normalmente "ODBC Driver 17 for SQL Server").

Esta clase solo sabe conectarse y ejecutar sentencias. Las consultas
concretas (qué tabla, qué campos) están en el paquete ``repositorios``.

IMPORTANTE: todas las consultas usan parámetros (signos "?") y NUNCA se
arman concatenando texto, para evitar errores con comillas y ataques de
inyección SQL desde el lector de códigos o el teclado.
"""

from __future__ import annotations

import logging
from contextlib import contextmanager
from typing import Any, Dict, Iterator, List, Optional, Sequence

from .configuracion import ConfigSQL

log = logging.getLogger("autoservicio.base_datos")

# Segundos que se espera al servidor antes de dar error de conexión.
TIEMPO_ESPERA_CONEXION = 10


class ErrorBaseDatos(Exception):
    """Error al conectarse o al ejecutar una consulta en SQL Server."""


class BaseDatos:
    """
    Maneja la conexión con SQL Server.

    Se mantiene una sola conexión abierta durante toda la sesión. Si la red
    se cae, la siguiente consulta intenta reconectar automáticamente una vez.
    """

    def __init__(self, config: ConfigSQL):
        self._config = config
        self._conexion = None   # Se abre al primer uso (conexión perezosa).

    # ------------------------------------------------------------------
    # Conexión
    # ------------------------------------------------------------------

    def cadena_conexion(self) -> str:
        """
        Arma la cadena de conexión ODBC.

        - Con usuario en el XML: autenticación de SQL Server (UID/PWD).
        - Sin usuario: autenticación de Windows (Trusted_Connection).
        """
        c = self._config
        partes = [
            f"DRIVER={{{c.driver}}}",
            f"SERVER={c.servidor}",
            f"DATABASE={c.basedatos}",
        ]
        if c.usa_autenticacion_windows:
            partes.append("Trusted_Connection=yes")
        else:
            partes.append(f"UID={c.usuario}")
            partes.append(f"PWD={c.clave}")
        # El Driver 18 exige cifrado por defecto; en la red local se confía en el servidor.
        partes.append("TrustServerCertificate=yes")
        return ";".join(partes) + ";"

    def conectar(self) -> None:
        """Abre la conexión (si no está abierta)."""
        if self._conexion is not None:
            return
        try:
            # Se importa aquí para que el resto de la aplicación (y las pruebas)
            # funcionen aunque pyodbc no esté instalado en la máquina de desarrollo.
            import pyodbc
        except ImportError as error:
            raise ErrorBaseDatos("No está instalada la librería pyodbc (pip install pyodbc).") from error

        try:
            log.info("Conectando a %s / %s", self._config.servidor, self._config.basedatos)
            self._conexion = pyodbc.connect(self.cadena_conexion(),
                                            timeout=TIEMPO_ESPERA_CONEXION,
                                            autocommit=True)
        except pyodbc.Error as error:
            log.exception("No se pudo conectar a SQL Server")
            raise ErrorBaseDatos(
                f"No se pudo conectar al servidor {self._config.servidor}.\n{error}"
            ) from error

    def cerrar(self) -> None:
        """Cierra la conexión (se llama al salir de la aplicación)."""
        if self._conexion is not None:
            try:
                self._conexion.close()
            except Exception:  # noqa: BLE001 - al cerrar no importa el error.
                pass
            self._conexion = None

    @contextmanager
    def _cursor(self) -> Iterator[Any]:
        """
        Entrega un cursor listo para usar.

        Si la conexión se perdió (por ejemplo se reinició el servidor), se
        cierra, se vuelve a abrir y se reintenta una sola vez.
        """
        self.conectar()
        try:
            cursor = self._conexion.cursor()
        except Exception:  # noqa: BLE001 - conexión muerta: reconectamos.
            log.warning("Conexión perdida, reconectando...")
            self.cerrar()
            self.conectar()
            cursor = self._conexion.cursor()
        try:
            yield cursor
        finally:
            cursor.close()

    # ------------------------------------------------------------------
    # Consultas
    # ------------------------------------------------------------------

    def consultar(self, sql: str, parametros: Sequence[Any] = ()) -> List[Dict[str, Any]]:
        """
        Ejecuta un SELECT y devuelve una lista de diccionarios
        (cada fila es {"nombre_columna": valor}).
        """
        try:
            with self._cursor() as cursor:
                cursor.execute(sql, *parametros)
                columnas = [col[0] for col in cursor.description]
                return [dict(zip(columnas, fila)) for fila in cursor.fetchall()]
        except ErrorBaseDatos:
            raise
        except Exception as error:  # noqa: BLE001 - se convierte a nuestro error.
            log.exception("Error en la consulta: %s", sql)
            raise ErrorBaseDatos(f"Error al consultar la base de datos.\n{error}") from error

    def consultar_uno(self, sql: str, parametros: Sequence[Any] = ()) -> Optional[Dict[str, Any]]:
        """Igual que consultar() pero devuelve solo la primera fila (o None)."""
        filas = self.consultar(sql, parametros)
        return filas[0] if filas else None
