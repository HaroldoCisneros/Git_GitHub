"""
Validación de claves de Profit Plus (tabla employee de MasterProfit).

Es la MISMA lógica que usa el Sistema Web (cuentas/encriptacion.py),
traducida de Visual FoxPro, para que el usuario y la clave del config.xml
se validen exactamente igual que en Profit.

1) QUÉ SE ENCRIPTA
   Profit no encripta solo la clave, sino este texto:

       UPPER(ALLTRIM(clave)) + STR(EMPLOYEE.PRIORIDAD, 3) + EMPLOYEE.MAPA

   - UPPER(ALLTRIM(clave)): la clave sin espacios a los lados y en MAYÚSCULAS
     (por eso en Profit la clave no distingue mayúsculas de minúsculas).
   - STR(prioridad, 3): la prioridad como texto de 3 posiciones alineado a la
     derecha. Ejemplo: 5 -> "  5", 10 -> " 10".
   - mapa: el campo employee.mapa, char(4), CON sus espacios de relleno.

   Ejemplo: clave "abc", prioridad 5, mapa "AB  "  ->  "ABC  5AB  "

2) CÓMO SE ENCRIPTA (función FUN de FoxPro)
   Cada carácter se reemplaza por CHR( ((ASC(carácter) + 17) * 11) módulo 255 ).
   ASC() de FoxPro usa la página de códigos de Windows (Windows-1252), por eso
   en Python se usa "cp1252".

3) CÓMO SE GUARDA
   employee.password es char(15): se guardan los primeros 15 caracteres del
   resultado (si sobra se corta, si falta se rellena con espacios).
"""

from __future__ import annotations

import hmac   # compare_digest: compara sin revelar por el tiempo cuántos caracteres coinciden.

LARGO_CAMPO = 15     # employee.password es char(15)
LARGO_MAPA = 4       # employee.mapa es char(4)


def encriptar(texto: str) -> bytes:
    """
    Aplica la fórmula de Profit a un texto y devuelve los bytes resultantes.
    Ejemplo: "A" (código 65) -> ((65 + 17) * 11) % 255 = 137 -> b'\\x89'
    """
    return bytes(((c + 17) * 11) % 255 for c in texto.encode("cp1252"))


def str_foxpro(numero, ancho: int = 3) -> str:
    """
    Equivalente a STR(numero, ancho) de FoxPro: el número entero alineado a
    la derecha con espacios. Si no cabe, FoxPro devuelve asteriscos ("***").
    """
    texto = str(int(round(float(numero or 0))))
    return texto.rjust(ancho) if len(texto) <= ancho else "*" * ancho


def texto_a_encriptar(clave: str, prioridad, mapa: str) -> str:
    """Arma el texto que Profit encripta: UPPER(ALLTRIM(clave)) + STR(prioridad, 3) + mapa."""
    clave = clave.strip(" ").upper()
    # El mapa conserva su relleno de espacios hasta 4 (char(4) en SQL).
    mapa = (mapa or "").ljust(LARGO_MAPA)[:LARGO_MAPA]
    return clave + str_foxpro(prioridad, 3) + mapa


def clave_coincide(clave: str, guardada: bytes | None, prioridad, mapa: str) -> bool:
    """
    Compara una clave con la guardada en employee.password.

    :param clave: la clave escrita en el config.xml.
    :param guardada: bytes crudos del campo password (se leen con
        CAST(password AS VARBINARY(15)) para que SQL no altere los caracteres).
    :param prioridad: employee.prioridad del usuario.
    :param mapa: employee.mapa del usuario (sin quitarle los espacios).
    """
    if not clave or not clave.strip() or guardada is None:
        return False
    try:
        calculada = encriptar(texto_a_encriptar(clave, prioridad, mapa))[:LARGO_CAMPO]
    except UnicodeEncodeError:
        # Carácter que no existe en Windows-1252: Profit no pudo haberlo guardado.
        return False
    # Ambos lados se rellenan con espacios hasta 15 (como el char(15) de SQL).
    calculada = calculada.ljust(LARGO_CAMPO, b" ")
    guardada = bytes(guardada)[:LARGO_CAMPO].ljust(LARGO_CAMPO, b" ")
    return hmac.compare_digest(calculada, guardada)
