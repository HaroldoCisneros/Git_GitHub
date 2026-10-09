"""
Teclado virtual en pantalla.

Como la aplicación es táctil y no hay teclado físico, cada vez que el usuario
debe escribir algo se muestra este teclado. Tiene dos modos:

    - "alfanumerico": letras (con Ñ), números, guion, punto y espacio.
    - "numerico":     teclado tipo calculadora (para cédula, claves, cantidades).

Funcionamiento:
    El teclado escribe en un QLineEdit llamado "destino". Se puede fijar el
    destino a mano con ``set_destino()`` o pedirle que siga automáticamente al
    campo que tenga el foco (``seguir_foco=True``), útil cuando la pantalla
    tiene varios campos (por ejemplo usuario y clave).

    Las teclas tienen la política "NoFocus" para que al tocarlas no le quiten
    el foco al campo donde se está escribiendo.

Señales:
    aceptado   -> se tocó la tecla "Aceptar".
"""

from __future__ import annotations

from typing import Optional

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (QApplication, QGridLayout, QHBoxLayout, QLineEdit,
                               QPushButton, QSizePolicy, QVBoxLayout, QWidget)

MODO_ALFANUMERICO = "alfanumerico"
MODO_NUMERICO = "numerico"

# Filas del teclado alfanumérico. Las teclas especiales se agregan aparte.
FILAS_ALFANUMERICAS = [
    "1234567890",
    "QWERTYUIOP",
    "ASDFGHJKLÑ",
    "ZXCVBNM-.",
]

# Distribución del teclado numérico (como un teléfono/calculadora).
FILAS_NUMERICAS = [
    "789",
    "456",
    "123",
]

# Textos de las teclas especiales.
TECLA_BORRAR = "⌫"
TECLA_MAYUSCULAS = "⇧"
TECLA_LIMPIAR = "Limpiar"
TECLA_ESPACIO = "Espacio"
TECLA_ACEPTAR = "Aceptar"


