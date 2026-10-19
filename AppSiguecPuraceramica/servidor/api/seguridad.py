"""
Seguridad de la App: claves de los usuarios y tokens de sesión.

Claves
    Nunca se guardan en texto. Se guarda un hash PBKDF2-SHA256 con sal aleatoria
    (el mismo método de Control de Visitas), en un solo texto:

        pbkdf2_sha256$<repeticiones>$<sal en base64>$<hash en base64>

    Así se pueden subir las repeticiones en el futuro sin invalidar las claves viejas.

Tokens
    Al entrar, el teléfono recibe un token aleatorio (como una llave temporal) y
    lo manda en cada petición (encabezado "Authorization: Bearer <token>").
    En la base solo se guarda su SHA-256: si alguien leyera la tabla sesiones no
    podría usar los tokens.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import secrets

ALGORITMO = "pbkdf2_sha256"
REPETICIONES = 210_000        # Recomendación actual de OWASP para PBKDF2-SHA256.
BYTES_SAL = 16
BYTES_TOKEN = 32              # 256 bits: imposible de adivinar.
LARGO_MINIMO_CLAVE = 6


def _b64(datos: bytes) -> str:
    return base64.b64encode(datos).decode("ascii")


def cifrar_clave(clave: str, repeticiones: int = REPETICIONES) -> str:
    """Hash de la clave para guardar en usuarios.clave_hash."""
    sal = secrets.token_bytes(BYTES_SAL)
    resumen = hashlib.pbkdf2_hmac("sha256", clave.encode("utf-8"), sal, repeticiones)
    return f"{ALGORITMO}${repeticiones}${_b64(sal)}${_b64(resumen)}"


def clave_coincide(clave: str, guardado: str) -> bool:
    """True si la clave escrita corresponde al hash guardado."""
    try:
        algoritmo, repeticiones, sal, resumen = (guardado or "").split("$")
        if algoritmo != ALGORITMO:
            return False
        calculado = hashlib.pbkdf2_hmac("sha256", clave.encode("utf-8"),
                                        base64.b64decode(sal), int(repeticiones))
    except (ValueError, TypeError):
        return False
    # compare_digest tarda lo mismo acierte o no (no da pistas por el tiempo).
    return hmac.compare_digest(calculado, base64.b64decode(resumen))


def nuevo_token() -> str:
    """Token aleatorio para el teléfono (se entrega UNA sola vez, al entrar)."""
    return secrets.token_urlsafe(BYTES_TOKEN)


def resumen_token(token: str) -> str:
    """SHA-256 del token: es lo que se guarda en sesiones.token_hash."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def validar_clave_nueva(clave: str) -> None:
    """Reglas mínimas de una clave nueva (ValueError con el motivo)."""
    if len(clave or "") < LARGO_MINIMO_CLAVE:
        raise ValueError(f"La clave debe tener al menos {LARGO_MINIMO_CLAVE} caracteres.")
