"""
Piezas que FastAPI le entrega a cada ruta ("dependencias"):

    servicio_*       Los servicios creados al arrancar (aplicacion.py).
    token_actual     El token del encabezado "Authorization: Bearer <token>".
    sesion_actual    La Sesion del token (si no sirve -> 401 y el teléfono pide la clave).
    ip_cliente       IP del teléfono (para la bitácora).
"""

from __future__ import annotations

from typing import Optional

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .errores import ErrorSesion
from .modelos import Sesion
from .servicios.administracion import ServicioAdministracion
from .servicios.articulos import ServicioArticulos
from .servicios.autenticacion import ServicioAutenticacion
from .servicios.conteos import ServicioConteos
from .servicios.inventarios import ServicioInventarios
from .servicios.sincronizacion import ServicioSincronizacion

# auto_error=False: si falta el encabezado lo manejamos nosotros (mensaje en español).
_esquema_bearer = HTTPBearer(auto_error=False)


def servicio_autenticacion(request: Request) -> ServicioAutenticacion:
    return request.app.state.autenticacion


def servicio_articulos(request: Request) -> ServicioArticulos:
    return request.app.state.articulos


def servicio_sincronizacion(request: Request) -> ServicioSincronizacion:
    return request.app.state.sincronizacion


def servicio_inventarios(request: Request) -> ServicioInventarios:
    return request.app.state.inventarios


def servicio_conteos(request: Request) -> ServicioConteos:
    return request.app.state.conteos


def servicio_administracion(request: Request) -> ServicioAdministracion:
    return request.app.state.administracion


def ip_cliente(request: Request) -> str:
    return request.client.host if request.client else ""


def token_actual(credenciales: Optional[HTTPAuthorizationCredentials] = Depends(_esquema_bearer)) -> str:
    if credenciales is None or not credenciales.credentials:
        raise ErrorSesion("Falta el token de la sesión. Entre con su usuario y clave.")
    return credenciales.credentials


def sesion_actual(token: str = Depends(token_actual),
                  servicio: ServicioAutenticacion = Depends(servicio_autenticacion)) -> Sesion:
    return servicio.sesion_de_token(token)
