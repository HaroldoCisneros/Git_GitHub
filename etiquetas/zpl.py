"""
Generación de etiquetas ZPL y envío a impresoras Zebra.

Cada formato define su tamaño y diseño en milímetros; el ZPL se calcula
según los DPI de la impresora.
"""

import socket
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

CARPETA = Path(__file__).resolve().parent


@dataclass
class Producto:
    codigo: str            # código del artículo
    nombre: str            # descripción
    precio: float | str = 0
    barras: str = ""       # contenido del código de barras (si está vacío se usa el código)
    referencia: str = ""   # línea en negrita bajo el código de barras (opcional)
    copias: int = 1


@dataclass
class Formato:
    """Medidas en mm. Los elementos van de arriba hacia abajo:
    logo, código de barras, número del código, referencia, precio (opcional), descripción."""
    ancho: float
    alto: float
    margen: float
    alto_logo: float
    alto_barras: float
    letra_codigo: float
    letra_ref: float
    letra_precio: float
    letra_nombre: float
    lineas_nombre: int
    espacio: float = 0.6


# Formatos disponibles. Para agregar uno nuevo basta con añadirlo aquí.
FORMATOS: dict[str, Formato] = {
    "3 x 2,2 cm vertical": Formato(
        ancho=22, alto=30, margen=1.2,
        alto_logo=4.5, alto_barras=8, letra_codigo=1.8,
        letra_ref=2.2, letra_precio=3.2, letra_nombre=2.3, lineas_nombre=4,
    ),
    "5 x 2,5 cm horizontal": Formato(
        ancho=50, alto=25, margen=1.5,
        alto_logo=3.5, alto_barras=6, letra_codigo=1.8,
        letra_ref=2.6, letra_precio=3.2, letra_nombre=2.5, lineas_nombre=2, espacio=0.5,
    ),
}


def escapar_zpl(texto: str) -> str:
    """Escapa los caracteres especiales de ZPL (se usa junto con ^FH\\)."""
    return texto.replace("\\", "\\5C").replace("^", "\\5E").replace("~", "\\7E")


def formatear_precio(precio, moneda: str) -> str:
    try:
        valor = float(str(precio).replace(",", "."))
    except ValueError:
        return f"{moneda} {precio}".strip()
    texto = f"{valor:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")  # 1.234,56
    return f"{moneda} {texto}".strip()


def modulos_code128(codigo: str) -> int:
    """Estima el ancho (en módulos) de un Code 128 en modo automático."""
    if codigo.isdigit():
        simbolos = len(codigo) // 2 + (len(codigo) % 2) * 2
    else:
        simbolos = len(codigo)
    return 11 * (simbolos + 2) + 13          # + inicio y control (11 c/u) + parada (13)


def recortar(texto: str, ancho_puntos: int, letra_puntos: int, lineas: int) -> str:
    """Recorta el texto para que quepa en el bloque (ZPL encima las líneas que sobran)."""
    por_linea = max(1, int(ancho_puntos / (letra_puntos * 0.5)))
    maximo = por_linea * lineas
    return texto if len(texto) <= maximo else texto[: maximo - 1].rstrip() + "."


# ---------------------------------------------------------------- logo

def ruta_logo(logo: str) -> Path | None:
    if not logo or not logo.strip():
        return None
    ruta = Path(logo.strip())
    return ruta if ruta.is_absolute() else CARPETA / ruta


def logo_zpl(logo: str, max_ancho: int, max_alto: int) -> tuple[str, int, int] | None:
    """Convierte la imagen del logo en un gráfico ZPL (^GFA). Devuelve (comando, ancho, alto)."""
    ruta = ruta_logo(logo)
    if ruta is None:
        return None
    if not ruta.exists():
        raise FileNotFoundError(f"No se encuentra la imagen del logo: {ruta}")
    return _logo(str(ruta), ruta.stat().st_mtime, max_ancho, max_alto)


@lru_cache(maxsize=16)
def _logo(ruta: str, _mtime: float, max_ancho: int, max_alto: int):
    try:
        from PIL import Image, ImageOps
    except ImportError as e:
        raise RuntimeError("Para imprimir el logo instale Pillow:  py -m pip install pillow") from e

    im = Image.open(ruta)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        fondo = Image.new("RGBA", im.size, "white")
        im = Image.alpha_composite(fondo, im)
    im = ImageOps.invert(im.convert("L"))           # negro -> 255
    caja = im.point(lambda v: 255 if v > 60 else 0).getbbox()
    if caja:
        im = im.crop(caja)                           # quita el borde blanco
    escala = min(max_ancho / im.width, max_alto / im.height)
    tam = (max(1, round(im.width * escala)), max(1, round(im.height * escala)))
    im = im.resize(tam, Image.LANCZOS)
    bn = im.point(lambda v: 255 if v >= 110 else 0).convert("1")   # 1 = punto negro
    datos = bn.tobytes()
    por_fila = (bn.width + 7) // 8
    return f"^GFA,{len(datos)},{len(datos)},{por_fila},{datos.hex().upper()}^FS", bn.width, bn.height


