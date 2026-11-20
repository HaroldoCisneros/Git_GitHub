"""Icono de las ventanas (barra de título) a partir de logoapp.bmp / logoconfig.bmp."""

import base64
import io
import tkinter as tk

from config import CARPETA


def poner_icono(ventana, archivo: str) -> None:
    """Pone la imagen (junto al programa o al .exe) como icono de la ventana, si existe."""
    ruta = CARPETA / archivo
    if not ruta.exists():
        return
    try:
        from PIL import Image

        img = Image.open(ruta).convert("RGBA")
        img.thumbnail((64, 64))
        buffer = io.BytesIO()
        img.save(buffer, format="PNG")
        foto = tk.PhotoImage(master=ventana, data=base64.b64encode(buffer.getvalue()))
        ventana.iconphoto(False, foto)
        ventana._icono = foto          # que no lo borre el recolector de basura
    except Exception:  # noqa: BLE001 - sin icono no pasa nada
        pass
