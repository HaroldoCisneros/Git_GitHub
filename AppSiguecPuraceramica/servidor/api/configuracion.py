"""
Lectura del archivo de configuración config.xml del servidor de AppSiguecPuraceramica.

Mismo estilo de XML de AppSighecMovil. Secciones:

    <sqlserver>      Conexión al SQL Server donde están las bases de Profit 2K12 y la
                     base de la App (obligatoria). OJO: aquí NO va la base de la
                     empresa; las empresas salen de la tabla SIGUEC_PURACERAMICA.dbo.empresas.
    <aplicacion>     Base de usuarios de la App, duración de la sesión y bloqueo por
                     claves erradas.
    <profit>         Nombres de las tablas y columnas de Profit Plus 2K12 que se leen
                     (en 2K12 cambian respecto a 2K8: saArticulo en vez de ART,
                     saStockAlmacen en vez de ST_ALMAC...). Si en la base real algún
                     nombre es distinto, se corrige AQUÍ sin tocar el código.
    <servidor_web>   Dirección y puerto donde escucha la API.

El config.xml va en la carpeta "servidor" (junto a main.py). El real no se sube
al repositorio porque tiene la clave de SQL Server (ver config.ejemplo.xml).
"""

from __future__ import annotations

import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import Tuple

# Nombre del archivo de configuración.
NOMBRE_ARCHIVO = "config.xml"

# Nombre válido de tabla o columna de SQL Server (se revisa porque estos nombres
# van DENTRO del SQL: no pueden ser parámetros "?").
PATRON_IDENTIFICADOR = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,127}$")


class ErrorConfiguracion(Exception):
    """El config.xml no existe, está dañado o le falta un dato obligatorio."""


# --------------------------------------------------------------------------
# Clases de datos: una por cada sección del XML
# --------------------------------------------------------------------------

@dataclass
class ConfigSQL:
    """Datos de conexión a SQL Server (sección <sqlserver>)."""
    servidor: str
    usuario: str = ""          # Vacío = autenticación de Windows.
    clave: str = ""
    driver: str = "ODBC Driver 17 for SQL Server"

    @property
    def usa_autenticacion_windows(self) -> bool:
        """True cuando no se indicó usuario en el XML."""
        return not self.usuario


@dataclass
class ConfigAplicacion:
    """Opciones de la App (sección <aplicacion>)."""
    base_usuarios: str = "SIGUEC_PURACERAMICA"  # Base con usuarios, artículos copiados e inventarios.
    horas_sesion: int = 12                      # Horas que dura el token antes de pedir clave otra vez.
    intentos_maximos: int = 5                   # Claves erradas seguidas antes de bloquear al usuario.
    minutos_bloqueo: int = 15                   # Minutos que queda bloqueado.
    tamano_pagina_maximo: int = 100             # Máximo de renglones por página en las consultas.


@dataclass
class ConfigProfit:
    """
    Tablas y columnas de Profit Plus 2K12 (sección <profit>).

    Los valores por defecto son los de Profit Plus 2KDoce. La App SOLO LEE
    estas tablas: nunca escribe en Profit.
    """
    # Artículos
    tabla_articulos: str = "saArticulo"
    col_codigo: str = "co_art"
    col_descripcion: str = "art_des"
    col_anulado: str = "anulado"              # Si no existe en la base, todos se toman como activos.
    # Columnas del artículo que también sirven como "código para escanear"
    # (además de co_art). Las que no existan en la base se ignoran solas.
    columnas_codigo: Tuple[str, ...] = ("modelo", "ref")
    # Tabla aparte de códigos de barra (opcional; vacío = no hay). Un renglón por código.
    tabla_codigos_barra: str = ""
    col_codigo_barra: str = "cod_barra"
    col_codigo_barra_articulo: str = "co_art"
    # Almacenes
    tabla_almacenes: str = "saAlmacen"
    col_almacen: str = "co_alma"
    col_almacen_descripcion: str = "des_alma"
    # Existencia por almacén: en 2K12 hay un renglón por tipo de stock (ACT actual,
    # COM comprometido, LLE por llegar, DES por despachar). Se usa el "actual".
    tabla_stock: str = "saStockAlmacen"
    col_stock_almacen: str = "co_alma"
    col_stock_articulo: str = "co_art"
    col_stock_tipo: str = "tipo"              # Vacío = la tabla no tiene tipo (un renglón por artículo).
    tipo_stock_actual: str = "ACT"
    col_stock_cantidad: str = "stock"

    def validar(self) -> None:
        """:raises ErrorConfiguracion: si un nombre no es un identificador válido de SQL."""
        nombres = [v for k, v in vars(self).items() if k not in ("columnas_codigo", "tipo_stock_actual") and v]
        nombres += list(self.columnas_codigo)
        for nombre in nombres:
            if not PATRON_IDENTIFICADOR.match(nombre):
                raise ErrorConfiguracion(f"<profit>: '{nombre}' no es un nombre válido de tabla o columna.")


@dataclass
class ConfigServidorWeb:
    """Dónde escucha la API (sección <servidor_web>)."""
    host: str = "127.0.0.1"   # 127.0.0.1 = solo el mismo equipo (detrás del túnel o del proxy HTTPS).
    puerto: int = 8000


