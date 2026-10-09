"""
Punto de entrada de la Aplicación de Autoservicio.

Uso:
    python main.py              Pantalla completa (modo normal en la caja).
    python main.py --ventana    En una ventana normal, para pruebas.

El archivo config.xml debe estar en la misma carpeta que este programa.
"""

from __future__ import annotations

import sys

from PySide6.QtWidgets import QApplication

from app import NOMBRE_APLICACION
from app.aplicacion import Aplicacion
from app.registro import configurar_registro


def main() -> int:
    """Crea la aplicación Qt, arranca el controlador y entra al ciclo de eventos."""
    configurar_registro()

    qt_app = QApplication(sys.argv)
    qt_app.setApplicationName(NOMBRE_APLICACION)

    pantalla_completa = "--ventana" not in sys.argv
    aplicacion = Aplicacion(qt_app, pantalla_completa=pantalla_completa)

    if not aplicacion.iniciar():
        aplicacion.finalizar()
        return 1

    codigo_salida = qt_app.exec()
    aplicacion.finalizar()
    return codigo_salida


if __name__ == "__main__":
    sys.exit(main())
