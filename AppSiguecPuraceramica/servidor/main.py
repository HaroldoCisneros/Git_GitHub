"""
Punto de entrada del servidor de AppSiguecPuraceramica.

    python main.py                      Arranca la API con el config.xml de esta carpeta.
    python main.py --config otro.xml    Con otro archivo de configuración.

La API queda escuchando en <servidor_web><host>:<puerto> (por defecto
127.0.0.1:8000). Para llegar desde internet se pone delante un túnel o un
proxy con HTTPS (ver README.md, "Publicar en internet").
Los errores quedan en siguec_puraceramica.log junto a este archivo.
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from logging.handlers import RotatingFileHandler

CARPETA = os.path.dirname(os.path.abspath(__file__))


def configurar_log() -> None:
    """Log en pantalla y en siguec_puraceramica.log (5 archivos de 2 MB como máximo)."""
    formato = logging.Formatter("%(asctime)s %(levelname)s %(name)s: %(message)s")
    archivo = RotatingFileHandler(os.path.join(CARPETA, "siguec_puraceramica.log"),
                                  maxBytes=2_000_000, backupCount=5, encoding="utf-8")
    archivo.setFormatter(formato)
    pantalla = logging.StreamHandler()
    pantalla.setFormatter(formato)
    raiz = logging.getLogger()
    raiz.setLevel(logging.INFO)
    raiz.addHandler(archivo)
    raiz.addHandler(pantalla)


def main() -> int:
    parser = argparse.ArgumentParser(description="Servidor de AppSiguecPuraceramica")
    parser.add_argument("--config", default=os.path.join(CARPETA, "config.xml"))
    argumentos = parser.parse_args()

    configurar_log()
    from api.aplicacion import crear_aplicacion
    from api.configuracion import ErrorConfiguracion, leer_configuracion

    try:
        config = leer_configuracion(argumentos.config)
    except ErrorConfiguracion as error:
        logging.getLogger("siguec").error("%s", error)
        return 1

    import uvicorn

    app = crear_aplicacion(config)
    web = config.servidor_web
    logging.getLogger("siguec").info("AppSiguecPuraceramica escuchando en http://%s:%s", web.host, web.puerto)
    # proxy_headers: detrás del túnel / proxy, la IP real del teléfono viene en X-Forwarded-For.
    uvicorn.run(app, host=web.host, port=web.puerto, proxy_headers=True,
                forwarded_allow_ips="127.0.0.1", log_config=None)
    return 0


if __name__ == "__main__":
    sys.exit(main())
