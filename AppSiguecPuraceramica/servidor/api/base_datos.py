"""
Conexión a SQL Server (bases de Profit Plus 2K12 y base SIGUEC_PURACERAMICA de la App).

Se usa pyodbc con el driver del config.xml (normalmente "ODBC Driver 17 for
SQL Server"). Es la misma base técnica del Autoservicio, con
una diferencia: aquí atienden VARIOS teléfonos a la vez, así que cada petición
abre su propia conexión con ``conexion(base)`` (pyodbc la toma de su "pool", por
eso es rápido) y la cierra al terminar.

    with bd.conexion("PURACERAMICA") as cx:      # una transacción
        filas = cx.consultar("SELECT ... WHERE co_art = ?", (codigo,))
        cx.ejecutar("UPDATE ...", (...))
    # Al salir sin error: COMMIT. Si hubo un error: ROLLBACK (se deshace todo).

Esta clase solo sabe conectarse y ejecutar. Las consultas (qué tabla, qué
campos) están en el paquete ``repositorios``.

IMPORTANTE: todas las consultas usan parámetros (signos "?") y NUNCA se arman
concatenando lo que escribe el usuario, para evitar la inyección SQL.
"""

from __future__ import annotations

import logging
import re
import threading
from contextlib import contextmanager
from typing import Any, Dict, Iterator, List, Optional, Sequence

from .configuracion import ConfigSQL

log = logging.getLogger("siguec.base_datos")

# Segundos que se espera al servidor antes de dar error de conexión.
TIEMPO_ESPERA_CONEXION = 10

# Nombre válido de una base de datos: solo letras, números y guion bajo. Se
# revisa antes de ponerlo en la cadena de conexión (un ";" permitiría cambiarla).
PATRON_NOMBRE_BASE = re.compile(r"^[A-Za-z0-9_]{1,128}$")

# Drivers de SQL Server, del más nuevo al más viejo: si el del XML no está
# instalado se usa el primero de estos que sí esté (igual que el Autoservicio).
DRIVERS_SQL_SERVER = (
    "ODBC Driver 18 for SQL Server",
    "ODBC Driver 17 for SQL Server",
    "ODBC Driver 13.1 for SQL Server",
    "ODBC Driver 13 for SQL Server",
    "ODBC Driver 11 for SQL Server",
    "SQL Server Native Client 11.0",
    "SQL Server Native Client 10.0",
    "SQL Server",
)


class ErrorBaseDatos(Exception):
    """Error al conectarse o al ejecutar una consulta en SQL Server."""

    @property
    def mensaje_servidor(self) -> str:
        """El mensaje de SQL Server sin el encabezado del driver ODBC."""
        texto = str(self.__cause__ or self)
        if "[SQL Server]" in texto:
            texto = texto.split("[SQL Server]")[1]
            texto = re.sub(r"\s*\(\d+\)\s*(\(SQL\w+\))?.*$", "", texto, flags=re.S)
        return texto.strip(" '\")")


def elegir_driver(configurado: str, instalados) -> str:
    """El driver del XML si está instalado; si no, el mejor de SQL Server que haya."""
    instalados = list(instalados)
    if configurado in instalados:
        return configurado
    for driver in DRIVERS_SQL_SERVER:
        if driver in instalados:
            log.warning("El driver '%s' del config.xml no está instalado; se usa '%s'", configurado, driver)
            return driver
    raise ErrorBaseDatos(
        f"Este equipo no tiene un driver ODBC de SQL Server (se pidió '{configurado}'). "
        "Instale \"ODBC Driver 17 for SQL Server\" de Microsoft (64 bits).")


def validar_nombre_base(base: str) -> str:
    """Devuelve el nombre si es válido; si no, ErrorBaseDatos."""
    if not PATRON_NOMBRE_BASE.match(base or ""):
        raise ErrorBaseDatos(f"Nombre de base de datos no válido: '{base}'.")
    return base


