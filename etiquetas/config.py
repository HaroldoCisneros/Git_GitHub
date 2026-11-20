"""
Configuración del programa en config.xml.

Ejemplo de config.xml:

<configuracion>
  <sql>
    <servidor>localhost</servidor>
    <puerto>1433</puerto>
    ...
  </sql>
  <tabla> ... </tabla>
  <impresora> ... </impresora>
  <etiqueta> ... </etiqueta>
</configuracion>
"""

import configparser
import xml.etree.ElementTree as ET
from pathlib import Path
import sys

# Carpeta del programa: junto al .exe cuando está empaquetado con PyInstaller
CARPETA = (Path(sys.executable).resolve().parent if getattr(sys, "frozen", False)
           else Path(__file__).resolve().parent)
RUTA = CARPETA / "config.xml"

# Secciones y valores por defecto (también definen el orden en el XML)
DEFECTO: dict[str, dict[str, str]] = {
    "sql": {
        "servidor": "localhost",
        "puerto": "1433",
        "base_datos": "VALPA_A",
        "usuario": "profit",
        "clave": "",
        "timeout": "5",
    },
    "tabla": {
        "nombre": "art",
        "codigo": "co_art",
        "descripcion": "art_des",
        "precio": "prec_vta1",
        "barras": "co_art",
        "referencia": "",       # columna opcional que se imprime en negrita bajo el código
        "modelo": "modelo",     # columna opcional para buscar por modelo
    },
    "impresora": {
        "tipo": "red",          # red | windows | dispositivo | archivo
        "destino": "192.168.1.50",
        "puerto": "9100",
        "dpi": "203",
        "oscuridad": "-8",      # ajuste -30..30 respecto a la impresora (-8: probado en PRUEBA B)
        "velocidad": "",        # pulgadas por segundo (2..6); vacío = la de la impresora
    },
    "etiqueta": {
        "formato": "3 x 2,2 cm vertical",
        "moneda": "Bs.",
        "logo": "",             # imagen del logo (png, jpg, bmp); vacío = sin logo
        "precio": "no",         # si | no: imprimir el precio
        "girar": "0",           # 0 | 180: girar la etiqueta si sale al revés
        "barra": "0",           # grosor de la barra más fina en puntos: 0 = automático, 1, 2, 3
        "reduccion": "0",       # puntos que se le quitan a cada barra (si la tinta se corre)
    },
}


class Config(dict):
    """{seccion: {clave: valor}} con ayuda para leer números."""

    def entero(self, seccion: str, clave: str) -> int:
        valor = self[seccion].get(clave, "")
        try:
            return int(valor)
        except ValueError:
            return int(DEFECTO[seccion][clave])


def nueva() -> Config:
    return Config({s: dict(v) for s, v in DEFECTO.items()})


def existe(ruta: Path = RUTA) -> bool:
    return ruta.exists()


def cargar(ruta: Path = RUTA) -> Config:
    """Lee config.xml. Si no existe pero hay un config.ini antiguo, lo convierte."""
    cfg = nueva()
    if not ruta.exists():
        ini = ruta.with_suffix(".ini")
        if ini.exists():
            antiguo = configparser.ConfigParser()
            antiguo.read(ini, encoding="utf-8")
            for s in cfg:
                if antiguo.has_section(s):
                    cfg[s].update({k: v for k, v in antiguo[s].items() if k in cfg[s]})
            guardar(cfg, ruta)
            return cfg
        raise FileNotFoundError(f"No existe {ruta.name}. Ejecute configurar.py para crearlo.")

    raiz = ET.parse(ruta).getroot()
    for s in cfg:
        nodo = raiz.find(s)
        if nodo is None:
            continue
        for k in cfg[s]:
            hijo = nodo.find(k)
            if hijo is not None:
                cfg[s][k] = (hijo.text or "").strip()
    return cfg


def guardar(cfg: dict, ruta: Path = RUTA) -> None:
    raiz = ET.Element("configuracion")
    for s, valores in cfg.items():
        nodo = ET.SubElement(raiz, s)
        for k, v in valores.items():
            ET.SubElement(nodo, k).text = str(v)
    ET.indent(raiz, space="  ")
    ET.ElementTree(raiz).write(ruta, encoding="utf-8", xml_declaration=True)


def _numero(texto: str) -> int | None:
    try:
        return int(str(texto).strip())
    except ValueError:
        return None


def opciones(cfg: Config) -> dict:
    """Ajustes de impresión de la configuración, listos para zpl.generar_lote(..., **opciones)."""
    et, imp = cfg["etiqueta"], cfg["impresora"]
    return dict(
        dpi=cfg.entero("impresora", "dpi"),
        moneda=et["moneda"],
        logo=et["logo"],
        con_precio=et["precio"].strip().lower() in ("si", "sí", "1", "true"),
        girar=cfg.entero("etiqueta", "girar"),
        barra=cfg.entero("etiqueta", "barra"),
        reduccion=cfg.entero("etiqueta", "reduccion"),
        oscuridad=_numero(imp["oscuridad"]),
        velocidad=_numero(imp["velocidad"]),
    )
