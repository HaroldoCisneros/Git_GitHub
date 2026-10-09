"""
Lectura del archivo de configuración config.xml.

El archivo tiene el MISMO formato que el del Visor de Precios (secciones
<sqlserver>, <rutas> y <pantalla>) y además tres secciones nuevas:

    <caja>
        <codigo>01</codigo>            Código de la caja en la tabla "cajas" de Profit.
    </caja>
    <usuario>
        <codigo>...</codigo>           Usuario de Profit (tabla employee).
        <clave>...</clave>             Su clave de Profit.
        <base>MasterProfit</base>      Base donde está la tabla de usuarios (opcional).
        <tabla>employee</tabla>        Tabla de usuarios (opcional).
    </usuario>
    <ambiente>                         (sección opcional)
        <cod_emp>...</cod_emp>         Empresa en PPV_AMBIENTE (por defecto, <basedatos>).
        <base>...</base>               Base donde está PPV_AMBIENTE (por defecto, la de la empresa).
    </ambiente>
    <seguridad>
        <clave_salida>...</clave_salida>   Contraseña para salir de la aplicación.
    </seguridad>

El config.xml debe estar en la misma carpeta del ejecutable.
Todos los valores se guardan en clases de datos (dataclasses) para que el
resto del programa use, por ejemplo, ``config.sql.servidor`` en lugar de
andar buscando etiquetas XML.
"""

from __future__ import annotations

import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

from .utilidades import carpeta_programa, resolver_ruta

# Nombre del archivo de configuración.
NOMBRE_ARCHIVO = "config.xml"

# Contraseña provisional para salir de la aplicación. Se usa si el XML no
# trae la sección <seguridad>.
CLAVE_SALIDA_POR_DEFECTO = "9898989898"

# Base y tabla de usuarios por defecto (las mismas del Sistema Web).
BASE_USUARIOS_POR_DEFECTO = "MasterProfit"
TABLA_USUARIOS_POR_DEFECTO = "employee"

# Los nombres de base y tabla van dentro del texto SQL (no pueden ir como
# parámetro), por eso solo se aceptan letras, números y "_".
PATRON_NOMBRE_SQL = re.compile(r"^[A-Za-z0-9_]{1,128}$")


class ErrorConfiguracion(Exception):
    """Se lanza cuando el config.xml no existe, está dañado o le falta un dato obligatorio."""


# --------------------------------------------------------------------------
# Clases de datos: una por cada sección del XML
# --------------------------------------------------------------------------

@dataclass
class ConfigSQL:
    """Datos de conexión a SQL Server (sección <sqlserver>)."""
    servidor: str
    basedatos: str
    usuario: str = ""          # Vacío = autenticación de Windows.
    clave: str = ""
    driver: str = "ODBC Driver 17 for SQL Server"

    @property
    def usa_autenticacion_windows(self) -> bool:
        """True cuando no se indicó usuario en el XML."""
        return not self.usuario


@dataclass
class ConfigRutas:
    """Rutas de archivos (sección <rutas>). Ya vienen convertidas a rutas absolutas."""
    imagenes: str = ""
    video: str = ""
    logo: str = ""
    imagen_defecto: str = ""


@dataclass
class ConfigPantalla:
    """Opciones de presentación (sección <pantalla>)."""
    segundos_producto: int = 20
    simbolo_moneda: str = "$"
    decimales: int = 2
    separador_miles: str = "."
    separador_decimal: str = ","
    nombre_negocio: str = ""


@dataclass
class ConfigCaja:
    """Datos de la caja (sección nueva <caja>)."""
    codigo: str


@dataclass
class ConfigUsuario:
    """
    Usuario con el que trabaja la caja (sección nueva <usuario>).
    Se valida contra la tabla de usuarios de Profit al arrancar.
    """
    codigo: str
    clave: str
    base: str = BASE_USUARIOS_POR_DEFECTO
    tabla: str = TABLA_USUARIOS_POR_DEFECTO


@dataclass
class ConfigAmbiente:
    """
    Dónde buscar el ambiente del usuario (sección nueva y opcional <ambiente>).

    El ambiente es la fila de PPV_AMBIENTE con la llave COD_EMP + COD_USU.
    """
    cod_emp: str          # Valor de COD_EMP. Por defecto el nombre de la base de la empresa.
    base: str = ""        # Vacío = la base de la empresa (la de <sqlserver><basedatos>).


@dataclass
class ConfigSeguridad:
    """Opciones de seguridad (sección nueva <seguridad>)."""
    clave_salida: str = CLAVE_SALIDA_POR_DEFECTO


@dataclass
class Configuracion:
    """Agrupa toda la configuración leída del XML."""
    sql: ConfigSQL
    rutas: ConfigRutas
    pantalla: ConfigPantalla
    caja: ConfigCaja
    usuario: ConfigUsuario
    ambiente: ConfigAmbiente
    seguridad: ConfigSeguridad = field(default_factory=ConfigSeguridad)
    archivo: str = ""          # Ruta del XML leído (para mensajes de error).


# --------------------------------------------------------------------------
# Funciones auxiliares de lectura
# --------------------------------------------------------------------------

def _texto(nodo: ET.Element | None, etiqueta: str, defecto: str = "") -> str:
    """
    Devuelve el texto de <etiqueta> dentro de ``nodo`` sin espacios a los lados.
    Si la sección o la etiqueta no existen, devuelve ``defecto``.
    """
    if nodo is None:
        return defecto
    hijo = nodo.find(etiqueta)
    if hijo is None or hijo.text is None:
        return defecto
    return hijo.text.strip()


