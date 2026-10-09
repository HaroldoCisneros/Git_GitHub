"""
Funciones de apoyo usadas por varios módulos.

Aquí se agrupan pequeñas funciones que no pertenecen a ninguna pantalla ni a
ninguna tabla en particular: ubicar la carpeta del programa, resolver rutas
relativas y dar formato a los montos según la configuración del XML.
"""

from __future__ import annotations

import os
import sys
from decimal import Decimal, ROUND_HALF_UP


def carpeta_programa() -> str:
    """
    Devuelve la carpeta donde está el programa.

    - Si la aplicación está empaquetada como .exe (PyInstaller), PyInstaller
      marca ``sys.frozen`` y la carpeta es la del ejecutable.
    - Si se ejecuta como script (python main.py), la carpeta es la del
      directorio "autoservicio" (un nivel arriba de este archivo).

    El config.xml, el logo, el video y la bitácora se buscan en esta carpeta.
    """
    if getattr(sys, "frozen", False):
        return os.path.dirname(os.path.abspath(sys.executable))
    # Este archivo está en autoservicio/app/utilidades.py -> subimos dos niveles.
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def resolver_ruta(ruta: str) -> str:
    """
    Convierte una ruta del XML en una ruta absoluta.

    Igual que en el Visor de Precios: si la ruta no trae carpeta
    (por ejemplo "logo.png"), se busca junto al programa. Si ya es absoluta
    (por ejemplo "J:\\Fotos"), se deja tal cual.
    """
    if not ruta:
        return ""
    if os.path.isabs(ruta):
        return ruta
    return os.path.join(carpeta_programa(), ruta)


def formatear_monto(valor, decimales: int = 2, simbolo: str = "",
                    separador_miles: str = ".", separador_decimal: str = ",") -> str:
    """
    Da formato a un monto con los separadores definidos en <pantalla>.

    Ejemplo con la configuración por defecto:
        formatear_monto(1234567.5, 2, "$")  ->  "$ 1.234.567,50"

    Se usa Decimal para evitar errores de redondeo de los números flotantes.
    """
    numero = Decimal(str(valor or 0))
    # Redondeo comercial (0,005 -> 0,01) a la cantidad de decimales pedida.
    cuantizador = Decimal(1).scaleb(-decimales) if decimales > 0 else Decimal(1)
    numero = numero.quantize(cuantizador, rounding=ROUND_HALF_UP)

    # Python formatea con "," para miles y "." para decimales; luego se
    # cambian por los separadores del XML usando un carácter temporal.
    texto = f"{numero:,.{decimales}f}"
    texto = texto.replace(",", "\0").replace(".", separador_decimal).replace("\0", separador_miles)

    return f"{simbolo} {texto}".strip()


def solo_digitos(texto: str) -> str:
    """Deja solamente los dígitos de un texto ("V-12.345.678" -> "12345678")."""
    return "".join(c for c in (texto or "") if c.isdigit())