@dataclass
class Configuracion:
    """Toda la configuración del servidor."""
    sql: ConfigSQL
    aplicacion: ConfigAplicacion = field(default_factory=ConfigAplicacion)
    profit: ConfigProfit = field(default_factory=ConfigProfit)
    servidor_web: ConfigServidorWeb = field(default_factory=ConfigServidorWeb)


# --------------------------------------------------------------------------
# Lectura
# --------------------------------------------------------------------------

def _texto(nodo, etiqueta: str, defecto: str = "") -> str:
    """Texto de <etiqueta> dentro de nodo, sin espacios a los lados (o el defecto)."""
    if nodo is None:
        return defecto
    hijo = nodo.find(etiqueta)
    if hijo is None or hijo.text is None:
        return defecto
    return hijo.text.strip()


def _entero(nodo, etiqueta: str, defecto: int) -> int:
    texto = _texto(nodo, etiqueta)
    if not texto:
        return defecto
    try:
        return int(texto)
    except ValueError as error:
        raise ErrorConfiguracion(f"<{etiqueta}> debe ser un número entero (tiene '{texto}').") from error


def _vacio_permitido(nodo, etiqueta: str, defecto: str) -> str:
    """Como _texto, pero una etiqueta presente y vacía (<x/>) significa "no hay"."""
    if nodo is None or nodo.find(etiqueta) is None:
        return defecto
    return (nodo.find(etiqueta).text or "").strip()


def _leer_profit(nodo) -> ConfigProfit:
    d = ConfigProfit()
    columnas = _vacio_permitido(nodo, "columnas_codigo", ",".join(d.columnas_codigo))
    profit = ConfigProfit(
        tabla_articulos=_texto(nodo, "tabla_articulos", d.tabla_articulos),
        col_codigo=_texto(nodo, "col_codigo", d.col_codigo),
        col_descripcion=_texto(nodo, "col_descripcion", d.col_descripcion),
        col_anulado=_vacio_permitido(nodo, "col_anulado", d.col_anulado),
        columnas_codigo=tuple(c.strip() for c in columnas.split(",") if c.strip()),
        tabla_codigos_barra=_vacio_permitido(nodo, "tabla_codigos_barra", d.tabla_codigos_barra),
        col_codigo_barra=_texto(nodo, "col_codigo_barra", d.col_codigo_barra),
        col_codigo_barra_articulo=_texto(nodo, "col_codigo_barra_articulo", d.col_codigo_barra_articulo),
        tabla_almacenes=_texto(nodo, "tabla_almacenes", d.tabla_almacenes),
        col_almacen=_texto(nodo, "col_almacen", d.col_almacen),
        col_almacen_descripcion=_vacio_permitido(nodo, "col_almacen_descripcion", d.col_almacen_descripcion),
        tabla_stock=_texto(nodo, "tabla_stock", d.tabla_stock),
        col_stock_almacen=_texto(nodo, "col_stock_almacen", d.col_stock_almacen),
        col_stock_articulo=_texto(nodo, "col_stock_articulo", d.col_stock_articulo),
        col_stock_tipo=_vacio_permitido(nodo, "col_stock_tipo", d.col_stock_tipo),
        tipo_stock_actual=_texto(nodo, "tipo_stock_actual", d.tipo_stock_actual),
        col_stock_cantidad=_texto(nodo, "col_stock_cantidad", d.col_stock_cantidad),
    )
    profit.validar()
    return profit


def leer_configuracion(ruta: str) -> Configuracion:
    """
    Lee el config.xml.

    :raises ErrorConfiguracion: si el archivo no existe, está dañado o falta el servidor.
    """
    if not os.path.isfile(ruta):
        raise ErrorConfiguracion(
            f"No se encontró {ruta}.\nCopie config.ejemplo.xml como config.xml y coloque los datos reales.")
    try:
        raiz = ET.parse(ruta).getroot()
    except ET.ParseError as error:
        raise ErrorConfiguracion(f"El archivo {ruta} está dañado: {error}") from error

    nodo_sql = raiz.find("sqlserver")
    servidor = _texto(nodo_sql, "servidor")
    if not servidor:
        raise ErrorConfiguracion("Falta <sqlserver><servidor> en el config.xml.")
    sql = ConfigSQL(
        servidor=servidor,
        usuario=_texto(nodo_sql, "usuario"),
        clave=_texto(nodo_sql, "clave"),
        driver=_texto(nodo_sql, "driver", ConfigSQL.driver),
    )

    nodo_app = raiz.find("aplicacion")
    defecto = ConfigAplicacion()
    aplicacion = ConfigAplicacion(
        base_usuarios=_texto(nodo_app, "base_usuarios", defecto.base_usuarios),
        horas_sesion=_entero(nodo_app, "horas_sesion", defecto.horas_sesion),
        intentos_maximos=_entero(nodo_app, "intentos_maximos", defecto.intentos_maximos),
        minutos_bloqueo=_entero(nodo_app, "minutos_bloqueo", defecto.minutos_bloqueo),
        tamano_pagina_maximo=_entero(nodo_app, "tamano_pagina_maximo", defecto.tamano_pagina_maximo),
    )

    nodo_web = raiz.find("servidor_web")
    servidor_web = ConfigServidorWeb(
        host=_texto(nodo_web, "host", ConfigServidorWeb.host),
        puerto=_entero(nodo_web, "puerto", ConfigServidorWeb.puerto),
    )
    return Configuracion(sql=sql, aplicacion=aplicacion, profit=_leer_profit(raiz.find("profit")),
                         servidor_web=servidor_web)
