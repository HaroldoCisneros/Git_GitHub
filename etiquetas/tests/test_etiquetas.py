"""
Pruebas del programa de etiquetas.  Ejecutar desde la carpeta etiquetas:

    py -m pytest tests

Pruebas opcionales (se saltan si no hay datos):
  ETIQUETAS_SQL=1          usa config.xml y prueba la conexión real a SQL Server
  ETIQUETAS_IMPRESORA=...  (solo Windows) nombre de una impresora instalada cuyo
  ETIQUETAS_SALIDA=...     puerto escribe en ese archivo: prueba la impresión RAW
"""

import os
import re
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402
import db  # noqa: E402
import vista_previa  # noqa: E402
import winprint  # noqa: E402
import zpl  # noqa: E402

LARGO = "DETERGENTE EN POLVO MULTIUSO FRAGANCIA LIMÓN EXTRA RENDIDOR CON SUAVIZANTE 2KG"


@pytest.fixture
def logo(tmp_path):
    from PIL import Image, ImageDraw

    im = Image.new("RGB", (600, 170), "white")
    ImageDraw.Draw(im).rectangle([40, 20, 560, 150], fill="black")
    ruta = tmp_path / "logo.png"
    im.save(ruta)
    return str(ruta)


# ------------------------------------------------------------- código de barras

@pytest.mark.parametrize("datos, esperado", [
    ("23329003", [105, 23, 32, 90, 3, 62]),          # control calculado a mano
    ("12", [105, 12, 14]),
])
def test_code128_simbolos(datos, esperado):
    assert vista_previa.code128(datos) == esperado


@pytest.mark.parametrize("datos", ["23329003", "7591234567890", "A001", "1811010/F-050-0", "X", "12345"])
def test_ancho_code128_coincide(datos):
    """El ancho que usa zpl.py para centrar coincide con el código real."""
    simbolos = vista_previa.code128(datos)
    modulos = sum(int(c) for s in simbolos for c in vista_previa._C128[s]) + 13
    assert zpl.modulos_code128(datos) == modulos


# ------------------------------------------------------------- etiqueta

def test_escapar_zpl():
    assert zpl.escapar_zpl("A^B~C\\D") == "A\\5EB\\7EC\\5CD"


def test_formatear_precio():
    assert zpl.formatear_precio(1234.5, "Bs.") == "Bs. 1.234,50"
    assert zpl.formatear_precio("45,5", "$") == "$ 45,50"


def leer_barras(img) -> str | None:
    """Lee el código de barras de la imagen con zbarimg (si está instalado)."""
    import shutil
    import subprocess
    import tempfile

    if not shutil.which("zbarimg"):
        return None
    with tempfile.TemporaryDirectory() as tmp:
        ruta = Path(tmp) / "e.png"
        img.resize((img.width * 3, img.height * 3)).save(ruta)
        r = subprocess.run(["zbarimg", "-q", "--raw", str(ruta)], capture_output=True, text=True)
        return r.stdout.strip()


@pytest.mark.parametrize("formato", list(zpl.FORMATOS))
@pytest.mark.parametrize("con_logo", [False, True])
@pytest.mark.parametrize("con_precio", [False, True])
@pytest.mark.parametrize("codigo, ref", [("23329003", "1811010/F-050-0"), ("7591234567890", ""),
                                         ("AB-1234-X", "REF-LARGA-0001")])
def test_todo_cabe_en_la_etiqueta(formato, con_logo, con_precio, codigo, ref, logo):
    f = zpl.FORMATOS[formato]
    p = zpl.Producto(codigo=codigo, nombre=LARGO, precio=12345.67, referencia=ref)
    img = zpl.imagen_etiqueta(p, f, logo=logo if con_logo else "", con_precio=con_precio)
    ancho, alto = round(f.ancho * 203 / 25.4), round(f.alto * 203 / 25.4)
    assert img.size == (ancho, alto)

    # nada se sale: los bordes de la etiqueta quedan en blanco
    borde = round(f.margen * 203 / 25.4) // 2
    px = img.load()
    for x in range(ancho):
        for y in list(range(borde)) + list(range(alto - borde, alto)):
            assert px[x, y] == 255, f"punto negro en el borde ({x},{y})"
    for y in range(alto):
        for x in list(range(borde)) + list(range(ancho - borde, ancho)):
            assert px[x, y] == 255, f"punto negro en el borde ({x},{y})"

    leido = leer_barras(img)
    if leido is not None:
        assert leido == codigo


