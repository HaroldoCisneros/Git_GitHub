# Etiquetas Zebra

Programa para imprimir etiquetas de artículos en impresoras Zebra (ZPL), leyendo
los artículos de la tabla `art` de SQL Server (Profit), aunque el servidor esté en un contenedor Docker.

Cada etiqueta lleva **descripción**, **precio** y **código de barras Code 128**.

## Formatos

| Formato                 | Tamaño (ancho × alto)  |
|-------------------------|------------------------|
| `3 x 2,2 cm vertical`   | 22 × 30 mm             |
| `5 x 2,5 cm horizontal` | 50 × 25 mm             |

Los formatos se definen en `zpl.py` (`FORMATOS`). Para agregar uno basta con copiar una entrada y cambiar las medidas.

## Instalación (Windows)

1. Instalar Python 3.10 o superior desde https://www.python.org/downloads/ marcando **"Add python.exe to PATH"**.
2. Doble clic en **`instalar.bat`**: instala las dependencias y crea `config.ini`.
3. Doble clic en **`iniciar.bat`** para abrir el programa.

A mano, desde la carpeta del proyecto (si `pip` "no se reconoce", use `py -m pip`):

```bash
py -m pip install -r requirements.txt
```

En `config.ini` se indican el servidor SQL, la tabla/columnas y la impresora:

- **SQL en Docker en el mismo equipo:** `servidor = localhost` y el puerto publicado del contenedor (normalmente `1433`).
- **Impresora**, según `tipo`:
  - `red`: `destino` es la IP de la Zebra (puerto 9100).
  - `windows`: `destino` es el nombre de la impresora instalada en Windows (requiere `pip install pywin32`).
  - `dispositivo`: `/dev/usb/lp0` en Linux o `\\PC-CAJA\Zebra` si está compartida.
  - `archivo`: guarda el ZPL en un archivo (para pruebas).

## Uso

```bash
py app.py
```

El interruptor de arriba cambia entre los dos modos:

- **Un artículo:** se escribe o escanea el código, se indica la **cantidad** de etiquetas y se pulsa **Imprimir**.
- **Lista de 10 artículos:** hasta 10 códigos distintos y se imprime una etiqueta de cada uno.
  Si algún código no existe, el programa avisa y pregunta si se imprimen los demás.

Al pulsar Enter en un código se muestran su precio y su descripción. **Buscar…** (o **F2** en la lista) abre
una búsqueda por descripción: Enter busca, y un segundo Enter elige el primer resultado.
**Guardar ZPL…** guarda las etiquetas en un archivo en vez de imprimirlas. **Ctrl+P** imprime.

Para ver cómo queda un `.zpl` sin gastar etiquetas, se puede pegar en https://labelary.com/viewer.html

## Línea de comandos (opcional)

`etiquetas_zebra.py` imprime desde un CSV o un solo código, sin base de datos:

```bash
python etiquetas_zebra.py --csv productos.csv --formato "3 x 2,2 cm vertical" --impresora 192.168.1.50
```