# ---------------------------------------------------------------- etiqueta

def generar_zpl(p: Producto, formato: Formato, dpi: int = 203, moneda: str = "Bs.",
                logo: str = "", con_precio: bool = False) -> str:
    f = formato
    pd = dpi / 25.4                                    # puntos por mm

    def d(mm: float) -> int:
        return round(mm * pd)

    ancho, alto = d(f.ancho), d(f.alto)
    m = d(f.margen)
    util = ancho - 2 * m
    esp = d(f.espacio)
    y = m
    cuerpo = []

    def texto(valor: str, letra: int, lineas: int = 1, ancho_letra: int | None = None):
        cuerpo.append(f"^FO{m},{y}^A0N,{letra},{ancho_letra or letra}"
                      f"^FB{util},{lineas},0,C^FH\\^FD{escapar_zpl(valor)}^FS")

    # Logo
    grafico = logo_zpl(logo, util, d(f.alto_logo))
    if grafico:
        cmd, w, h = grafico
        cuerpo.append(f"^FO{(ancho - w) // 2},{y}{cmd}")
        y += h + esp

    # Código de barras Code 128 (el número se imprime aparte, debajo)
    barras = (p.barras or p.codigo).strip()
    modulos = modulos_code128(barras)
    ancho_modulo = max(1, min(3, util // modulos))
    alto_b = d(f.alto_barras)
    cuerpo.append(f"^BY{ancho_modulo},2,{alto_b}")
    cuerpo.append(f"^FO{(ancho - modulos * ancho_modulo) // 2},{y}"
                  f"^BCN,{alto_b},N,N,N,A^FH\\^FD{escapar_zpl(barras)}^FS")
    y += alto_b + d(0.4)

    letra = d(f.letra_codigo)
    texto(barras, letra)
    y += letra + esp

    # Referencia (en negrita)
    if p.referencia.strip():
        letra = d(f.letra_ref)
        texto(p.referencia.strip(), letra)
        y += letra + esp

    # Precio (opcional)
    if con_precio:
        letra = d(f.letra_precio)
        texto(formatear_precio(p.precio, moneda), letra)
        y += letra + esp

    # Descripción: las líneas que quepan en lo que queda de etiqueta
    letra = d(f.letra_nombre)
    lineas = max(1, min(f.lineas_nombre, (alto - m - y) // letra))
    nombre = recortar(" ".join(p.nombre.split()), util, letra, lineas)
    texto(nombre, letra, lineas, round(letra * 0.85))

    return "\n".join([
        "^XA",
        "^CI28",                                       # UTF-8 (acentos, ñ)
        f"^PW{ancho}",
        f"^LL{alto}",
        "^LH0,0",
        *cuerpo,
        f"^PQ{max(1, int(p.copias))}",
        "^XZ",
    ]) + "\n"


def generar_lote(productos, formato: Formato, dpi: int = 203, moneda: str = "Bs.",
                 logo: str = "", con_precio: bool = False) -> str:
    return "".join(generar_zpl(p, formato, dpi, moneda, logo, con_precio) for p in productos)


# ---------------------------------------------------------------- envío

def enviar_red(zpl: str, host: str, puerto: int = 9100, timeout: float = 10) -> None:
    """Zebra conectada por red (puerto RAW 9100)."""
    with socket.create_connection((host, puerto), timeout=timeout) as s:
        s.sendall(zpl.encode("utf-8"))


def enviar_dispositivo(zpl: str, ruta: str) -> None:
    """Dispositivo o impresora compartida: /dev/usb/lp0, \\\\PC\\Zebra, archivo .zpl."""
    with open(ruta, "wb") as f:
        f.write(zpl.encode("utf-8"))


def enviar_windows(zpl: str, impresora: str) -> None:
    """Impresora instalada en Windows (USB, compartida...), envío RAW."""
    import winprint

    winprint.imprimir_raw(zpl.encode("utf-8"), impresora)


def enviar(zpl: str, tipo: str, destino: str, puerto: int = 9100) -> None:
    tipo = tipo.lower()
    if tipo == "red":
        enviar_red(zpl, destino, puerto)
    elif tipo == "windows":
        enviar_windows(zpl, destino)
    elif tipo in ("dispositivo", "archivo"):
        enviar_dispositivo(zpl, destino)
    else:
        raise ValueError(f"Tipo de impresora desconocido: {tipo!r} (use red, windows, dispositivo o archivo)")
