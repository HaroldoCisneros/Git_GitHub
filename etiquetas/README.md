# Etiquetas Zebra

Programa en Python (sin dependencias externas) para imprimir etiquetas de productos
en impresoras Zebra usando ZPL.

Cada etiqueta lleva: **descripción** (hasta 2 líneas), **precio** y **código de barras Code 128**.
Tamaño por defecto: 50 × 25 mm a 203 dpi (configurable).

## Uso

Una etiqueta a una Zebra en red (puerto RAW 9100):

```bash
python etiquetas_zebra.py --codigo 7591234567890 --nombre "Arroz 1Kg" --precio 45,50 --impresora 192.168.1.50
```

Varias desde un CSV (`codigo;nombre;precio;copias`, separador `,` o `;`):

```bash
python etiquetas_zebra.py --csv productos.csv --impresora 192.168.1.50
```

Impresora USB en Linux o compartida en Windows:

```bash
python etiquetas_zebra.py --csv productos.csv --dispositivo /dev/usb/lp0
python etiquetas_zebra.py --csv productos.csv --dispositivo \\PC-CAJA\Zebra
```

Solo generar el archivo ZPL (sin imprimir); sin ningún destino, el ZPL se muestra en pantalla:

```bash
python etiquetas_zebra.py --csv productos.csv --salida etiquetas.zpl
```

## Opciones de formato

| Opción     | Default | Descripción                     |
|------------|---------|---------------------------------|
| `--ancho`  | 50      | Ancho de la etiqueta en mm      |
| `--alto`   | 25      | Alto de la etiqueta en mm       |
| `--dpi`    | 203     | Resolución: 152, 203, 300 o 600 |
| `--moneda` | `Bs.`   | Símbolo antes del precio        |
| `--copias` | 1       | Copias por producto             |

Para ver cómo queda un `.zpl` sin gastar etiquetas, se puede pegar en https://labelary.com/viewer.html