class TecladoVirtual(QWidget):
    """Teclado en pantalla que escribe en un QLineEdit."""

    # Se emite al tocar "Aceptar".
    aceptado = Signal()

    def __init__(self, modo: str = MODO_ALFANUMERICO, seguir_foco: bool = False,
                 parent: Optional[QWidget] = None):
        """
        :param modo: MODO_ALFANUMERICO o MODO_NUMERICO.
        :param seguir_foco: si es True, el teclado escribe en el QLineEdit que
            tenga el foco en cada momento.
        """
        super().__init__(parent)
        self._modo = modo
        self._destino: Optional[QLineEdit] = None
        self._mayusculas = True           # Arranca en mayúsculas (códigos, nombres).
        self._teclas_letras: list[QPushButton] = []   # Para cambiar mayús/minús.

        if modo == MODO_NUMERICO:
            self._construir_numerico()
        else:
            self._construir_alfanumerico()

        if seguir_foco:
            QApplication.instance().focusChanged.connect(self._al_cambiar_foco)

    # ------------------------------------------------------------------
    # API pública
    # ------------------------------------------------------------------

    def set_destino(self, campo: Optional[QLineEdit]) -> None:
        """Indica en qué campo debe escribir el teclado."""
        self._destino = campo

    # ------------------------------------------------------------------
    # Construcción de los botones
    # ------------------------------------------------------------------

    def _crear_tecla(self, texto: str, accion, checkable: bool = False) -> QPushButton:
        """
        Crea un botón de tecla con el estilo del teclado.

        :param accion: función que se ejecuta al tocar la tecla.
        """
        boton = QPushButton(texto)
        boton.setProperty("tipo", "tecla")
        boton.setFocusPolicy(Qt.NoFocus)          # No robar el foco al campo.
        boton.setCheckable(checkable)
        boton.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        boton.clicked.connect(accion)
        return boton

    def _crear_tecla_caracter(self, caracter: str) -> QPushButton:
        """Crea una tecla que escribe un carácter (letra, número o símbolo)."""
        boton = self._crear_tecla(caracter, lambda: self._escribir(boton.text()))
        if caracter.isalpha():
            self._teclas_letras.append(boton)
        return boton

    def _construir_alfanumerico(self) -> None:
        """Arma el teclado QWERTY en español."""
        layout = QVBoxLayout(self)
        layout.setSpacing(6)

        for indice, fila in enumerate(FILAS_ALFANUMERICAS):
            fila_layout = QHBoxLayout()
            fila_layout.setSpacing(6)

            # En la última fila de letras va primero la tecla de mayúsculas.
            if indice == len(FILAS_ALFANUMERICAS) - 1:
                self._boton_mayusculas = self._crear_tecla(
                    TECLA_MAYUSCULAS, self._alternar_mayusculas, checkable=True)
                self._boton_mayusculas.setChecked(self._mayusculas)
                fila_layout.addWidget(self._boton_mayusculas)

            for caracter in fila:
                fila_layout.addWidget(self._crear_tecla_caracter(caracter))

            # En la fila de números va al final la tecla de borrar.
            if indice == 0:
                fila_layout.addWidget(self._crear_tecla(TECLA_BORRAR, self._borrar))

            layout.addLayout(fila_layout)

        # Última fila: limpiar, espacio y aceptar.
        fila_layout = QHBoxLayout()
        fila_layout.setSpacing(6)
        fila_layout.addWidget(self._crear_tecla(TECLA_LIMPIAR, self._limpiar), 2)
        fila_layout.addWidget(self._crear_tecla(TECLA_ESPACIO, lambda: self._escribir(" ")), 6)
        aceptar = self._crear_tecla(TECLA_ACEPTAR, self.aceptado.emit)
        aceptar.setProperty("tipo", "exito")      # Verde para destacarla.
        fila_layout.addWidget(aceptar, 2)
        layout.addLayout(fila_layout)

    def _construir_numerico(self) -> None:
        """Arma el teclado numérico tipo calculadora."""
        grilla = QGridLayout(self)
        grilla.setSpacing(6)

        for fila, digitos in enumerate(FILAS_NUMERICAS):
            for columna, digito in enumerate(digitos):
                grilla.addWidget(self._crear_tecla_caracter(digito), fila, columna)

        # Fila inferior: limpiar, 0 y borrar.
        grilla.addWidget(self._crear_tecla(TECLA_LIMPIAR, self._limpiar), 3, 0)
        grilla.addWidget(self._crear_tecla_caracter("0"), 3, 1)
        grilla.addWidget(self._crear_tecla(TECLA_BORRAR, self._borrar), 3, 2)

        # Aceptar ocupa todo el ancho.
        aceptar = self._crear_tecla(TECLA_ACEPTAR, self.aceptado.emit)
        aceptar.setProperty("tipo", "exito")
        grilla.addWidget(aceptar, 4, 0, 1, 3)

    # ------------------------------------------------------------------
    # Acciones de las teclas
    # ------------------------------------------------------------------

    def _escribir(self, texto: str) -> None:
        """Inserta el texto en la posición del cursor del campo destino."""
        if self._destino is not None:
            self._destino.insert(texto)

    def _borrar(self) -> None:
        """Borra el carácter a la izquierda del cursor."""
        if self._destino is not None:
            self._destino.backspace()

    def _limpiar(self) -> None:
        """Deja el campo destino vacío."""
        if self._destino is not None:
            self._destino.clear()

    def _alternar_mayusculas(self) -> None:
        """Cambia entre mayúsculas y minúsculas en todas las teclas de letras."""
        self._mayusculas = not self._mayusculas
        for boton in self._teclas_letras:
            texto = boton.text()
            boton.setText(texto.upper() if self._mayusculas else texto.lower())

    def _al_cambiar_foco(self, _anterior, actual) -> None:
        """Si el nuevo foco es un campo de texto de esta misma ventana, pasa a ser el destino."""
        if isinstance(actual, QLineEdit) and actual.window() is self.window():
            self._destino = actual
