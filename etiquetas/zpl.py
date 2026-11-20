"""
Generación de etiquetas ZPL y envío a impresoras Zebra.

Cada formato define su tamaño y diseño en milímetros; el ZPL se calcula
según los DPI de la impresora.
"""

import re
import socket
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
import sys

# Carpeta del programa: junto al .exe cuando está empaquetado con PyInstaller
CARPETA = (Path(sys.executable).resolve().parent if getattr(sys, "frozen", False)
           else Path(__file__).resolve().parent)


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
    # Giro (grados, sentido antihorario) para que la etiqueta salga bien según cómo pasa el
    # rollo por la impresora. 90: el lado largo de la etiqueta va a lo ancho del cabezal.
    rotacion: int = 0


# Formatos disponibles. Para agregar uno nuevo basta con añadirlo aquí.
FORMATOS: dict[str, Formato] = {
    "3 x 2,2 cm vertical": Formato(
        ancho=22, alto=30, margen=1.2,
        alto_logo=4.5, alto_barras=8, letra_codigo=1.8,
        letra_ref=2.2, letra_precio=3.2, letra_nombre=2.3, lineas_nombre=4,
        rotacion=90,
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


# Code 128: anchos barra/espacio de los símbolos 0..105 y el de parada
C128 = ("212222 222122 222221 121223 121322 131222 122213 122312 132212 221213 221312 231212 "
        "112232 122132 122231 113222 123122 123221 223211 221132 221231 213212 223112 312131 "
        "311222 321122 321221 312212 322112 322211 212123 212321 232121 111323 131123 131321 "
        "112313 132113 132311 211313 231113 231311 112133 112331 132131 113123 113321 133121 "
        "313121 211331 231131 213113 213311 213131 311123 311321 331121 312113 312311 332111 "
        "314111 221411 431111 111224 111422 121124 121421 141122 141221 112214 112412 122114 "
        "122411 142112 142211 241211 221114 413111 241112 134111 111242 121142 121241 114212 "
        "124112 124211 411212 421112 421211 212141 214121 412121 111143 111341 131141 114113 "
        "114311 411113 411311 113141 114131 311141 411131 211412 211214 211232").split()
C128_PARADA = "2331112"


def code128(datos: str) -> list[int]:
    """Símbolos Code 128 en modo automático (C para pares de dígitos, B para el resto),
    incluido el de inicio y el de control."""
    simbolos: list[int] = []
    i, modo = 0, None
    while i < len(datos):
        digitos = len(re.match(r"\d*", datos[i:]).group())
        todo_digitos_par = i == 0 and digitos == len(datos) and digitos % 2 == 0
        if digitos >= 4 or (modo == "C" and digitos >= 2) or (todo_digitos_par and digitos):
            if modo != "C":
                simbolos.append(105 if modo is None else 99)    # inicio C / cambio a C
                modo = "C"
            simbolos.append(int(datos[i:i + 2]))
            i += 2
        else:
            if modo != "B":
                simbolos.append(104 if modo is None else 100)   # inicio B / cambio a B
                modo = "B"
            simbolos.append(ord(datos[i]) - 32)
            i += 1
    control = simbolos[0] + sum(k * s for k, s in enumerate(simbolos[1:], start=1))
    return simbolos + [control % 103]


def modulos_code128(codigo: str) -> int:
    """Ancho del Code 128 en módulos (cada símbolo mide 11 y la parada 13)."""
    return 11 * len(code128(codigo)) + 13


def cabe_barras(codigo: str, formato: Formato, dpi: int = 203) -> bool:
    """True si el código de barras cabe en el ancho útil de la etiqueta."""
    pd = dpi / 25.4
    util = round(formato.ancho * pd) - 2 * round(formato.margen * pd)
    return modulos_code128(codigo.strip()) <= util


# ---------------------------------------------------------------- logo

def ruta_logo(logo: str) -> Path | None:
    if not logo or not logo.strip():
        return None
    ruta = Path(logo.strip())
    return ruta if ruta.is_absolute() else CARPETA / ruta


def logo_imagen(logo: str, max_ancho: int, max_alto: int):
    """Imagen del logo en blanco y negro ajustada al tamaño (o None si no hay logo)."""
    ruta = ruta_logo(logo)
    if ruta is None:
        return None
    if not ruta.exists():
        raise FileNotFoundError(f"No se encuentra la imagen del logo: {ruta}")
    return _logo(str(ruta), ruta.stat().st_mtime, max_ancho, max_alto)


@lru_cache(maxsize=16)
def _logo(ruta: str, _mtime: float, max_ancho: int, max_alto: int):
    from PIL import Image, ImageOps

    im = Image.open(ruta)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        im = Image.alpha_composite(Image.new("RGBA", im.size, "white"), im)
    im = ImageOps.invert(im.convert("L"))            # negro -> 255
    caja = im.point(lambda v: 255 if v > 60 else 0).getbbox()
    if caja:
        im = im.crop(caja)                            # quita el borde blanco
    escala = min(max_ancho / im.width, max_alto / im.height)
    tam = (max(1, round(im.width * escala)), max(1, round(im.height * escala)))
    im = im.resize(tam, Image.LANCZOS)
    return im.point(lambda v: 0 if v >= 110 else 255)  # negro sobre blanco ("L")


# ---------------------------------------------------------------- letras

_LETRAS = {
    True: ["arialbd.ttf", "LiberationSans-Bold.ttf", "DejaVuSans-Bold.ttf"],
    False: ["arial.ttf", "LiberationSans-Regular.ttf", "DejaVuSans.ttf"],
}


@lru_cache(maxsize=64)
def fuente(tam: int, negrita: bool = False):
    from PIL import ImageFont

    for nombre in _LETRAS[negrita]:
        try:
            return ImageFont.truetype(nombre, tam)
        except OSError:
            continue
    return ImageFont.load_default(size=tam)


def envolver(dib, texto: str, letra, ancho: int, lineas: int) -> list[str]:
    """Reparte el texto en líneas que quepan en el ancho; corta lo que sobra."""
    filas: list[str] = []
    actual = ""
    for palabra in texto.split():
        while dib.textlength(palabra, font=letra) > ancho and len(palabra) > 1:
            # palabra más larga que la línea: se parte
            corte = len(palabra)
            while corte > 1 and dib.textlength(palabra[:corte], font=letra) > ancho:
                corte -= 1
            if actual:
                filas.append(actual)
                actual = ""
            filas.append(palabra[:corte])
            palabra = palabra[corte:]
        prueba = f"{actual} {palabra}".strip()
        if dib.textlength(prueba, font=letra) <= ancho:
            actual = prueba
        else:
            filas.append(actual)
            actual = palabra
    if actual:
        filas.append(actual)
    if len(filas) > lineas:
        filas = filas[:lineas]
        ultima = filas[-1]
        while ultima and dib.textlength(ultima + "…", font=letra) > ancho:
            ultima = ultima[:-1]
        filas[-1] = ultima.rstrip() + "…"
    return filas


# ---------------------------------------------------------------- etiqueta

def imagen_etiqueta(p: Producto, formato: Formato, dpi: int = 203, moneda: str = "Bs.",
                    logo: str = "", con_precio: bool = False):
    """Dibuja la etiqueta derecha (como se lee), a la resolución de la impresora."""
    barras = (p.barras or p.codigo).strip()
    if not cabe_barras(barras, formato, dpi):
        raise ValueError(f"El código «{barras}» es demasiado largo para la etiqueta "
                         f"de {formato.ancho:g} mm de ancho.")
    # Si no caben al menos 2 líneas de descripción, se achican el logo y las barras
    for escala in (1.0, 0.9, 0.8, 0.7, 0.6):
        img, caben = _dibujar(p, formato, dpi, moneda, logo, con_precio, escala)
        if caben >= min(2, formato.lineas_nombre):
            break
    return img


def _dibujar(p: Producto, f: Formato, dpi: int, moneda: str, logo: str, con_precio: bool,
             escala: float):
    from PIL import Image, ImageDraw

    pd = dpi / 25.4

    def d(mm: float) -> int:
        return round(mm * pd)

    ancho, alto = d(f.ancho), d(f.alto)
    m = d(f.margen)
    util = ancho - 2 * m
    esp = d(f.espacio)
    img = Image.new("L", (ancho, alto), 255)
    dib = ImageDraw.Draw(img)
    dib.fontmode = "1"                                 # sin suavizado: puntos nítidos
    y = m

    def linea(texto: str, tam_mm: float, negrita: bool) -> int:
        letra = fuente(d(tam_mm), negrita)
        while dib.textlength(texto, font=letra) > util and letra.size > 6:
            letra = fuente(letra.size - 1, negrita)    # se achica si no cabe
        dib.text((ancho / 2, y), texto, font=letra, fill=0, anchor="mt")
        return round(letra.size * 1.05)

    # Logo
    grafico = logo_imagen(logo, util, d(f.alto_logo * escala))
    if grafico is not None:
        img.paste(grafico, ((ancho - grafico.width) // 2, y))
        y += grafico.height + esp

    # Código de barras Code 128, punto por punto
    barras = (p.barras or p.codigo).strip()
    modulos = modulos_code128(barras)
    modulo = max(1, min(3, util // modulos))
    alto_b = d(f.alto_barras * max(escala, 0.75))
    x = (ancho - modulos * modulo) // 2
    for patron in [C128[s] for s in code128(barras)] + [C128_PARADA]:
        for k, n in enumerate(patron):
            w = int(n) * modulo
            if k % 2 == 0:
                dib.rectangle([x, y, x + w - 1, y + alto_b - 1], fill=0)
            x += w
    y += alto_b + d(0.3)

    y += linea(barras, f.letra_codigo, False) + esp // 2
    if p.referencia.strip():
        y += linea(p.referencia.strip(), f.letra_ref, True) + esp // 2
    if con_precio:
        y += linea(formatear_precio(p.precio, moneda), f.letra_precio, True) + esp // 2

    # Descripción: las líneas que quepan en lo que queda. Si no cabe completa,
    # se prueba con letra un poco más pequeña (hasta un 80 %).
    texto = " ".join(p.nombre.split())
    base = d(f.letra_nombre)
    for tam in range(base, max(6, round(base * 0.8)) - 1, -1):
        letra = fuente(tam, False)
        sube, baja = letra.getmetrics()
        alto_linea = round((sube + baja) * 0.95)
        caben = (alto - m - y) // alto_linea
        lineas = max(1, min(f.lineas_nombre + (base - tam > 0), caben))
        filas = envolver(dib, texto, letra, util, 99)
        if len(filas) <= lineas:
            break
    filas = envolver(dib, texto, letra, util, lineas)
    for fila in filas:
        dib.text((ancho / 2, y + sube), fila, font=letra, fill=0, anchor="ms")  # sobre la línea base
        y += alto_linea
    return img, caben


def imagen_a_zpl(img) -> str:
    """Convierte una imagen en un gráfico ZPL ^GFA (1 = punto negro)."""
    from PIL import ImageOps

    bn = ImageOps.invert(img.convert("L")).point(lambda v: 255 if v >= 128 else 0).convert("1")
    datos = bn.tobytes()
    por_fila = (bn.width + 7) // 8
    return f"^GFA,{len(datos)},{len(datos)},{por_fila},{datos.hex().upper()}^FS"


def generar_zpl(p: Producto, formato: Formato, dpi: int = 203, moneda: str = "Bs.",
                logo: str = "", con_precio: bool = False, girar: int = 0) -> str:
    """ZPL de una etiqueta: la imagen girada según el formato (+ giro extra de la configuración)."""
    img = imagen_etiqueta(p, formato, dpi, moneda, logo, con_precio)
    rotacion = (formato.rotacion + girar) % 360
    if rotacion:
        img = img.rotate(rotacion, expand=True, fillcolor=255)
    return "\n".join([
        "^XA",
        f"^PW{img.width}",
        f"^LL{img.height}",
        "^LH0,0",
        f"^FO0,0{imagen_a_zpl(img)}",
        f"^PQ{max(1, int(p.copias))}",
        "^XZ",
    ]) + "\n"


def generar_lote(productos, formato: Formato, dpi: int = 203, moneda: str = "Bs.",
                 logo: str = "", con_precio: bool = False, girar: int = 0) -> str:
    return "".join(generar_zpl(p, formato, dpi, moneda, logo, con_precio, girar) for p in productos)


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
