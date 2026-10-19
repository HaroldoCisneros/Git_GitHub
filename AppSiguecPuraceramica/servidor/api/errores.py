"""
Errores de la App. Cada uno corresponde a un código HTTP: la ruta no tiene que
saberlo, el manejador de aplicacion.py hace la traducción y el teléfono recibe

    {"detalle": "mensaje para el usuario"}
"""

from __future__ import annotations


class ErrorApp(Exception):
    """Error con un mensaje que se le puede mostrar al usuario."""
    codigo_http = 400

    def __init__(self, mensaje: str):
        super().__init__(mensaje)
        self.mensaje = mensaje


class ErrorAutenticacion(ErrorApp):
    """Usuario o clave incorrectos, usuario bloqueado o inactivo."""
    codigo_http = 401


class ErrorSesion(ErrorApp):
    """El token no existe, venció o se cerró: el teléfono debe pedir la clave otra vez."""
    codigo_http = 401


class ErrorPermiso(ErrorApp):
    """El usuario no tiene ambiente en esa empresa o no tiene el permiso."""
    codigo_http = 403


class ErrorNoEncontrado(ErrorApp):
    codigo_http = 404


class ErrorConflicto(ErrorApp):
    """Otro usuario cambió el registro mientras éste lo tenía abierto."""
    codigo_http = 409


class ErrorValidacion(ErrorApp):
    """Un dato enviado no es válido (largo, número, campo que no existe...)."""
    codigo_http = 422
