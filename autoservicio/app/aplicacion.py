"""
Controlador general de la aplicación.

Coordina el arranque y el paso entre pantallas:

    1. Lee el config.xml.
    2. Se conecta a SQL Server y valida que la caja del XML exista en Profit.
    3. Muestra la pantalla de inicio de sesión.
    4. Con el usuario validado, abre la pantalla principal (escaneo / factura).

Si algo falla al arrancar (falta el XML, no hay conexión, la caja no
existe) se muestra el error en pantalla y la aplicación termina.
"""

from __future__ import annotations

import logging
from typing import Optional

from PySide6.QtWidgets import QApplication

from . import NOMBRE_APLICACION, VERSION
from .base_datos import BaseDatos, ErrorBaseDatos
from .configuracion import Configuracion, ErrorConfiguracion, cargar_configuracion
from .modelos import Caja, Usuario
from .repositorios import (RepositorioArticulos, RepositorioCajas, RepositorioClientes,
                           RepositorioUsuarios)
from .ui.dialogos import mostrar_mensaje
from .ui.estilos import HOJA_ESTILOS
from .ui.ventana_login import VentanaLogin
from .ui.ventana_principal import VentanaPrincipal

log = logging.getLogger("autoservicio.aplicacion")


class Aplicacion:
    """Arranca la aplicación y maneja el cambio entre pantallas."""

    def __init__(self, qt_app: QApplication, pantalla_completa: bool = True):
        """
        :param qt_app: la QApplication ya creada en main.py.
        :param pantalla_completa: False para probar en ventana normal.
        """
        self._qt_app = qt_app
        self._pantalla_completa = pantalla_completa
        self._qt_app.setStyleSheet(HOJA_ESTILOS)

        # Se completan en iniciar().
        self.config: Optional[Configuracion] = None
        self.bd: Optional[BaseDatos] = None
        self.caja: Optional[Caja] = None
        self._ventana_login: Optional[VentanaLogin] = None
        self._ventana_principal: Optional[VentanaPrincipal] = None

    # ------------------------------------------------------------------
    # Arranque
    # ------------------------------------------------------------------

    def iniciar(self) -> bool:
        """
        Prepara todo y muestra la pantalla de inicio de sesión.

        :return: False si hubo un error que impide arrancar (ya se mostró al usuario).
        """
        log.info("Iniciando %s v%s", NOMBRE_APLICACION, VERSION)

        # 1. Configuración.
        try:
            self.config = cargar_configuracion()
        except ErrorConfiguracion as error:
            return self._error_fatal("Error de configuración", str(error))

        # 2. Base de datos y validación de la caja.
        self.bd = BaseDatos(self.config.sql)
        try:
            self.caja = RepositorioCajas(self.bd).obtener(self.config.caja.codigo)
        except ErrorBaseDatos as error:
            return self._error_fatal("Error de conexión", str(error))

        if self.caja is None:
            return self._error_fatal(
                "Caja no válida",
                f"La caja '{self.config.caja.codigo}' indicada en config.xml "
                "no existe en Profit.")

        log.info("Caja validada: %s - %s", self.caja.codigo, self.caja.descripcion)

        # 3. Pantalla de inicio de sesión.
        self._ventana_login = VentanaLogin(self.config, self.caja, RepositorioUsuarios(self.bd))
        self._ventana_login.usuario_validado.connect(self._abrir_principal)
        self._ventana_login.mostrar(self._pantalla_completa)
        return True

    def finalizar(self) -> None:
        """Libera recursos al salir (cierra la conexión a la base de datos)."""
        if self.bd is not None:
            self.bd.cerrar()
        log.info("Aplicación cerrada")

    # ------------------------------------------------------------------
    # Cambio de pantallas
    # ------------------------------------------------------------------

    def _abrir_principal(self, usuario: Usuario) -> None:
        """Con el usuario validado, cambia del login a la pantalla de escaneo."""
        self._ventana_principal = VentanaPrincipal(
            self.config, self.caja, usuario,
            RepositorioClientes(self.bd), RepositorioArticulos(self.bd))
        self._ventana_principal.mostrar(self._pantalla_completa)
        self._ventana_login.cerrar_desde_programa()

    # ------------------------------------------------------------------
    # Errores
    # ------------------------------------------------------------------

    @staticmethod
    def _error_fatal(titulo: str, texto: str) -> bool:
        """Anota el error, lo muestra en pantalla y devuelve False."""
        log.error("%s: %s", titulo, texto)
        mostrar_mensaje(None, titulo, texto + "\n\nLa aplicación se cerrará.", es_error=True)
        return False
