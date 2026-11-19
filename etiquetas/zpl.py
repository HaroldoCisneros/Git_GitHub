"""
Generación de etiquetas ZPL y envío a impresoras Zebra.

Cada formato define su tamaño y diseño en milímetros; el ZPL se calcula
según los DPI de la impresora.
"""

import socket
from dataclasses import dataclass


@dataclass
class Producto:
    codigo: str          # código del artículo
    nombre: str          # descripción
    precio: float | str
    barras: str = ""     # contenido del código de barras (si está vacío se usa el código)
    copias: int = 1


@dataclass
class Formato:
    ancho: float          # mm
    alto: float           # mm
    margen: float         # mm
    letra_nombre: float   # alto de letra de la descripción, mm
    lineas_nombre: int
    letra_precio: float   # mm
    alto_barras: float    # mm


# Formatos disponibles. Para agregar uno nuevo basta con añadirlo aquí.
FORMATOS: dict[str, Formato] = {
    "3 x 2,2 cm vertical": Formato(
        ancho=22, alto=30, margen=1.5,
        letra_nombre=2.4, lineas_nombre=3,
        letra_precio=4.5, alto_barras=10,
    ),
    "5 x 2,5 cm horizontal": Formato(
        ancho=50, alto=25, margen=2,
        letra_nombre=2.8, lineas_nombre=2,
        letra_precio=5, alto_barras=7,
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
    return 11 * (simbolos + 2) + 2


def recortar(texto: str, ancho_puntos: int, letra_puntos: int, lineas: int) -> str:
    """Recorta el texto para que quepa en el bloque (ZPL encima las líneas que sobran)."""
    por_linea = max(1, int(ancho_puntos / (letra_puntos * 0.5)))
    maximo = por_linea * lineas
    return texto if len(texto) <= maximo else texto[: maximo - 1].rstrip() + "."


def generar_zpl(p: Producto, formato: Formato, dpi: int = 203, moneda: str = "Bs.") -> str:
    pd = dpi / 25.4                                    # puntos por mm
    ancho, alto = round(formato.ancho * pd), round(formato.alto * pd)
    m = round(formato.margen * pd)
    util = ancho - 2 * m
    letra_n = round(formato.letra_nombre * pd)
    letra_p = round(formato.letra_precio * pd)
    alto_b = round(formato.alto_barras * pd)

    y_nombre = m
    y_precio = y_nombre + formato.lineas_nombre * letra_n + round(0.8 * pd)
    y_barras = y_precio + letra_p + round(1.2 * pd)

    barras = (p.barras or p.codigo).strip()
    modulos = modulos_code128(barras)
    ancho_modulo = max(1, min(3, util // modulos))
    x_barras = max(0, (ancho - modulos * ancho_modulo) // 2)

    nombre = escapar_zpl(recortar(" ".join(p.nombre.split()), util, letra_n, formato.lineas_nombre))
    precio = escapar_zpl(formatear_precio(p.precio, moneda))

    return "\n".join([
        "^XA",
        "^CI28",                                       # UTF-8 (acentos, ñ)
        f"^PW{ancho}",
        f"^LL{alto}",
        "^LH0,0",
        f"^FO{m},{y_nombre}^A0N,{letra_n},{letra_n}"
        f"^FB{util},{formato.lineas_nombre},0,C^FH\\^FD{nombre}^FS",
        f"^FO{m},{y_precio}^A0N,{letra_p},{letra_p}^FB{util},1,0,C^FH\\^FD{precio}^FS",
        f"^BY{ancho_modulo},2,{alto_b}",
        f"^FO{x_barras},{y_barras}^BCN,{alto_b},Y,N,N,A^FH\\^FD{escapar_zpl(barras)}^FS",
        f"^PQ{max(1, int(p.copias))}",
        "^XZ",
    ]) + "\n"


def generar_lote(productos, formato: Formato, dpi: int = 203, moneda: str = "Bs.") -> str:
    return "".join(generar_zpl(p, formato, dpi, moneda) for p in productos)


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
    """Impresora instalada en Windows (USB), envío RAW. Requiere: pip install pywin32"""
    import win32print  # importación opcional

    h = win32print.OpenPrinter(impresora)
    try:
        win32print.StartDocPrinter(h, 1, ("Etiquetas", None, "RAW"))
        try:
            win32print.StartPagePrinter(h)
            win32print.WritePrinter(h, zpl.encode("utf-8"))
            win32print.EndPagePrinter(h)
        finally:
            win32print.EndDocPrinter(h)
    finally:
        win32print.ClosePrinter(h)


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
