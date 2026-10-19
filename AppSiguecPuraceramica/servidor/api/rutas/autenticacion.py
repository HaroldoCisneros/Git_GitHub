"""Entrar, revisar y cerrar la sesión del teléfono."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Response, status

from ..dependencias import ip_cliente, servicio_autenticacion, sesion_actual, token_actual
from ..esquemas import EntradaSesion, SesionNueva, SesionSalida, sesion_salida
from ..modelos import Sesion
from ..servicios.autenticacion import ServicioAutenticacion

router = APIRouter(prefix="/sesion", tags=["sesion"])


@router.post("", response_model=SesionNueva)
def entrar(datos: EntradaSesion,
           servicio: ServicioAutenticacion = Depends(servicio_autenticacion),
           ip: str = Depends(ip_cliente)) -> dict:
    """Usuario + clave -> token y la lista de empresas (ambientes) del usuario."""
    token, sesion = servicio.entrar(datos.usuario, datos.clave, datos.dispositivo, ip)
    return {"token": token, **sesion_salida(sesion)}


@router.get("", response_model=SesionSalida)
def sesion(sesion: Sesion = Depends(sesion_actual)) -> dict:
    """Al abrir la App: si el token guardado sigue vigente no se pide la clave."""
    return sesion_salida(sesion)


@router.delete("", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def salir(token: str = Depends(token_actual),
          servicio: ServicioAutenticacion = Depends(servicio_autenticacion),
          ip: str = Depends(ip_cliente)) -> Response:
    """Cierra la sesión: el token deja de servir aunque no haya vencido."""
    servicio.salir(token, ip)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
