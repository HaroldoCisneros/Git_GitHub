"""
Ventanas emergentes táctiles.

Los cuadros de diálogo estándar de Qt (QMessageBox, QInputDialog) tienen
botones pequeños y no muestran teclado, así que no sirven en una pantalla
táctil. Aquí están las versiones propias de la aplicación:

    pedir_texto(...)      Pide un dato mostrando el teclado en pantalla
                          (opcionalmente con un botón extra, ver OPCION_EXTRA).
    mostrar_mensaje(...)  Muestra un aviso o un error con un botón grande.
    confirmar(...)        Pregunta Sí / No con botones grandes.

Todas son modales: bloquean la ventana de atrás hasta que se respondan.
"""

from __future__ import annotations

from typing import Optional

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (QDialog, QHBoxLayout, QLabel, QLineEdit, QPushButton,
                               QVBoxLayout, QWidget)

from .teclado import MODO_ALFANUMERICO, MODO_NUMERICO, TecladoVirtual

# Valor que devuelve pedir_texto() cuando se toca el botón extra
# (por ejemplo "Usar cliente por defecto" en la pantalla de la cédula).
OPCION_EXTRA = object()

# Código de resultado del diálogo para el botón extra (Accepted = 1, Rejected = 0).
_RESULTADO_EXTRA = 2

# Anchos de los diálogos según el tipo de teclado (px).
ANCHO_DIALOGO_NUMERICO = 560
ANCHO_DIALOGO_ALFANUMERICO = 1100
ANCHO_DIALOGO_MENSAJE = 700


class _DialogoBase(QDialog):
    """Diálogo sin barra de título (no se puede cerrar con la "X" ni moverse)."""

    def __init__(self, parent: Optional[QWidget]):
        super().__init__(parent, Qt.Dialog | Qt.FramelessWindowHint)
        self.setModal(True)

    def keyPressEvent(self, evento) -> None:
        """Ignora la tecla Escape para que el diálogo no se cierre "por accidente"."""
        if evento.key() == Qt.Key_Escape:
            return
        super().keyPressEvent(evento)


# --------------------------------------------------------------------------
# Pedir un dato con teclado
# --------------------------------------------------------------------------

class DialogoEntrada(_DialogoBase):
    """
    Pide un dato al usuario mostrando el teclado en pantalla.

    Se usa, por ejemplo, para la cédula del cliente, para teclear un código
    de barras que no se pudo escanear o para la contraseña de salida.
    """

    def __init__(self, parent: Optional[QWidget], titulo: str, mensaje: str = "",
                 modo: str = MODO_NUMERICO, oculto: bool = False,
                 permitir_cancelar: bool = True, texto_inicial: str = "",
                 boton_extra: Optional[str] = None):
        """
        :param titulo: texto grande de arriba.
        :param mensaje: explicación debajo del título.
        :param modo: tipo de teclado (MODO_NUMERICO o MODO_ALFANUMERICO).
        :param oculto: True para contraseñas (muestra puntos en vez de números).
        :param permitir_cancelar: si es False no aparece el botón Cancelar.
        :param texto_inicial: valor con el que arranca el campo.
        :param boton_extra: texto de un botón adicional (opcional). Al tocarlo
            el diálogo se cierra y pedir_texto() devuelve OPCION_EXTRA.
        """
        super().__init__(parent)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(14)

        # --- Título y mensaje ---
        etiqueta_titulo = QLabel(titulo)
        etiqueta_titulo.setObjectName("titulo")
        etiqueta_titulo.setAlignment(Qt.AlignCenter)
        layout.addWidget(etiqueta_titulo)

        if mensaje:
            etiqueta_mensaje = QLabel(mensaje)
            etiqueta_mensaje.setObjectName("subtitulo")
            etiqueta_mensaje.setAlignment(Qt.AlignCenter)
            etiqueta_mensaje.setWordWrap(True)
            layout.addWidget(etiqueta_mensaje)

        # --- Campo donde se escribe ---
        self.campo = QLineEdit(texto_inicial)
        self.campo.setAlignment(Qt.AlignCenter)
        if oculto:
            self.campo.setEchoMode(QLineEdit.Password)
        # Enter (por ejemplo desde un lector o teclado físico) equivale a Aceptar.
        self.campo.returnPressed.connect(self._aceptar)
        layout.addWidget(self.campo)

        # --- Teclado en pantalla ---
        self.teclado = TecladoVirtual(modo)
        self.teclado.set_destino(self.campo)
        self.teclado.aceptado.connect(self._aceptar)
        layout.addWidget(self.teclado)

        # --- Botón extra (opcional) ---
        if boton_extra:
            extra = QPushButton(boton_extra)
            extra.setFocusPolicy(Qt.NoFocus)
            extra.clicked.connect(lambda: self.done(_RESULTADO_EXTRA))
            layout.addWidget(extra)

        # --- Botón cancelar ---
        if permitir_cancelar:
            cancelar = QPushButton("Cancelar")
            cancelar.setProperty("tipo", "peligro")
            cancelar.setFocusPolicy(Qt.NoFocus)
            cancelar.clicked.connect(self.reject)
            layout.addWidget(cancelar)

        self.setFixedWidth(ANCHO_DIALOGO_NUMERICO if modo == MODO_NUMERICO
                           else ANCHO_DIALOGO_ALFANUMERICO)
        self.campo.setFocus()

    def _aceptar(self) -> None:
        """Cierra el diálogo con "aceptar" solo si se escribió algo."""
        if self.campo.text().strip():
            self.accept()

    def valor(self) -> str:
        """Texto escrito, sin espacios a los lados."""
        return self.campo.text().strip()


