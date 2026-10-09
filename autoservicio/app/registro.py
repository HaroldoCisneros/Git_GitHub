"""
Bitácora (log) de la aplicación.

Como la aplicación corre a pantalla completa en un equipo de autoservicio,
no hay consola donde ver los errores. Por eso todo se anota en el archivo
"autoservicio.log" junto al programa. El archivo rota al llegar a 1 MB y se
guardan 5 copias, para que nunca llene el disco.
"""

from __future__ import annotations

import logging
import os
from logging.handlers import RotatingFileHandler

from .utilidades import carpeta_programa

# Nombre del archivo de bitácora (se crea junto al programa).
ARCHIVO_LOG = "autoservicio.log"


def configurar_registro(nivel: int = logging.INFO) -> logging.Logger:
    """
    Prepara la bitácora y devuelve el logger raíz de la aplicación.

    Se llama una sola vez al arrancar (desde main.py). Los demás módulos solo
    hacen ``logging.getLogger(__name__)`` y escriben.
    """
    logger = logging.getLogger("autoservicio")
    logger.setLevel(nivel)

    # Evita agregar manejadores repetidos si se llama dos veces.
    if logger.handlers:
        return logger

    formato = logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")

    # Manejador del archivo con rotación.
    try:
        archivo = RotatingFileHandler(
            os.path.join(carpeta_programa(), ARCHIVO_LOG),
            maxBytes=1_000_000, backupCount=5, encoding="utf-8",
        )
        archivo.setFormatter(formato)
        logger.addHandler(archivo)
    except OSError:
        # Si la carpeta es de solo lectura, seguimos sin archivo de bitácora.
        pass

    # También a la consola (útil mientras se desarrolla).
    consola = logging.StreamHandler()
    consola.setFormatter(formato)
    logger.addHandler(consola)

    return logger
