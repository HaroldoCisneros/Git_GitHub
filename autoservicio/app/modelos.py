"""
Clases de datos (modelos) de la aplicación.

Estas clases representan la información con la que trabaja el autoservicio:
el usuario que inició sesión, la caja, el cliente, los artículos y la factura
que se está armando. No saben nada de SQL ni de pantallas: solo guardan datos
y hacen cálculos (por ejemplo, el total de la factura).

Todos los montos se manejan con Decimal para no perder céntimos por los
redondeos de los números flotantes.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from decimal import ROUND_HALF_UP, Decimal
from typing import Any, Dict, List, Optional

# Los montos de la factura se guardan con 2 decimales (reng_neto, tot_neto...).
CENTIMOS = Decimal("0.01")
CIEN = Decimal("100")


def redondear(valor: Decimal) -> Decimal:
    """Redondeo comercial a 2 decimales (0,005 -> 0,01)."""
    return valor.quantize(CENTIMOS, rounding=ROUND_HALF_UP)


@dataclass
class Usuario:
    """Usuario que inició sesión (validado contra la tabla de usuarios de la aplicación web)."""
    codigo: str
    nombre: str


class Ambiente:
    """
    Ambiente del usuario: la fila completa de la tabla PPV_AMBIENTE
    (llave COD_EMP + COD_USU) con TODOS sus parámetros.

    Cualquier columna se puede leer de tres formas, siempre por su nombre en
    la tabla (sin importar mayúsculas o minúsculas):

        ambiente["VD_ALMACEN"]        # como diccionario
        ambiente.VD_ALMACEN           # como atributo
        ambiente.get("VD_ALMACEN")    # devuelve None (o un defecto) si no existe

    Los campos de texto (CHAR / TEXT) vienen sin los espacios de relleno de
    SQL Server; los BIT llegan como True/False; los NUMERIC como Decimal.

    Para los parámetros más usados hay además propiedades con nombre en
    español (almacen, vendedor, ...). Ver la tabla de abajo.
    """

    # Propiedades con nombre en español -> columna de PPV_AMBIENTE.
    # PENDIENTE DE CONFIRMAR: el significado se dedujo del nombre de la columna.
    ALIAS = {
        "empresa": "COD_EMP",
        "usuario": "COD_USU",
        "condicion_pago": "VD_CONDICION",
        "cliente": "VD_CLIENTE",
        "vendedor": "VD_VENDEDOR",
        "transporte": "VD_TRANSPORTE",
        "moneda": "VD_MONEDA",
        "almacen": "VD_ALMACEN",
        "banco": "VD_BANCO",
        "punto_venta": "VD_PUNTO",
        "caja": "VD_CAJA",
        "lista_precios": "VD_LISTPREC",
        "impresora": "VD_IMPRESORA",
        "puerto_impresora": "VD_PUERTO",
    }

    def __init__(self, valores: Dict[str, Any]):
        """
        :param valores: la fila de PPV_AMBIENTE como diccionario {columna: valor}.
        """
        # Se guarda con los nombres en mayúsculas para buscar sin importar cómo se escriban.
        object.__setattr__(self, "_valores", {
            str(columna).upper(): (valor.strip() if isinstance(valor, str) else valor)
            for columna, valor in valores.items()
        })

    # --- Acceso por nombre de columna ------------------------------------

    def __getitem__(self, columna: str) -> Any:
        try:
            return self._valores[columna.upper()]
        except KeyError:
            raise KeyError(f"PPV_AMBIENTE no tiene la columna '{columna}'") from None

    def __getattr__(self, nombre: str) -> Any:
        # Solo se llama si el atributo no existe en la clase: se busca como columna.
        if nombre.startswith("_"):
            raise AttributeError(nombre)
        if nombre in Ambiente.ALIAS:
            return self._valores.get(Ambiente.ALIAS[nombre])
        try:
            return self._valores[nombre.upper()]
        except KeyError:
            raise AttributeError(f"PPV_AMBIENTE no tiene la columna '{nombre}'") from None

    def __setattr__(self, nombre: str, valor: Any) -> None:
        # El ambiente es de solo lectura: se carga una vez al arrancar.
        raise AttributeError("El ambiente es de solo lectura")

    def __contains__(self, columna: str) -> bool:
        return columna.upper() in self._valores

    def get(self, columna: str, defecto: Any = None) -> Any:
        """Valor de la columna, o ``defecto`` si la columna no existe."""
        return self._valores.get(columna.upper(), defecto)

    def como_diccionario(self) -> Dict[str, Any]:
        """Copia de todos los parámetros {COLUMNA: valor}."""
        return dict(self._valores)

    def __repr__(self) -> str:
        return f"Ambiente(empresa={self.empresa!r}, usuario={self.usuario!r}, columnas={len(self._valores)})"


@dataclass
class Caja:
    """Caja de Profit en la que está instalada la aplicación (tabla "cajas")."""
    codigo: str
    descripcion: str


@dataclass
class Sesion:
    """
    Datos validados al arrancar, que se usan en toda la aplicación:
    la caja, el usuario del config.xml y su ambiente de PPV_AMBIENTE.
    """
    caja: "Caja"
    usuario: Usuario
    ambiente: Ambiente


@dataclass
class Cliente:
    """Cliente de Profit (tabla "clientes") al que se le hace la factura."""
    codigo: str      # co_cli
    nombre: str      # cli_des
    rif: str         # rif / cédula


@dataclass
class Articulo:
    """
    Artículo encontrado al escanear, con su precio para la cantidad del
    renglón (lo calcula el procedimiento ppv_buscarart).
    """
    codigo: str                  # co_art
    descripcion: str             # art_des
    precio: Decimal              # Precio unitario de venta SIN impuesto
    tipo_impuesto: str = ""      # tipo_imp de Profit
    porcentaje_impuesto: Decimal = Decimal("0")   # % de IVA ("factor" de ppv_buscarart)
    # Fila completa que devolvió ppv_buscarart (uni_venta, costos, stock...),
    # necesaria para grabar el renglón de la factura.
    datos: Dict[str, Any] = field(default_factory=dict, repr=False, compare=False)

    @property
    def precio_con_impuesto(self) -> Decimal:
        """Precio unitario con IVA (el que ve el cliente en pantalla)."""
        return redondear(self.precio * (1 + self.porcentaje_impuesto / CIEN))


@dataclass
class LineaFactura:
    """Un renglón de la factura: un artículo y la cantidad escaneada."""
    articulo: Articulo
    cantidad: Decimal = Decimal("1")

    @property
    def subtotal(self) -> Decimal:
        """Precio x cantidad, sin impuesto, a 2 decimales (reng_neto de Profit)."""
        return redondear(self.articulo.precio * self.cantidad)

    @property
    def impuesto(self) -> Decimal:
        """Monto del IVA del renglón, a 2 decimales."""
        return redondear(self.subtotal * self.articulo.porcentaje_impuesto / CIEN)

    @property
    def total(self) -> Decimal:
        """Subtotal + impuesto."""
        return self.subtotal + self.impuesto


@dataclass
class Factura:
    """
    Factura que se está armando en pantalla.

    Vive solo en memoria hasta que el cliente la finaliza; recién ahí se
    grabará en Profit (esa parte depende de cómo se defina el grabado).
    """
    caja: Caja
    usuario: Usuario
    cliente: Optional[Cliente] = None
    lineas: List[LineaFactura] = field(default_factory=list)
    fecha: datetime = field(default_factory=datetime.now)

    # ------------------------------------------------------------------
    # Operaciones sobre los renglones
    # ------------------------------------------------------------------

    def agregar_articulo(self, articulo: Articulo, cantidad: Decimal = Decimal("1")) -> LineaFactura:
        """
        Agrega un artículo escaneado.

        Si el artículo ya está en la factura se suma la cantidad al mismo
        renglón (así escanear 3 veces el mismo producto muestra "3" y no
        tres renglones). Devuelve el renglón afectado.
        """
        for linea in self.lineas:
            if linea.articulo.codigo == articulo.codigo:
                linea.cantidad += cantidad
                return linea
        linea = LineaFactura(articulo=articulo, cantidad=cantidad)
        self.lineas.append(linea)
        return linea

    def cambiar_cantidad(self, indice: int, diferencia: Decimal) -> None:
        """
        Suma (o resta, si es negativa) ``diferencia`` a la cantidad del renglón.

        La cantidad nunca baja de 1: para sacar el artículo se usa quitar_linea().
        """
        if 0 <= indice < len(self.lineas):
            linea = self.lineas[indice]
            linea.cantidad = max(Decimal("1"), linea.cantidad + diferencia)

    def quitar_linea(self, indice: int) -> None:
        """Elimina el renglón de la posición indicada (si existe)."""
        if 0 <= indice < len(self.lineas):
            del self.lineas[indice]

    # ------------------------------------------------------------------
    # Totales
    # ------------------------------------------------------------------

    @property
    def subtotal(self) -> Decimal:
        """Suma de los subtotales (sin impuesto)."""
        return sum((l.subtotal for l in self.lineas), Decimal("0"))

    @property
    def impuesto(self) -> Decimal:
        """Suma de los impuestos."""
        return sum((l.impuesto for l in self.lineas), Decimal("0"))

    @property
    def total(self) -> Decimal:
        """Total a pagar."""
        return self.subtotal + self.impuesto

    @property
    def cantidad_articulos(self) -> Decimal:
        """Cantidad total de unidades en la factura."""
        return sum((l.cantidad for l in self.lineas), Decimal("0"))

    @property
    def vacia(self) -> bool:
        """True si todavía no se ha escaneado nada."""
        return not self.lineas
