#!/usr/bin/env python3
"""
Impresión de etiquetas desde la línea de comandos (CSV o un código). La ventana
que lee los artículos de SQL Server es app.py.

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
import sys

from zpl import FORMATOS, Producto, enviar_dispositivo, enviar_red, generar_lote


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
    formato.add_argument("--formato", default=next(iter(FORMATOS)), choices=list(FORMATOS),
                         help="Formato de etiqueta")
    formato.add_argument("--dpi", type=int, default=203, choices=[152, 203, 300, 600])
    formato.add_argument("--moneda", default="Bs.", help='Símbolo de moneda (default "Bs.")')

    a = ap.parse_args()

    if a.csv:
        productos = leer_csv(a.csv)
        if a.copias != 1:
            for p in productos:
                p.copias = a.copias
    elif a.codigo:
        productos = [Producto(codigo=a.codigo, nombre=a.nombre, precio=a.precio, copias=a.copias)]
    else:
        ap.error("Indique --csv o --codigo")

    if not productos:
        print("No hay productos para imprimir.", file=sys.stderr)
        return 1

    zpl = generar_lote(productos, FORMATOS[a.formato], a.dpi, a.moneda)
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
