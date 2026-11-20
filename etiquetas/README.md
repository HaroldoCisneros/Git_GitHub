# Etiquetas Zebra

Programa para imprimir etiquetas de artículos en impresoras Zebra (ZPL), leyendo
los artículos de la tabla `art` de SQL Server (Profit), aunque el servidor esté en un contenedor Docker.

Cada etiqueta lleva, de arriba hacia abajo: **logo**, **código de barras Code 128** (con `co_art`), el número del
código, la **referencia** en negrita (opcional), el **precio** (opcional) y la **descripción**.

## Formatos

| Formato                 | Tamaño (ancho × alto)  |
|-------------------------|------------------------|
| `3 x 2,2 cm vertical`   | 22 × 30 mm             |
| `5 x 2,5 cm horizontal` | 50 × 25 mm             |

Los formatos se definen en `zpl.py` (`FORMATOS`). Para agregar uno basta con copiar una entrada y cambiar las medidas.

## Instalación (Windows)

1. Instalar Python 3.10 o superior desde https://www.python.org/downloads/ marcando **"Add python.exe to PATH"**.
2. Doble clic en **`instalar.bat`**: instala las dependencias y abre la configuración.
3. Doble clic en **`iniciar.bat`** para abrir el programa.

A mano, desde la carpeta del proyecto (si `pip` "no se reconoce", use `py -m pip`):

```bash
py -m pip install -r requirements.txt
```

## Configuración (`config.xml`)

Toda la configuración se guarda en `config.xml` y se edita con su propia ventana:

```bash
py configurar.py
```

También se abre con el botón **Configuración…** del programa, y sola la primera vez si no existe `config.xml`.
Si había un `config.ini` de la versión anterior, se convierte a `config.xml` automáticamente.

- **Servidor SQL:** servidor, puerto, base de datos, usuario y clave. Si SQL Server está en Docker en el
  mismo equipo, el servidor es `localhost` y el puerto el publicado por el contenedor (normalmente `1433`).
- **Tabla de artículos:** tabla y columnas (por defecto `art`, `co_art`, `art_des`, `prec_vta1`; el código
  de barras es `co_art`). El botón **Probar conexión** comprueba la conexión y las columnas.
- **Impresora**, según la conexión:
  - Zebra en red: la IP (puerto 9100).
  - Instalada en Windows (USB): se elige de la lista de impresoras instaladas (↻ la actualiza). Si hay una Zebra
    (o ZDesigner) se selecciona sola. Para ver la lista desde la consola: `py winprint.py`.
  - Dispositivo / compartida: `\\PC-CAJA\Zebra` o `/dev/usb/lp0`.
  - Archivo `.zpl`: para pruebas.

  **Imprimir etiqueta de prueba** manda una etiqueta con los datos que hay en pantalla, sin guardarlos.
- **Etiqueta:** formato por defecto, moneda, si se imprime el precio y la imagen del **logo**.
  El logo se convierte solo a blanco y negro y se ajusta al ancho de la etiqueta; conviene una imagen
  en blanco y negro (fondo blanco), sin bordes. Si se pone dentro de la carpeta del programa
  (por ejemplo `logo.png`), se guarda la ruta relativa.

`config.xml` contiene la clave de SQL, por eso no se sube a git.

## Uso

```bash
py app.py
```

El interruptor de arriba cambia entre los dos modos:

- **Un artículo:** se escribe o escanea el código, se indica la **cantidad** de etiquetas y se pulsa **Imprimir**.
- **Lista de 10 artículos:** hasta 10 códigos distintos y se imprime una etiqueta de cada uno.
  Si algún código no existe, el programa avisa y pregunta si se imprimen los demás.

Arriba se puede **cambiar la impresora** antes de imprimir: la primera de la lista es la de la configuración
(predeterminada) y después aparecen todas las impresoras instaladas en Windows (por ejemplo, una 3nStar).

**Vista previa** muestra en pantalla cómo quedarán las etiquetas, tal como se imprimen.

Al pulsar Enter en un código se muestran su precio y su descripción. **Buscar…** (o **F2** en la lista) abre
una búsqueda por descripción: Enter busca, y un segundo Enter elige el primer resultado.
**Guardar ZPL…** guarda las etiquetas en un archivo en vez de imprimirlas. **Ctrl+P** imprime.

Las etiquetas se envían en lenguaje **ZPL** (como gráfico `^GFA`). Las impresoras de otras marcas
(como 3nStar) deben tener activada la emulación ZPL.

## Orientación

La etiqueta se dibuja completa como imagen a la resolución de la impresora (el código de barras punto por
punto) y se envía como un gráfico ZPL. En el formato `3 x 2,2 cm vertical` la imagen se gira 90°, porque
ese rollo pasa por la impresora con los 3 cm a lo ancho del cabezal. Si sale al revés, marque
**Girar 180°** en la configuración. La vista previa es exactamente lo que se imprime.

**Grosor de barra:** por defecto (automático) se usa la barra más gruesa que quepa a lo ancho de la
etiqueta: 2 puntos (0,25 mm) para códigos de 8 dígitos en la etiqueta de 2,2 cm. Si el lector no lee
el código, pruebe otro grosor en la configuración.

## Ejecutable (.exe)

- **Automático:** cada cambio en `etiquetas/` genera `EtiquetasZebra.exe` en GitHub Actions
  (pestaña *Actions* → *Ejecutable etiquetas Zebra* → *Artifacts*), después de pasar las pruebas en Windows.
- **En su PC:** doble clic en `generar_exe.bat`; el ejecutable queda en `dist\EtiquetasZebra.exe`.

El `.exe` no necesita Python. `config.xml` y el logo se guardan **junto al .exe**.

## Pruebas

```bash
py -m pytest tests
```

## Línea de comandos (opcional)

`etiquetas_zebra.py` imprime desde un CSV o un solo código, sin base de datos:

```bash
python etiquetas_zebra.py --csv productos.csv --formato "3 x 2,2 cm vertical" --impresora 192.168.1.50
```