def pedir_texto(parent: Optional[QWidget], titulo: str, mensaje: str = "",
                modo: str = MODO_NUMERICO, oculto: bool = False,
                permitir_cancelar: bool = True, texto_inicial: str = "",
                boton_extra: Optional[str] = None):
    """
    Muestra un DialogoEntrada y espera la respuesta.

    :return: el texto escrito; None si el usuario tocó Cancelar; u
        OPCION_EXTRA si tocó el botón extra.
    """
    dialogo = DialogoEntrada(parent, titulo, mensaje, modo, oculto,
                             permitir_cancelar, texto_inicial, boton_extra)
    resultado = dialogo.exec()
    if resultado == _RESULTADO_EXTRA:
        return OPCION_EXTRA
    if resultado == QDialog.Accepted:
        return dialogo.valor()
    return None


# --------------------------------------------------------------------------
# Mensajes y confirmaciones
# --------------------------------------------------------------------------

class DialogoMensaje(_DialogoBase):
    """Aviso con un título, un texto y uno o dos botones grandes."""

    def __init__(self, parent: Optional[QWidget], titulo: str, texto: str,
                 es_error: bool = False, texto_si: str = "Aceptar",
                 texto_no: Optional[str] = None):
        """
        :param es_error: pinta el título en rojo.
        :param texto_si: texto del botón principal.
        :param texto_no: si se indica, aparece un segundo botón (para preguntas Sí/No).
        """
        super().__init__(parent)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(30, 30, 30, 30)
        layout.setSpacing(20)

        etiqueta_titulo = QLabel(titulo)
        etiqueta_titulo.setObjectName("error" if es_error else "titulo")
        etiqueta_titulo.setStyleSheet("font-size: 30px;")
        etiqueta_titulo.setAlignment(Qt.AlignCenter)
        layout.addWidget(etiqueta_titulo)

        etiqueta_texto = QLabel(texto)
        etiqueta_texto.setWordWrap(True)
        etiqueta_texto.setAlignment(Qt.AlignCenter)
        etiqueta_texto.setStyleSheet("font-size: 24px;")
        layout.addWidget(etiqueta_texto)

        botones = QHBoxLayout()
        if texto_no:
            no = QPushButton(texto_no)
            no.setProperty("tipo", "peligro")
            no.clicked.connect(self.reject)
            botones.addWidget(no)
        si = QPushButton(texto_si)
        si.setProperty("tipo", "exito")
        si.clicked.connect(self.accept)
        si.setDefault(True)
        botones.addWidget(si)
        layout.addLayout(botones)

        self.setFixedWidth(ANCHO_DIALOGO_MENSAJE)


def mostrar_mensaje(parent: Optional[QWidget], titulo: str, texto: str,
                    es_error: bool = False) -> None:
    """Muestra un aviso y espera a que se toque "Aceptar"."""
    DialogoMensaje(parent, titulo, texto, es_error).exec()


def confirmar(parent: Optional[QWidget], titulo: str, texto: str,
              texto_si: str = "Sí", texto_no: str = "No") -> bool:
    """Pregunta Sí / No. Devuelve True si se tocó "Sí"."""
    return DialogoMensaje(parent, titulo, texto, False, texto_si, texto_no).exec() == QDialog.Accepted


# Se reexportan las constantes de modo para que las pantallas no tengan que
# importar el módulo del teclado.
__all__ = ["pedir_texto", "OPCION_EXTRA", "mostrar_mensaje", "confirmar", "DialogoEntrada",
           "DialogoMensaje", "MODO_NUMERICO", "MODO_ALFANUMERICO"]
