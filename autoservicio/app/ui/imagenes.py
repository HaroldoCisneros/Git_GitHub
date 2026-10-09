"""
Imágenes de los artículos.

Todo lo relacionado con DE DÓNDE sale la foto de un artículo está aquí, para
cambiar la fuente sin tocar las pantallas.

Fuente actual (PROVISIONAL, la misma del Visor de Precios):
    La carpeta <rutas><imagenes> del config.xml, con un archivo por artículo
    nombrado con su co_art: 00123.jpg, 00124.png ...

    Si no hay foto se usa <rutas><imagen_defecto>; si tampoco existe, se
    dibuja un recuadro gris con el texto "Sin imagen".

PENDIENTE: confirmar la fuente definitiva. La tabla "art" de Profit también
tiene el campo picture (imagen guardada en la base) y los campos imagen1 /
imagen2 (rutas de archivo).

Las imágenes se guardan en memoria (caché) ya reducidas al tamaño del
renglón, para no leer el disco cada vez que se redibuja la factura.
"""

from __future__ import annotations

import os
from typing import Dict

from PySide6.QtCore import Qt
from PySide6.QtGui import QColor, QPainter, QPixmap

from ..configuracion import ConfigRutas

# Extensiones que se prueban, en este orden, para la foto de cada artículo.
EXTENSIONES = (".jpg", ".jpeg", ".png", ".bmp", ".gif")


class ProveedorImagenes:
    """Entrega la imagen de cada artículo ya escalada a un tamaño fijo."""

    def __init__(self, rutas: ConfigRutas, tamano: int):
        """
        :param rutas: sección <rutas> del config.xml.
        :param tamano: lado (px) del cuadro donde se muestra la imagen.
        """
        self._rutas = rutas
        self._tamano = tamano
        self._cache: Dict[str, QPixmap] = {}
        self._defecto: QPixmap | None = None

    def imagen(self, codigo_articulo: str) -> QPixmap:
        """Devuelve la imagen del artículo (o la imagen por defecto)."""
        codigo = (codigo_articulo or "").strip()
        if codigo not in self._cache:
            ruta = self._buscar_archivo(codigo)
            self._cache[codigo] = self._cargar(ruta) if ruta else self._imagen_defecto()
        return self._cache[codigo]

    # ------------------------------------------------------------------

    def _buscar_archivo(self, codigo: str) -> str:
        """Ruta de la foto del artículo en la carpeta de imágenes, o "" si no hay."""
        carpeta = self._rutas.imagenes
        if not codigo or not carpeta or not os.path.isdir(carpeta):
            return ""
        for extension in EXTENSIONES:
            ruta = os.path.join(carpeta, codigo + extension)
            if os.path.isfile(ruta):
                return ruta
        return ""

    def _cargar(self, ruta: str) -> QPixmap:
        """Lee y escala una imagen; si el archivo está dañado usa la de defecto."""
        pixmap = QPixmap(ruta)
        if pixmap.isNull():
            return self._imagen_defecto()
        return pixmap.scaled(self._tamano, self._tamano, Qt.KeepAspectRatio,
                             Qt.SmoothTransformation)

    def _imagen_defecto(self) -> QPixmap:
        """Imagen para artículos sin foto (se crea una sola vez)."""
        if self._defecto is None:
            ruta = self._rutas.imagen_defecto
            pixmap = QPixmap(ruta) if ruta and os.path.isfile(ruta) else QPixmap()
            if pixmap.isNull():
                self._defecto = self._dibujar_sin_imagen()
            else:
                self._defecto = pixmap.scaled(self._tamano, self._tamano, Qt.KeepAspectRatio,
                                              Qt.SmoothTransformation)
        return self._defecto

    def _dibujar_sin_imagen(self) -> QPixmap:
        """Recuadro gris con el texto "Sin imagen"."""
        pixmap = QPixmap(self._tamano, self._tamano)
        pixmap.fill(QColor("#E4E7EB"))
        pintor = QPainter(pixmap)
        pintor.setPen(QColor("#7B8794"))
        pintor.drawText(pixmap.rect(), Qt.AlignCenter | Qt.TextWordWrap, "Sin\nimagen")
        pintor.end()
        return pixmap
