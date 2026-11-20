"""
Vista previa de las etiquetas.

Las etiquetas se imprimen como imagen, así que la vista previa es exacta.
renderizar() además dibuja un archivo ZPL (solo los comandos que usa este programa).
"""

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from zpl import C128 as _C128, C128_PARADA as _STOP, code128  # noqa: E402,F401

_FUENTES = [
    "arialbd.ttf", "C:/Windows/Fonts/arialbd.ttf",
    "LiberationSans-Bold.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
_MUESTRA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
_ANCHO_ZEBRA = 0.55   # ancho medio de un carácter de la fuente 0 de Zebra, relativo al ancho ^A0


def _fuente(tam: int):
    for nombre in _FUENTES:
        try:
            return ImageFont.truetype(nombre, tam)
        except OSError:
            continue
    try:
        return ImageFont.load_default(size=tam)
    except TypeError:
        return ImageFont.load_default()


def _dibujar_barras(dib, x, y, datos, modulo, alto):
    patrones = [_C128[s] for s in code128(datos)] + [_STOP]
    for patron in patrones:
        for k, ancho in enumerate(patron):
            w = int(ancho) * modulo
            if k % 2 == 0:
                dib.rectangle([x, y, x + w - 1, y + alto - 1], fill=0)
            x += w


def _decodificar_fh(texto: str) -> str:
    return re.sub(r"\\([0-9A-Fa-f]{2})", lambda m: chr(int(m.group(1), 16)), texto)


def _medir(dib, texto, fuente, k):
    return dib.textlength(texto, font=fuente) * k


def _dibujar_texto(img, x, y, texto, fuente, k):
    """Dibuja el texto comprimido horizontalmente por el factor k."""
    ancho = max(1, int(ImageDraw.Draw(img).textlength(texto, font=fuente)) + 2)
    alto = int(fuente.size * 1.3)
    capa = Image.new("L", (ancho, alto), 0)
    ImageDraw.Draw(capa).text((0, 0), texto, font=fuente, fill=255)
    capa = capa.resize((max(1, round(ancho * k)), alto), Image.LANCZOS)
    img.paste(0, (round(x), round(y)), capa)


def _envolver(dib, texto, fuente, ancho, lineas, k=1.0):
    palabras, filas, actual = texto.split(), [], ""
    for p in palabras:
        prueba = f"{actual} {p}".strip()
        if _medir(dib, prueba, fuente, k) <= ancho or not actual:
            actual = prueba
        else:
            filas.append(actual)
            actual = p
    if actual:
        filas.append(actual)
    if len(filas) > lineas:  # la Zebra encima lo que sobra en la última línea
        filas = filas[:lineas - 1] + [" ".join(filas[lineas - 1:])]
    return filas


def renderizar(zpl: str) -> list[Image.Image]:
    """Devuelve una imagen (1 punto = 1 píxel) por cada etiqueta ^XA...^XZ."""
    imagenes = []
    for bloque in re.findall(r"\^XA(.*?)\^XZ", zpl, re.S):
        ancho, alto = 400, 200
        m = re.search(r"\^PW(\d+)", bloque)
        ancho = int(m.group(1)) if m else ancho
        m = re.search(r"\^LL(\d+)", bloque)
        alto = int(m.group(1)) if m else alto
        img = Image.new("L", (ancho, alto), 255)
        dib = ImageDraw.Draw(img)
        x = y = 0
        letra = (20, 20)
        bloque_txt = None
        modulo = 2
        barras = None
        for cmd in bloque.replace("\n", "").split("^")[1:]:
            c, args = cmd[:2], cmd[2:]
            if c == "FO":
                x, y = (int(v) for v in args.split(",")[:2])
                bloque_txt, barras = None, None
            elif c == "A0":
                h, w = args.split(",")[1:3]
                letra = (int(h), int(w))
            elif c == "FB":
                p = args.split(",")
                bloque_txt = (int(p[0]), int(p[1]), p[3] if len(p) > 3 else "L")
            elif c == "BY":
                modulo = int(args.split(",")[0])
            elif c == "BC":
                barras = int(args[1:].split(",")[1])
            elif c == "GF":
                p = args.split(",", 4)
                por_fila, datos = int(p[3]), bytes.fromhex(p[4])
                filas = len(datos) // por_fila
                g = Image.frombytes("1", (por_fila * 8, filas), datos)  # bit 1 = punto negro
                img.paste(0, (x, y), g.convert("L"))
            elif c == "FD":
                texto = _decodificar_fh(args)
                if barras is not None:
                    _dibujar_barras(dib, x, y, texto, modulo, barras)
                    continue
                fuente = _fuente(max(6, letra[0]))
                # se comprime la letra hasta el ancho aproximado de la fuente 0 de Zebra
                k = len(_MUESTRA) * _ANCHO_ZEBRA * letra[1] / dib.textlength(_MUESTRA, font=fuente)
                if bloque_txt:
                    w, lineas, just = bloque_txt
                    filas = _envolver(dib, texto, fuente, w, lineas, k)
                else:
                    w, just, filas = ancho - x, "L", [texto]
                for i, fila in enumerate(filas):
                    tw = _medir(dib, fila, fuente, k)
                    dx = (w - tw) / 2 if just == "C" else (w - tw if just == "R" else 0)
                    _dibujar_texto(img, x + dx, y + i * letra[0], fila, fuente, k)
        imagenes.append(img)
    return imagenes


def lamina(etiquetas, escala: int = 3, por_fila: int = 5, maximo: int = 10) -> Image.Image:
    """Etiquetas una al lado de la otra, ampliadas, con borde redondeado como en el rollo.
    Recibe una lista de imágenes o un texto ZPL."""
    if isinstance(etiquetas, str):
        etiquetas = renderizar(etiquetas)
    etiquetas = list(etiquetas)[:maximo]
    if not etiquetas:
        return Image.new("RGB", (10, 10), "white")
    w, h = etiquetas[0].width * escala, etiquetas[0].height * escala
    sep = 8 * escala
    cols = min(por_fila, len(etiquetas))
    filas = (len(etiquetas) + cols - 1) // cols
    hoja = Image.new("RGB", (cols * (w + sep) + sep, filas * (h + sep) + sep), (210, 210, 210))
    for i, et in enumerate(etiquetas):
        cx, cy = sep + (i % cols) * (w + sep), sep + (i // cols) * (h + sep)
        grande = et.resize((w, h), Image.NEAREST).convert("RGB")
        mascara = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mascara).rounded_rectangle([0, 0, w - 1, h - 1], radius=6 * escala, fill=255)
        hoja.paste(grande, (cx, cy), mascara)
    return hoja


if __name__ == "__main__":
    import sys

    entrada = Path(sys.argv[1])
    salida = Path(sys.argv[2]) if len(sys.argv) > 2 else entrada.with_suffix(".png")
    lamina(entrada.read_text(encoding="utf-8")).save(salida)
    print(f"Vista previa guardada en {salida}")