@pytest.mark.parametrize("formato", list(zpl.FORMATOS))
def test_zpl_girado(formato):
    f = zpl.FORMATOS[formato]
    p = zpl.Producto("23329003", "SILICON ANTIHONGOS", copias=3)
    texto = zpl.generar_zpl(p, f)
    ancho, alto = round(f.ancho * 203 / 25.4), round(f.alto * 203 / 25.4)
    if f.rotacion in (90, 270):
        ancho, alto = alto, ancho
    assert f"^PW{ancho}" in texto and f"^LL{alto}" in texto and "^PQ3" in texto
    total, _, por_fila = (int(v) for v in re.search(r"\^GFA,(\d+),(\d+),(\d+),", texto).groups())
    assert por_fila == (ancho + 7) // 8 and total == por_fila * alto
    datos = re.search(r"\^GFA,\d+,\d+,\d+,([0-9A-F]+)\^FS", texto).group(1)
    assert len(datos) == total * 2


def test_girar_180():
    f = zpl.FORMATOS["3 x 2,2 cm vertical"]
    p = zpl.Producto("23329003", "SILICON ANTIHONGOS")
    assert zpl.generar_zpl(p, f) != zpl.generar_zpl(p, f, girar=180)


def test_descripcion_larga_se_corta():
    from PIL import Image, ImageDraw

    dib = ImageDraw.Draw(Image.new("L", (10, 10)))
    letra = zpl.fuente(18)
    filas = zpl.envolver(dib, LARGO + " " + LARGO, letra, 150, 3)
    assert len(filas) == 3 and filas[-1].endswith("…")
    assert all(dib.textlength(f, font=letra) <= 150 for f in filas)
    palabra = zpl.envolver(dib, "A" * 80, letra, 150, 4)
    assert all(dib.textlength(f, font=letra) <= 150 for f in palabra)


@pytest.mark.parametrize("codigo", ["00121008", "23329003"])
def test_barras_gruesas_en_etiqueta_pequena(codigo):
    """Los códigos de 8 dígitos llevan barras de 2 puntos (1 punto no lo leen muchos lectores)."""
    f = zpl.FORMATOS["3 x 2,2 cm vertical"]
    assert zpl.modulo_barras(codigo, f) == 2
    img = zpl.imagen_etiqueta(zpl.Producto(codigo, "PINTURA"), f)
    leido = leer_barras(img)
    if leido is not None:
        assert leido == codigo


def test_grosor_forzado():
    f = zpl.FORMATOS["3 x 2,2 cm vertical"]
    assert zpl.modulo_barras("23329003", f, barra=1) == 1
    assert zpl.modulo_barras("23329003", f, barra=3) == 0
    with pytest.raises(ValueError):
        zpl.imagen_etiqueta(zpl.Producto("23329003", "X"), f, barra=3)


def test_codigo_demasiado_largo():
    f = zpl.FORMATOS["3 x 2,2 cm vertical"]
    assert not zpl.cabe_barras("ABC-12345-XYZ", f)
    with pytest.raises(ValueError):
        zpl.generar_zpl(zpl.Producto("ABC-12345-XYZ", "X"), f)
    assert zpl.cabe_barras("ABC-12345-XYZ", zpl.FORMATOS["5 x 2,5 cm horizontal"])


def test_copias_y_lote():
    f = zpl.FORMATOS["3 x 2,2 cm vertical"]
    ps = [zpl.Producto("1", "A", copias=5), zpl.Producto("2", "B")]
    texto = zpl.generar_lote(ps, f)
    assert texto.count("^XA") == 2 and "^PQ5" in texto and "^PQ1" in texto


def test_logo_no_existe():
    with pytest.raises(FileNotFoundError):
        zpl.logo_imagen("no_existe.png", 100, 40)


def test_logo_tamano(logo):
    img = zpl.logo_imagen(logo, 150, 36)
    assert img.width <= 150 and img.height <= 36


