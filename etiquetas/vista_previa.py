"""
Vista previa aproximada de las etiquetas: dibuja con Pillow el mismo ZPL que se
envía a la impresora (solo los comandos que usa este programa).

La letra es parecida a la de la Zebra pero no idéntica; las posiciones, el logo y
el código de barras sí son los reales.
"""

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# Code 128: anchos barra/espacio de los símbolos 0..105 y el de parada
_C128 = ("212222 222122 222221 121223 121322 131222 122213 122312 132212 221213 221312 231212 "
         "112232 122132 122231 113222 123122 123221 223211 221132 221231 213212 223112 312131 "
         "311222 321122 321221 312212 322112 322211 212123 212321 232121 111323 131123 131321 "
         "112313 132113 132311 211313 231113 231311 112133 112331 132131 113123 113321 133121 "
         "313121 211331 231131 213113 213311 213131 311123 311321 331121 312113 312311 332111 "
         "314111 221411 431111 111224 111422 121124 121421 141122 141221 112214 112412 122114 "
         "122411 142112 142211 241211 221114 413111 241112 134111 111242 121142 121241 114212 "
         "124112 124211 411212 421112 421211 212141 214121 412121 111143 111341 131141 114113 "
         "114311 411113 411311 113141 114131 311141 411131 211412 211214 211232").split()
_STOP = "2331112"
_START_B, _START_C, _CODE_B, _CODE_C = 104, 105, 100, 99

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


def code128(datos: str) -> list[int]:
    """Símbolos Code 128 en modo automático (C para pares de dígitos, B para el resto)."""
    simbolos: list[int] = []
    i, modo = 0, None
    while i < len(datos):
        digitos = len(re.match(r"\d*", datos[i:]).group())
        if digitos >= 4 or (modo == "C" and digitos >= 2) or (i == 0 and digitos == len(datos) and digitos % 2 == 0 and digitos):
            if modo != "C":
                simbolos.append(_START_C if modo is None else _CODE_C)
                modo = "C"
            simbolos.append(int(datos[i:i + 2]))
            i += 2
        else:
            if modo != "B":
                simbolos.append(_START_B if modo is None else _CODE_B)
                modo = "B"
            simbolos.append(ord(datos[i]) - 32)
            i += 1
    control = simbolos[0] + sum(k * s for k, s in enumerate(simbolos[1:], start=1))
    simbolos.append(control % 103)
    return simbolos


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


def lamina(zpl: str, escala: int = 3, por_fila: int = 5, maximo: int = 10) -> Image.Image:
    """Etiquetas una al lado de la otra, ampliadas, con borde redondeado como en el rollo."""
    etiquetas = renderizar(zpl)[:maximo]
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
