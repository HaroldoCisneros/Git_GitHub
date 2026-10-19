"""
Conversión de valores entre SQL Server y el teléfono.

Hacia el teléfono todo viaja como TEXTO (así una cantidad 12,50000 no se
convierte en 12.499999 por el camino):
    número   -> "12.5"  (punto decimal, sin ceros de más)
    fecha    -> "2026-10-17" o "2026-10-17 14:30:00"
    lógico   -> "true" / "false"
    texto    -> sin los espacios de relleno de los campos char de Profit
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any, Optional

CERO = Decimal("0")


def a_texto(valor: Any) -> Optional[str]:
    """Valor leído de SQL Server -> texto para el teléfono (None se queda None)."""
    if valor is None:
        return None
    if isinstance(valor, bool):
        return "true" if valor else "false"
    if isinstance(valor, Decimal):
        return texto_decimal(valor)
    if isinstance(valor, float):
        return texto_decimal(Decimal(repr(valor)))
    if isinstance(valor, int):
        return str(valor)
    if isinstance(valor, datetime):
        if valor.time() == datetime.min.time():
            return valor.date().isoformat()
        return valor.replace(microsecond=0).isoformat(sep=" ")
    if isinstance(valor, date):
        return valor.isoformat()
    if isinstance(valor, (bytes, bytearray)):
        return None
    return str(valor).rstrip()


def limpio(valor: Any) -> str:
    """Texto sin espacios a los lados ("" si es None)."""
    return (a_texto(valor) or "").strip()


def a_decimal(valor: Any) -> Decimal:
    """Valor de SQL Server -> Decimal (None o basura -> 0)."""
    if valor is None:
        return CERO
    try:
        return Decimal(str(valor))
    except (ArithmeticError, ValueError):
        return CERO


def texto_decimal(valor: Decimal) -> str:
    """Decimal -> "1234.5" (punto decimal, sin ceros de más; 0 -> "0")."""
    valor = Decimal(valor)
    return "0" if valor == 0 else format(valor.normalize(), "f")


def logico(valor: Any) -> bool:
    """bit / char de Profit -> bool ("1", "S", "T", "TRUE" son verdaderos)."""
    if isinstance(valor, str):
        return valor.strip().upper() in ("1", "S", "T", "TRUE", "SI", "SÍ")
    return bool(valor)


def leer_cantidad(texto: str) -> Decimal:
    """
    Cantidad escrita en el teléfono ("12", "2,5", "-1") -> Decimal.
    :raises ValueError: si no es un número
    """
    limpio_ = (texto or "").strip().replace(" ", "")
    if "," in limpio_ and "." not in limpio_:
        limpio_ = limpio_.replace(",", ".")
    try:
        numero = Decimal(limpio_)
    except InvalidOperation:
        raise ValueError("La cantidad debe ser un número.") from None
    if not numero.is_finite():
        raise ValueError("La cantidad debe ser un número.")
    return numero