def test_vista_previa(logo):
    f = zpl.FORMATOS["3 x 2,2 cm vertical"]
    p = zpl.Producto("23329003", LARGO, referencia="1811010/F-050-0")
    texto = zpl.generar_lote([p] * 3, f, logo=logo)
    imagenes = vista_previa.renderizar(texto)            # lo que recibe la impresora (girado)
    assert len(imagenes) == 3 and imagenes[0].size == (240, 176)
    derecha = zpl.imagen_etiqueta(p, f, logo=logo)
    assert imagenes[0].convert("1").tobytes() == derecha.rotate(90, expand=True).convert("1").tobytes()
    assert vista_previa.lamina([derecha] * 3).width > 176


# ------------------------------------------------------------- configuración

def test_config_ida_y_vuelta(tmp_path):
    cfg = config.nueva()
    cfg["sql"]["clave"] = "cl@ve<&>"
    cfg["etiqueta"]["logo"] = r"C:\Logos\ñandú.png"
    ruta = tmp_path / "config.xml"
    config.guardar(cfg, ruta)
    leida = config.cargar(ruta)
    assert leida == cfg
    assert leida.entero("impresora", "puerto") == 9100


def test_config_valores_que_faltan(tmp_path):
    ruta = tmp_path / "config.xml"
    ruta.write_text("<configuracion><sql><servidor>appsrv</servidor></sql></configuracion>",
                    encoding="utf-8")
    cfg = config.cargar(ruta)
    assert cfg["sql"]["servidor"] == "appsrv"
    assert cfg["tabla"]["codigo"] == "co_art"     # valor por defecto


def test_config_convierte_ini(tmp_path):
    (tmp_path / "config.ini").write_text("[sql]\nservidor = viejo\n[impresora]\ntipo = windows\n",
                                         encoding="utf-8")
    cfg = config.cargar(tmp_path / "config.xml")
    assert cfg["sql"]["servidor"] == "viejo" and cfg["impresora"]["tipo"] == "windows"
    assert (tmp_path / "config.xml").exists()


def test_config_no_existe(tmp_path):
    with pytest.raises(FileNotFoundError):
        config.cargar(tmp_path / "config.xml")


# ------------------------------------------------------------- base de datos

@pytest.mark.parametrize("nombre", ["art", "dbo.art", "co_art"])
def test_columna_valida(nombre):
    assert db._col(nombre).startswith("[")


@pytest.mark.parametrize("nombre", ["art; DROP TABLE art", "a b", "[x]", "", "1abc"])
def test_columna_invalida(nombre):
    with pytest.raises(ValueError):
        db._col(nombre)


def test_consulta_sin_referencia():
    a = db.Articulos(config.nueva())
    assert a._select().strip().endswith("'' FROM [art]")


@pytest.mark.skipif(not os.environ.get("ETIQUETAS_SQL"), reason="sin ETIQUETAS_SQL")
def test_sql_real():
    a = db.Articulos(config.cargar())
    assert a.probar() > 0
    assert a.buscar_codigos(["NO-EXISTE-123"]) == {}


# ------------------------------------------------------------- impresoras Windows

def test_sugerida():
    assert winprint.sugerida(["HP", "ZDesigner GC420t", "PDF"]) == "ZDesigner GC420t"


@pytest.mark.skipif(sys.platform != "win32", reason="solo Windows")
def test_windows_lista_impresoras():
    lista = winprint.impresoras()
    assert isinstance(lista, list)
    winprint.predeterminada()


@pytest.mark.skipif(sys.platform != "win32", reason="solo Windows")
def test_windows_impresora_inexistente():
    with pytest.raises(OSError):
        winprint.imprimir_raw(b"^XA^XZ", "Impresora que no existe 123")


@pytest.mark.skipif(not os.environ.get("ETIQUETAS_IMPRESORA"), reason="sin ETIQUETAS_IMPRESORA")
def test_windows_imprimir_raw():
    import time

    nombre = os.environ["ETIQUETAS_IMPRESORA"]
    salida = Path(os.environ["ETIQUETAS_SALIDA"])
    assert nombre in winprint.impresoras()
    texto = zpl.generar_zpl(zpl.Producto("23329003", "PRUEBA ÑANDÚ"), zpl.FORMATOS["3 x 2,2 cm vertical"])
    zpl.enviar(texto, "windows", nombre)
    for _ in range(30):                      # el spooler escribe el archivo en segundo plano
        if salida.exists() and salida.stat().st_size >= len(texto.encode()):
            break
        time.sleep(1)
    assert salida.read_bytes() == texto.encode("utf-8")
