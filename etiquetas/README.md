# Etiquetas Zebra

Programa para imprimir etiquetas de artículos en impresoras Zebra (ZPL), leyendo
los artículos de la tabla `art` de SQL Server (Profit), aunque el servidor esté en un contenedor Docker.

Cada etiqueta lleva, de arriba hacia abajo: **logo**, **código de barras Code 128** (con `co_art`), el número del
código, la **referencia** en negrita (opcional), el **precio** (opcional) y la **descripción**.

## Formatos

El primer número es lo que mide la etiqueta **a lo ancho del cabezal**; el segundo, el largo en el
sentido en que avanza el papel. Todos los verticales tienen el mismo diseño.

| Formato                 | Diseño (ancho × alto) | En la impresora              |
|-------------------------|-----------------------|------------------------------|
| `3 x 2,2 cm vertical`   | 22 × 30 mm            | girada 90° (30 mm de ancho)  |
| `4 x 3 cm vertical`     | 30 × 40 mm            | girada 90° (40 mm de ancho)  |
| `5 x 8 cm vertical`     | 50 × 80 mm            | derecha (50 mm de ancho)     |
| `5 x 2,5 cm horizontal` | 50 × 25 mm            | derecha (50 mm de ancho)     |

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
- **Programa de etiquetas:** si se muestra o no el botón **Configuración…** en el programa. Si se
  quita, la configuración solo se cambia con `ConfigEtiquetas.exe`.
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
- **Lista de 20 artículos:** hasta 20 códigos distintos (en dos columnas de 10) y se imprime una
  etiqueta de cada uno.
  Si algún código no existe, el programa avisa y pregunta si se imprimen los demás.

Arriba se puede **cambiar la impresora** antes de imprimir: la primera de la lista es la de la configuración
(predeterminada) y después aparecen todas las impresoras instaladas en Windows (por ejemplo, una 3nStar).

**Vista previa** muestra en pantalla cómo quedarán las etiquetas, tal como se imprimen.

Al pulsar Enter en un código se muestran su descripción y su referencia.

**F2** (en cualquiera de los dos modos) abre la búsqueda de artículos. Arriba se elige **buscar por**
Descripción, Código, Referencia o Modelo. En la descripción cada palabra se busca por separado
(«pintura gris» encuentra «PINTURA AY-2000 INTERIOR GRIS»). El **`*`** vale por cualquier texto:
`PINT*GALON`, `0012*8`, `1811*050`; un `*` solo muestra todos (los primeros 200). Enter busca, un segundo Enter (o doble
clic) elige el artículo. Referencia y Modelo usan las columnas indicadas en la configuración.
**Guardar ZPL…** guarda las etiquetas en un archivo en vez de imprimirlas. **Ctrl+P** imprime.

Las etiquetas se envían en lenguaje **ZPL** (como gráfico `^GFA`). Las impresoras de otras marcas
(como 3nStar) deben tener activada la emulación ZPL.

## Orientación

La etiqueta se dibuja completa como imagen a la resolución de la impresora (el código de barras punto por
punto) y se envía como un gráfico ZPL. En el formato `3 x 2,2 cm vertical` la imagen se gira 90°, porque
ese rollo pasa por la impresora con los 3 cm a lo ancho del cabezal. Si sale al revés, marque
**Girar 180°** en la configuración. La vista previa es exactamente lo que se imprime.

### Si el lector no lee el código de barras

En el editor de configuración, **Hoja de prueba de lectura…** imprime el mismo código con varios ajustes
(PRUEBA A, B, C…): grosor de barra, reducción de barra, oscuridad y velocidad. Pase el lector por cada
una y deje en la configuración los ajustes de la que lea mejor:

- **Grosor de barra:** automático deja el margen blanco que pide la norma a los lados del código.
- **Reducción de barra:** quita 1 punto a cada barra (si la tinta se corre); solo con barra de 2 o 3.
- **Oscuridad:** ajuste respecto a la impresora (`^MD`); negativo = más claro. Vacío = no cambia.
- **Velocidad:** pulgadas por segundo (`^PR`); más lenta suele marcar mejor. Vacío = no cambia.

## Ejecutables (.exe)

Se generan dos:

| Ejecutable            | Qué es                    | Icono            |
|-----------------------|---------------------------|------------------|
| `EtiquetasZebra.exe`  | El programa de etiquetas  | `logoapp.bmp`    |
| `ConfigEtiquetas.exe` | El editor de `config.xml` | `logoconfig.bmp` |

- **En su PC:** ponga `logoapp.bmp` y `logoconfig.bmp` en la carpeta del proyecto y haga doble clic en
  **`generar_exe.bat`**. Los `.exe` (y las imágenes) quedan en la carpeta `dist`. Si falta una imagen,
  ese `.exe` queda sin icono.
- **Automático:** cada cambio en `etiquetas/` genera los `.exe` en GitHub Actions
  (pestaña *Actions* → *Ejecutable etiquetas Zebra* → *Artifacts*), después de pasar las pruebas en Windows.

Los `.exe` no necesitan Python. `config.xml`, el logo de la etiqueta y las imágenes de los iconos van
**junto a los .exe**.

## Pruebas

```bash
py -m pytest tests
```

## Línea de comandos (opcional)

`etiquetas_zebra.py` imprime desde un CSV o un solo código, sin base de datos:

```bash
python etiquetas_zebra.py --csv productos.csv --formato "3 x 2,2 cm vertical" --impresora 192.168.1.50
```