class Conexion:
    """
    Una conexión abierta dentro de una transacción (la entrega ``BaseDatos.conexion``).

    Las filas se devuelven como diccionarios {"columna": valor}; los nombres de
    columna se pasan a minúsculas para no depender de cómo están escritos en Profit.
    """

    def __init__(self, conexion_odbc, base: str):
        self._cx = conexion_odbc
        self.base = base

    def consultar(self, sql: str, parametros: Sequence[Any] = ()) -> List[Dict[str, Any]]:
        """Ejecuta un SELECT y devuelve la lista de filas."""
        cursor = self._cx.cursor()
        try:
            cursor.execute(sql, *parametros)
            # Si el SQL empieza con SET NOCOUNT / DECLARE puede haber resultados
            # vacíos antes del SELECT: se avanza hasta el primero con columnas.
            while cursor.description is None:
                if not cursor.nextset():
                    return []
            columnas = [col[0].lower() for col in cursor.description]
            return [dict(zip(columnas, fila)) for fila in cursor.fetchall()]
        except ErrorBaseDatos:
            raise
        except Exception as error:  # noqa: BLE001 - se convierte a nuestro error.
            log.exception("Error en la consulta (%s): %s", self.base, sql)
            raise ErrorBaseDatos(f"Error al consultar la base de datos: {error}") from error
        finally:
            cursor.close()

    def consultar_uno(self, sql: str, parametros: Sequence[Any] = ()) -> Optional[Dict[str, Any]]:
        """Igual que consultar() pero solo la primera fila (o None)."""
        filas = self.consultar(sql, parametros)
        return filas[0] if filas else None

    def ejecutar_muchos(self, sql: str, filas: Sequence[Sequence[Any]]) -> int:
        """
        El mismo INSERT / UPDATE para muchas filas de una vez (lo usa la
        sincronización de artículos). fast_executemany manda todo en bloque:
        miles de artículos en segundos en vez de minutos.
        """
        if not filas:
            return 0
        cursor = self._cx.cursor()
        try:
            cursor.fast_executemany = True
            cursor.executemany(sql, [tuple(f) for f in filas])
            return len(filas)
        except Exception as error:  # noqa: BLE001 - se convierte a nuestro error.
            log.exception("Error al grabar en bloque (%s): %s", self.base, sql)
            raise ErrorBaseDatos(f"Error al grabar en la base de datos: {error}") from error
        finally:
            cursor.close()

    def ejecutar(self, sql: str, parametros: Sequence[Any] = ()) -> int:
        """Ejecuta INSERT / UPDATE / DELETE. Devuelve la cantidad de filas afectadas."""
        cursor = self._cx.cursor()
        try:
            cursor.execute(sql, *parametros)
            return cursor.rowcount
        except Exception as error:  # noqa: BLE001 - se convierte a nuestro error.
            log.exception("Error al ejecutar (%s): %s", self.base, sql)
            raise ErrorBaseDatos(f"Error al grabar en la base de datos: {error}") from error
        finally:
            cursor.close()


class BaseDatos:
    """Abre conexiones a cualquier base del SQL Server configurado."""

    def __init__(self, config: ConfigSQL):
        self._config = config
        self._driver: Optional[str] = None   # Se elige una vez, en la primera conexión.
        self._candado = threading.Lock()

    def cadena_conexion(self, base: str, driver: str = "") -> str:
        """
        Cadena ODBC para la base indicada.

        - Con usuario en el XML: autenticación de SQL Server (UID/PWD).
        - Sin usuario: autenticación de Windows (Trusted_Connection).
        """
        c = self._config
        partes = [
            f"DRIVER={{{driver or c.driver}}}",
            f"SERVER={c.servidor}",
            f"DATABASE={validar_nombre_base(base)}",
        ]
        if c.usa_autenticacion_windows:
            partes.append("Trusted_Connection=yes")
        else:
            partes.append(f"UID={c.usuario}")
            partes.append(f"PWD={{{c.clave}}}")   # Entre llaves por si la clave tiene ";".
        # El Driver 18 exige cifrado por defecto; en la red local se confía en el servidor.
        partes.append("TrustServerCertificate=yes")
        partes.append("APP=AppSiguecPuraceramica")
        return ";".join(partes) + ";"

    def _pyodbc(self):
        try:
            # Se importa aquí para que las pruebas funcionen sin pyodbc instalado.
            import pyodbc
        except ImportError as error:
            raise ErrorBaseDatos("No está instalada la librería pyodbc (pip install pyodbc).") from error
        with self._candado:
            if self._driver is None:
                self._driver = elegir_driver(self._config.driver, pyodbc.drivers())
        return pyodbc

    @contextmanager
    def conexion(self, base: str) -> Iterator[Conexion]:
        """
        Abre una conexión a ``base`` con una transacción.

        Al salir sin error se confirma (COMMIT); si hubo un error se deshace (ROLLBACK).
        """
        pyodbc = self._pyodbc()
        try:
            cx = pyodbc.connect(self.cadena_conexion(base, self._driver),
                                timeout=TIEMPO_ESPERA_CONEXION, autocommit=False)
        except pyodbc.Error as error:
            log.exception("No se pudo conectar a %s / %s", self._config.servidor, base)
            raise ErrorBaseDatos(f"No se pudo conectar a la base {base} del servidor "
                                 f"{self._config.servidor}.") from error
        try:
            yield Conexion(cx, base)
            cx.commit()
        except BaseException:
            try:
                cx.rollback()
            except Exception:  # noqa: BLE001 - si la conexión murió no hay nada que deshacer.
                pass
            raise
        finally:
            try:
                cx.close()
            except Exception:  # noqa: BLE001 - al cerrar no importa el error.
                pass
