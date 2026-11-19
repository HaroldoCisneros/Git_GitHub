#!/usr/bin/env python3
"""
Impresión de etiquetas de productos en impresoras Zebra (lenguaje ZPL).

Solo usa la biblioteca estándar de Python 3.

Ejemplos:
  # Una etiqueta, enviada a una Zebra en red (puerto 9100)
  python etiquetas_zebra.py --codigo 7591234567890 --nombre "Arroz 1Kg" --precio 45.50 --impresora 192.168.1.50

  # Varias etiquetas desde un CSV
  python etiquetas_zebra.py --csv productos.csv --impresora 192.168.1.50

  # Solo generar el archivo ZPL (sin imprimir)
  python etiquetas_zebra.py --csv productos.csv --salida etiquetas.zpl

  # Impresora USB en Linux / compartida en Windows
  python etiquetas_zebra.py --csv productos.csv --dispositivo /dev/usb/lp0
  python etiquetas_zebra.py --csv productos.csv --dispositivo \\\\PC-CAJA\\Zebra
"""

import argparse
import csv
import socket
import sys
from dataclasses import dataclass


@dataclass
class Producto:
    codigo: str
    nombre: str
    precio: str
    copias: int = 1


def escapar_zpl(texto: str) -> str:
    """Escapa los caracteres especiales de ZPL (se usa junto con ^FH\\)."""
    return (
        texto.replace("\\", "\\5C")
        .replace("^", "\\5E")
        .replace("~", "\\7E")
    )


def formatear_precio(precio: str, moneda: str) -> str:
    try:
        valor = float(str(precio).replace(",", "."))
    except ValueError:
        return f"{moneda} {precio}".strip()
    # Formato 1.234,56
    texto = f"{valor:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")
    return f"{moneda} {texto}".strip()


def modulos_code128(codigo: str) -> int:
    """Estima el ancho (en módulos) de un Code 128 en modo automático."""
    if codigo.isdigit():
        simbolos = len(codigo) // 2 + (len(codigo) % 2) * 2  # subconjunto C (+cambio si es impar)
    else:
        simbolos = len(codigo)
    return 11 * (simbolos + 2) + 2  # + inicio y verificación, + parada (13)


