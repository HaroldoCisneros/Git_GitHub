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
from decimal import Decimal
from typing import List, Optional


@dataclass
class Usuario:
    """Usuario que inició sesión (validado contra la tabla de usuarios de la aplicación web)."""
    codigo: str
    nombre: str


@dataclass
class Caja:
    """Caja de Profit en la que está instalada la aplicación (tabla "cajas")."""
    codigo: str
    descripcion: str


@dataclass
class Cliente:
    """Cliente de Profit (tabla "clientes") al que se le hace la factura."""
    codigo: str      # co_cli
    nombre: str      # cli_des
    rif: str         # rif / cédula


@dataclass
class Articulo:
    """Artículo de Profit (tabla "art") encontrado al escanear un código de barras."""
    codigo: str                  # co_art
    descripcion: str             # art_des
    precio: Decimal              # Precio de venta (sin impuesto)
    tipo_impuesto: str = ""      # tipo_imp de Profit
    porcentaje_impuesto: Decimal = Decimal("0")   # % de IVA del artículo


@dataclass
class LineaFactura:
    """Un renglón de la factura: un artículo y la cantidad escaneada."""
    articulo: Articulo
    cantidad: Decimal = Decimal("1")

    @property
    def subtotal(self) -> Decimal:
        """Precio x cantidad, sin impuesto."""
        return self.articulo.precio * self.cantidad

    @property
    def impuesto(self) -> Decimal:
        """Monto del impuesto del renglón."""
        return self.subtotal * self.articulo.porcentaje_impuesto / Decimal("100")

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
