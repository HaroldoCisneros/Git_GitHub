"""
Ventana base "modo kiosco".

Todas las pantallas principales de la aplicación heredan de esta clase.
Se encarga de:

    - Mostrarse a pantalla completa y sin bordes.
    - Impedir que se cierre con Alt+F4 o con la "X".
    - Pedir la contraseña de salida (requerimiento: provisionalmente
      9898989898, configurable en <seguridad><clave_salida> del XML).
"""

from __future__ import annotations

import logging
from typing import Optional

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QWidget

from .dialogos import MODO_NUMERICO, mostrar_mensaje, pedir_texto

log = logging.getLogger("autoservicio.kiosco")


class VentanaKiosco(QWidget):
    """Ventana a pantalla completa que solo se cierra con contraseña."""

    def __init__(self, clave_salida: str, parent: Optional[QWidget] = None):
        """
        :param clave_salida: contraseña que hay que escribir para salir.
        """
        super().__init__(parent, Qt.Window | Qt.FramelessWindowHint)
        self._clave_salida = clave_salida
        # Cuando es True la ventana sí se deja cerrar (salida autorizada o
        # cambio de pantalla hecho por el propio programa).
        self._cierre_permitido = False

    # ------------------------------------------------------------------
    # Mostrar / cerrar
    # ------------------------------------------------------------------

    def mostrar(self, pantalla_completa: bool = True) -> None:
        """
        Muestra la ventana.

        :param pantalla_completa: False solo para pruebas en el equipo de desarrollo.
        """
        if pantalla_completa:
            self.showFullScreen()
        else:
            self.resize(1280, 800)
            self.show()

    def cerrar_desde_programa(self) -> None:
        """Cierra la ventana sin pedir contraseña (lo usa el programa al cambiar de pantalla)."""
        self._cierre_permitido = True
        self.close()

    def closeEvent(self, evento) -> None:
        """Bloquea el cierre (Alt+F4, X) si no fue autorizado."""
        if self._cierre_permitido:
            evento.accept()
        else:
            evento.ignore()

    # ------------------------------------------------------------------
    # Salida con contraseña
    # ------------------------------------------------------------------

    def solicitar_salida(self) -> None:
        """
        Pide la contraseña de salida con el teclado numérico.
        Si es correcta, cierra toda la aplicación.
        """
        clave = pedir_texto(self, "Salir de la aplicación",
                            "Introduzca la contraseña de salida",
                            modo=MODO_NUMERICO, oculto=True)
        if clave is None:          # Tocó "Cancelar".
            return

        if clave == self._clave_salida:
            log.info("Salida autorizada de la aplicación")
            self._cierre_permitido = True
            QApplication.instance().quit()
        else:
            log.warning("Intento de salida con contraseña incorrecta")
            mostrar_mensaje(self, "Contraseña incorrecta",
                            "La contraseña de salida no es válida.", es_error=True)