def generar_zpl(p: Producto, ancho_mm: float, alto_mm: float, dpi: int, moneda: str) -> str:
    """Genera el ZPL de una etiqueta. El diseño se escala según tamaño y DPI."""
    pd = dpi / 25.4                      # puntos por milímetro
    ancho = round(ancho_mm * pd)
    alto = round(alto_mm * pd)
    m = round(2 * pd)                    # margen de 2 mm
    util = ancho - 2 * m

    letra_nombre = round(alto * 0.11)
    letra_precio = round(alto * 0.20)
    alto_barras = round(alto * 0.28)

    y_nombre = m
    y_precio = y_nombre + 2 * letra_nombre + round(1 * pd)
    y_barras = y_precio + letra_precio + round(1.5 * pd)

    # Código de barras: módulo lo más ancho posible sin salirse, y centrado
    modulos = modulos_code128(p.codigo.strip())
    ancho_modulo = max(1, min(3 if dpi <= 203 else 4, util // modulos))
    x_barras = max(0, (ancho - modulos * ancho_modulo) // 2)

    nombre = escapar_zpl(p.nombre.strip())
    codigo = escapar_zpl(p.codigo.strip())
    precio = escapar_zpl(formatear_precio(p.precio, moneda))

    return "\n".join([
        "^XA",
        "^CI28",                                   # UTF-8 (acentos, ñ)
        f"^PW{ancho}",
        f"^LL{alto}",
        "^LH0,0",
        # Nombre del producto (hasta 2 líneas, centrado)
        f"^FO{m},{y_nombre}^A0N,{letra_nombre},{letra_nombre}"
        f"^FB{util},2,0,C^FH\\^FD{nombre}^FS",
        # Precio
        f"^FO{m},{y_precio}^A0N,{letra_precio},{letra_precio}"
        f"^FB{util},1,0,C^FH\\^FD{precio}^FS",
        # Código de barras Code 128 con texto legible debajo
        f"^BY{ancho_modulo},2,{alto_barras}",
        f"^FO{x_barras},{y_barras}^BCN,{alto_barras},Y,N,N,A^FH\\^FD{codigo}^FS",
        f"^PQ{max(1, p.copias)}",
        "^XZ",
    ])


def leer_csv(ruta: str) -> list[Producto]:
    """Lee un CSV con columnas: codigo, nombre, precio[, copias]. Acepta ',' o ';'."""
    with open(ruta, newline="", encoding="utf-8-sig") as f:
        muestra = f.read(2048)
        f.seek(0)
        try:
            dialecto = csv.Sniffer().sniff(muestra, delimiters=",;\t")
        except csv.Error:
            dialecto = csv.excel
        lector = csv.DictReader(f, dialect=dialecto)
        productos = []
        for n, fila in enumerate(lector, start=2):
            fila = {(k or "").strip().lower(): (v or "").strip() for k, v in fila.items()}
            if not fila.get("codigo"):
                print(f"Aviso: fila {n} sin código, se omite.", file=sys.stderr)
                continue
            copias = fila.get("copias") or "1"
            productos.append(Producto(
                codigo=fila["codigo"],
                nombre=fila.get("nombre", ""),
                precio=fila.get("precio", ""),
                copias=int(copias) if copias.isdigit() else 1,
            ))
    return productos


def enviar_red(zpl: str, host: str, puerto: int = 9100, timeout: float = 10) -> None:
    with socket.create_connection((host, puerto), timeout=timeout) as s:
        s.sendall(zpl.encode("utf-8"))


def enviar_dispositivo(zpl: str, ruta: str) -> None:
    with open(ruta, "wb") as f:
        f.write(zpl.encode("utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser(description="Imprime etiquetas de productos en impresoras Zebra (ZPL).")
    origen = ap.add_argument_group("Productos")
    origen.add_argument("--csv", help="Archivo CSV con columnas codigo,nombre,precio[,copias]")
    origen.add_argument("--codigo", help="Código del producto (va en el código de barras)")
    origen.add_argument("--nombre", default="", help="Descripción del producto")
    origen.add_argument("--precio", default="", help="Precio de venta")
    origen.add_argument("--copias", type=int, default=1, help="Copias por producto (default 1)")

    destino = ap.add_argument_group("Destino (puede usar varios)")
    destino.add_argument("--impresora", help="IP o nombre de la Zebra en red")
    destino.add_argument("--puerto", type=int, default=9100, help="Puerto RAW (default 9100)")
    destino.add_argument("--dispositivo", help="Ruta del dispositivo o impresora compartida")
    destino.add_argument("--salida", help="Guardar el ZPL en este archivo")

    formato = ap.add_argument_group("Formato de etiqueta")
    formato.add_argument("--ancho", type=float, default=50, help="Ancho en mm (default 50)")
    formato.add_argument("--alto", type=float, default=25, help="Alto en mm (default 25)")
    formato.add_argument("--dpi", type=int, default=203, choices=[152, 203, 300, 600])
    formato.add_argument("--moneda", default="Bs.", help='Símbolo de moneda (default "Bs.")')

    a = ap.parse_args()

    if a.csv:
        productos = leer_csv(a.csv)
        if a.copias != 1:
            for p in productos:
                p.copias = a.copias
    elif a.codigo:
        productos = [Producto(a.codigo, a.nombre, a.precio, a.copias)]
    else:
        ap.error("Indique --csv o --codigo")

    if not productos:
        print("No hay productos para imprimir.", file=sys.stderr)
        return 1

    zpl = "\n".join(generar_zpl(p, a.ancho, a.alto, a.dpi, a.moneda) for p in productos) + "\n"
    total = sum(p.copias for p in productos)

    if not (a.impresora or a.dispositivo or a.salida):
        print(zpl)  # sin destino: mostrar el ZPL en pantalla
        return 0

    try:
        if a.salida:
            with open(a.salida, "w", encoding="utf-8") as f:
                f.write(zpl)
            print(f"ZPL guardado en {a.salida}")
        if a.dispositivo:
            enviar_dispositivo(zpl, a.dispositivo)
            print(f"Enviadas {total} etiqueta(s) a {a.dispositivo}")
        if a.impresora:
            enviar_red(zpl, a.impresora, a.puerto)
            print(f"Enviadas {total} etiqueta(s) a {a.impresora}:{a.puerto}")
    except OSError as e:
        print(f"Error al imprimir: {e}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
