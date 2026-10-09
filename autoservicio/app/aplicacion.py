"""
Controlador general de la aplicación.

Coordina el arranque:

    1. Lee el config.xml.
    2. Valida el usuario y la clave del XML contra MasterProfit.dbo.employee
       (no se piden en pantalla).
    3. Carga el ambiente del usuario (tabla PPV_AMBIENTE) con todos sus
       parámetros.
    4. Valida que la caja del ambiente (VD_CAJA) exista en la tabla cajas.
    5. Valida la lista de precios del ambiente (VD_LISTPREC); el precio y el
       IVA de cada artículo los calcula el procedimiento ppv_buscarart.
    6. Abre la pantalla principal (escaneo / factura).

Si algo falla al arrancar (falta el XML, no hay conexión, el usuario o la
clave no son correctos, el usuario no tiene ambiente, la caja o la lista de
precios del ambiente no son válidas) se muestra el error en pantalla y la
aplicación termina sin entrar.
"""

from __future__ import annotations

import logging
from typing import Optional

from PySide6.QtWidgets import QApplication

from . import NOMBRE_APLICACION, VERSION
from .base_datos import BaseDatos, ErrorBaseDatos
from .configuracion import Configuracion, ErrorConfiguracion, cargar_configuracion
from .modelos import Sesion
from .repositorios import (RepositorioAmbiente, RepositorioArticulos, RepositorioCajas,
                           RepositorioClientes, RepositorioUsuarios)
from .repositorios.ambiente import TABLA_AMBIENTE
from .repositorios.articulos import ErrorListaPrecios
from .repositorios.usuarios import ErrorUsuario
from .ui.dialogos import mostrar_mensaje
from .ui.estilos import HOJA_ESTILOS
from .ui.ventana_principal import VentanaPrincipal

log = logging.getLogger("autoservicio.aplicacion")


class Aplicacion:
    """Arranca la aplicación: validaciones iniciales y pantalla principal."""

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
        self.sesion: Optional[Sesion] = None     # Caja + usuario + ambiente validados.
        self._ventana_principal: Optional[VentanaPrincipal] = None

    # ------------------------------------------------------------------
    # Arranque
    # ------------------------------------------------------------------

    def iniciar(self) -> bool:
        """
        Hace las validaciones iniciales y muestra la pantalla principal.

        :return: False si hubo un error que impide arrancar (ya se mostró al usuario).
        """
        log.info("Iniciando %s v%s", NOMBRE_APLICACION, VERSION)

        # 1. Configuración.
        try:
            self.config = cargar_configuracion()
        except ErrorConfiguracion as error:
            return self._error_fatal("Error de configuración", str(error))

        self.bd = BaseDatos(self.config.sql)
        try:
            # 2. Usuario y clave del XML contra la tabla employee.
            usuario = RepositorioUsuarios(self.bd, self.config.usuario).validar()

            # 3. El usuario debe tener un ambiente creado en PPV_AMBIENTE.
            ambiente = RepositorioAmbiente(self.bd, self.config.ambiente).obtener(usuario.codigo)
            if ambiente is None:
                return self._error_fatal(
                    "Usuario sin ambiente",
                    f"El usuario '{usuario.codigo}' no tiene un ambiente creado para la "
                    f"empresa '{self.config.ambiente.cod_emp}' (tabla {TABLA_AMBIENTE}).")

            # 4. La caja sale del ambiente (VD_CAJA) y debe existir en Profit.
            codigo_caja = ambiente.caja or ""
            caja = RepositorioCajas(self.bd).obtener(codigo_caja) if codigo_caja else None
            if caja is None:
                return self._error_fatal(
                    "Caja no válida",
                    f"La caja del ambiente del usuario '{usuario.codigo}' "
                    f"(VD_CAJA = '{codigo_caja}') no existe en Profit.")
            log.info("Caja validada: %s - %s", caja.codigo, caja.descripcion)

            self.sesion = Sesion(caja=caja, usuario=usuario, ambiente=ambiente)

            # 5. Lista de precios del ambiente (VD_LISTPREC).
            repo_articulos = RepositorioArticulos(self.bd, self.sesion)

        except ErrorBaseDatos as error:
            return self._error_fatal("Error de conexión", str(error))
        except ErrorUsuario as error:
            return self._error_fatal("Usuario no válido", str(error))
        except ErrorListaPrecios as error:
            return self._error_fatal("Lista de precios no válida", str(error))

        # 6. Pantalla principal.
        self._ventana_principal = VentanaPrincipal(
            self.config, self.sesion, RepositorioClientes(self.bd), repo_articulos)
        self._ventana_principal.mostrar(self._pantalla_completa)
        return True

    def finalizar(self) -> None:
        """Libera recursos al salir (cierra la conexión a la base de datos)."""
        if self.bd is not None:
            self.bd.cerrar()
        log.info("Aplicación cerrada")

    # ------------------------------------------------------------------
    # Errores
    # ------------------------------------------------------------------

    @staticmethod
    def _error_fatal(titulo: str, texto: str) -> bool:
        """Anota el error, lo muestra en pantalla y devuelve False."""
        log.error("%s: %s", titulo, texto)
        mostrar_mensaje(None, titulo, texto + "\n\nLa aplicación se cerrará.", es_error=True)
        return False