def _entero(nodo: ET.Element | None, etiqueta: str, defecto: int) -> int:
    """Igual que _texto pero convierte a número entero; si no se puede, usa el defecto."""
    valor = _texto(nodo, etiqueta)
    try:
        return int(valor) if valor else defecto
    except ValueError:
        raise ErrorConfiguracion(f"El valor de <{etiqueta}> debe ser un número entero: '{valor}'")


def _obligatorio(nodo: ET.Element | None, seccion: str, etiqueta: str) -> str:
    """Lee una etiqueta que no puede quedar vacía; si falta, lanza ErrorConfiguracion."""
    valor = _texto(nodo, etiqueta)
    if not valor:
        raise ErrorConfiguracion(f"Falta el valor de <{seccion}><{etiqueta}> en {NOMBRE_ARCHIVO}")
    return valor


def _nombre_sql(nodo: ET.Element | None, seccion: str, etiqueta: str, defecto: str) -> str:
    """Lee un nombre de base o tabla y comprueba que sea seguro ponerlo dentro del SQL."""
    valor = _texto(nodo, etiqueta, defecto) or defecto
    if not valor:
        return ""          # Opcional y sin valor: se usa la base de la conexión.
    if not PATRON_NOMBRE_SQL.match(valor):
        raise ErrorConfiguracion(
            f"<{seccion}><{etiqueta}> solo puede tener letras, números y _ : '{valor}'")
    return valor


# --------------------------------------------------------------------------
# Función principal
# --------------------------------------------------------------------------

def cargar_configuracion(ruta: str | None = None) -> Configuracion:
    """
    Lee el config.xml y devuelve un objeto Configuracion.

    :param ruta: ruta del XML. Si no se indica se usa config.xml junto al programa.
    :raises ErrorConfiguracion: si el archivo no existe, no es un XML válido
        o le falta algún dato obligatorio.
    """
    ruta = ruta or os.path.join(carpeta_programa(), NOMBRE_ARCHIVO)

    if not os.path.isfile(ruta):
        raise ErrorConfiguracion(f"No se encontró el archivo de configuración:\n{ruta}")

    try:
        raiz = ET.parse(ruta).getroot()
    except ET.ParseError as error:
        raise ErrorConfiguracion(f"El archivo {NOMBRE_ARCHIVO} tiene un error de formato:\n{error}")

    # --- <sqlserver> -------------------------------------------------------
    nodo_sql = raiz.find("sqlserver")
    sql = ConfigSQL(
        servidor=_obligatorio(nodo_sql, "sqlserver", "servidor"),
        basedatos=_obligatorio(nodo_sql, "sqlserver", "basedatos"),
        usuario=_texto(nodo_sql, "usuario"),
        clave=_texto(nodo_sql, "clave"),
        driver=_texto(nodo_sql, "driver", "ODBC Driver 17 for SQL Server"),
    )

    # --- <rutas> -----------------------------------------------------------
    nodo_rutas = raiz.find("rutas")
    rutas = ConfigRutas(
        imagenes=resolver_ruta(_texto(nodo_rutas, "imagenes")),
        video=resolver_ruta(_texto(nodo_rutas, "video")),
        logo=resolver_ruta(_texto(nodo_rutas, "logo")),
        imagen_defecto=resolver_ruta(_texto(nodo_rutas, "imagen_defecto")),
    )

    # --- <pantalla> --------------------------------------------------------
    nodo_pantalla = raiz.find("pantalla")
    pantalla = ConfigPantalla(
        segundos_producto=_entero(nodo_pantalla, "segundos_producto", 20),
        simbolo_moneda=_texto(nodo_pantalla, "simbolo_moneda", "$"),
        decimales=_entero(nodo_pantalla, "decimales", 2),
        separador_miles=_texto(nodo_pantalla, "separador_miles", "."),
        separador_decimal=_texto(nodo_pantalla, "separador_decimal", ","),
        nombre_negocio=_texto(nodo_pantalla, "nombre_negocio"),
    )

    # --- <caja> (nuevo, obligatorio) ----------------------------------------
    caja = ConfigCaja(codigo=_obligatorio(raiz.find("caja"), "caja", "codigo"))

    # --- <usuario> (nuevo, obligatorio) -------------------------------------
    nodo_usuario = raiz.find("usuario")
    usuario = ConfigUsuario(
        codigo=_obligatorio(nodo_usuario, "usuario", "codigo"),
        # La clave no se recorta aquí: Profit ya ignora los espacios de los lados.
        clave=_obligatorio(nodo_usuario, "usuario", "clave"),
        base=_nombre_sql(nodo_usuario, "usuario", "base", BASE_USUARIOS_POR_DEFECTO),
        tabla=_nombre_sql(nodo_usuario, "usuario", "tabla", TABLA_USUARIOS_POR_DEFECTO),
    )

    # --- <ambiente> (nuevo, opcional) ---------------------------------------
    nodo_ambiente = raiz.find("ambiente")
    ambiente = ConfigAmbiente(
        cod_emp=_texto(nodo_ambiente, "cod_emp") or sql.basedatos,
        base=_nombre_sql(nodo_ambiente, "ambiente", "base", ""),
    )

    # --- <seguridad> (nuevo, opcional) --------------------------------------
    seguridad = ConfigSeguridad(
        clave_salida=_texto(raiz.find("seguridad"), "clave_salida", CLAVE_SALIDA_POR_DEFECTO),
    )

    return Configuracion(sql=sql, rutas=rutas, pantalla=pantalla,
                         caja=caja, usuario=usuario, ambiente=ambiente,
                         seguridad=seguridad, archivo=ruta)
