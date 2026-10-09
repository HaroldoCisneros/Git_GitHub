"""
Pantalla de inicio de sesión.

Muestra la caja en la que está instalada la aplicación, pide usuario y
contraseña con el teclado en pantalla (siempre visible) y los valida contra
la tabla de usuarios de la aplicación web.

Cuando el usuario es válido emite la señal ``usuario_validado`` con el
objeto Usuario; quien escucha (la clase Aplicacion) abre la pantalla
principal.
"""

from __future__ import annotations

import logging
import os

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (QFormLayout, QFrame, QHBoxLayout, QLabel, QLineEdit,
                               QPushButton, QVBoxLayout)

from ..base_datos import ErrorBaseDatos
from ..configuracion import Configuracion
from ..modelos import Caja, Usuario
from ..repositorios import RepositorioUsuarios
from .dialogos import mostrar_mensaje
from .teclado import MODO_ALFANUMERICO, TecladoVirtual
from .ventana_kiosco import VentanaKiosco

log = logging.getLogger("autoservicio.login")

# Alto máximo del logo en esta pantalla (px).
ALTO_LOGO = 140


class VentanaLogin(VentanaKiosco):
    """Pantalla de inicio de sesión del usuario."""

    # Se emite con el Usuario validado.
    usuario_validado = Signal(object)

    def __init__(self, config: Configuracion, caja: Caja, repo_usuarios: RepositorioUsuarios):
        super().__init__(config.seguridad.clave_salida)
        self._config = config
        self._caja = caja
        self._repo_usuarios = repo_usuarios
        self._construir()

    # ------------------------------------------------------------------
    # Construcción de la pantalla
    # ------------------------------------------------------------------

    def _construir(self) -> None:
        """Arma los controles: encabezado, formulario y teclado."""
        principal = QVBoxLayout(self)
        principal.setContentsMargins(40, 30, 40, 30)
        principal.setSpacing(20)

        # --- Encabezado: logo (o nombre del negocio) y botón Salir ---
        encabezado = QHBoxLayout()
        encabezado.addWidget(self._crear_logo())
        encabezado.addStretch()
        salir = QPushButton("Salir")
        salir.setProperty("tipo", "peligro")
        salir.setFocusPolicy(Qt.NoFocus)
        salir.clicked.connect(self.solicitar_salida)
        encabezado.addWidget(salir, alignment=Qt.AlignTop)
        principal.addLayout(encabezado)

        # --- Panel central con el formulario ---
        panel = QFrame()
        panel.setObjectName("panel")
        panel_layout = QVBoxLayout(panel)
        panel_layout.setContentsMargins(40, 30, 40, 30)

        titulo = QLabel("Inicio de sesión")
        titulo.setObjectName("titulo")
        titulo.setAlignment(Qt.AlignCenter)
        panel_layout.addWidget(titulo)

        caja = QLabel(f"Caja {self._caja.codigo} - {self._caja.descripcion}")
        caja.setObjectName("subtitulo")
        caja.setAlignment(Qt.AlignCenter)
        panel_layout.addWidget(caja)

        formulario = QFormLayout()
        formulario.setSpacing(16)
        self.campo_usuario = QLineEdit()
        self.campo_clave = QLineEdit()
        self.campo_clave.setEchoMode(QLineEdit.Password)
        formulario.addRow("Usuario:", self.campo_usuario)
        formulario.addRow("Contraseña:", self.campo_clave)
        panel_layout.addLayout(formulario)

        # Mensaje de error (vacío mientras no haya error).
        self.etiqueta_error = QLabel("")
        self.etiqueta_error.setObjectName("error")
        self.etiqueta_error.setAlignment(Qt.AlignCenter)
        panel_layout.addWidget(self.etiqueta_error)

        principal.addWidget(panel)

        # --- Teclado siempre visible, escribe en el campo con el foco ---
        self.teclado = TecladoVirtual(MODO_ALFANUMERICO, seguir_foco=True)
        self.teclado.set_destino(self.campo_usuario)
        self.teclado.aceptado.connect(self._al_aceptar)
        principal.addWidget(self.teclado, stretch=1)

        # Enter desde un teclado físico: igual que tocar Aceptar.
        self.campo_usuario.returnPressed.connect(self._al_aceptar)
        self.campo_clave.returnPressed.connect(self._al_aceptar)

        self.campo_usuario.setFocus()

    def _crear_logo(self) -> QLabel:
        """Devuelve el logo del XML; si no existe, el nombre del negocio en texto."""
        etiqueta = QLabel()
        ruta_logo = self._config.rutas.logo
        if ruta_logo and os.path.isfile(ruta_logo):
            etiqueta.setPixmap(QPixmap(ruta_logo).scaledToHeight(ALTO_LOGO, Qt.SmoothTransformation))
        else:
            etiqueta.setText(self._config.pantalla.nombre_negocio)
            etiqueta.setObjectName("titulo")
        return etiqueta

    # ------------------------------------------------------------------
    # Lógica
    # ------------------------------------------------------------------

    def _al_aceptar(self) -> None:
        """
        Se ejecuta al tocar "Aceptar" (o Enter).
        Si está en el campo usuario pasa a la contraseña; si está en la
        contraseña intenta iniciar sesión.
        """
        if self.campo_usuario.hasFocus() and not self.campo_clave.text():
            self.campo_clave.setFocus()
            return
        self._iniciar_sesion()

    def _iniciar_sesion(self) -> None:
        """Valida usuario y contraseña contra la base de datos."""
        usuario = self.campo_usuario.text().strip()
        clave = self.campo_clave.text()

        if not usuario or not clave:
            self.etiqueta_error.setText("Escriba usuario y contraseña")
            return

        try:
            resultado: Usuario | None = self._repo_usuarios.validar(usuario, clave)
        except ErrorBaseDatos as error:
            mostrar_mensaje(self, "Error", str(error), es_error=True)
            return

        if resultado is None:
            self.etiqueta_error.setText("Usuario o contraseña incorrectos")
            self.campo_clave.clear()
            self.campo_clave.setFocus()
            return

        log.info("Inicio de sesión: %s en caja %s", resultado.codigo, self._caja.codigo)
        self.usuario_validado.emit(resultado)

    def limpiar(self) -> None:
        """Deja la pantalla lista para otro inicio de sesión."""
        self.campo_usuario.clear()
        self.campo_clave.clear()
        self.etiqueta_error.clear()
        self.campo_usuario.setFocus()
