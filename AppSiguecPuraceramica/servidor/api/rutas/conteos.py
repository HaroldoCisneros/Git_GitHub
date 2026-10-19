"""Conteos individuales dentro de un inventario abierto (escaneando el código de barras)."""

from __future__ import annotations

from dataclasses import asdict

from fastapi import APIRouter, Depends, Query, Response, status

from ..dependencias import ip_cliente, servicio_conteos, sesion_actual
from ..esquemas import EntradaConteo, ListaConteosSalida, ResultadoConteoSalida
from ..modelos import Sesion
from ..servicios.conteos import ServicioConteos

router = APIRouter(prefix="/empresas/{co_empresa}/inventarios/{inventario_id:int}/conteos", tags=["conteos"])


@router.get("", response_model=ListaConteosSalida)
def ultimos(co_empresa: str, inventario_id: int,
            de_todos: bool = False,
            cuantos: int = Query(50, ge=1, le=200),
            sesion: Sesion = Depends(sesion_actual),
            servicio: ServicioConteos = Depends(servicio_conteos)) -> dict:
    """Los últimos conteos propios (de_todos=true: de todos, solo quien prepara)."""
    return {"conteos": [asdict(c) for c in servicio.ultimos(sesion, co_empresa, inventario_id, de_todos, cuantos)]}


@router.post("", response_model=ResultadoConteoSalida)
def anotar(co_empresa: str, inventario_id: int, datos: EntradaConteo,
           sesion: Sesion = Depends(sesion_actual),
           servicio: ServicioConteos = Depends(servicio_conteos)) -> dict:
    """Código escaneado + cantidad: un conteo a nombre del usuario."""
    return asdict(servicio.anotar(sesion, co_empresa, inventario_id, datos.codigo, datos.cantidad))


@router.delete("/{conteo_id:int}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def borrar(co_empresa: str, inventario_id: int, conteo_id: int,
           sesion: Sesion = Depends(sesion_actual),
           servicio: ServicioConteos = Depends(servicio_conteos),
           ip: str = Depends(ip_cliente)) -> Response:
    servicio.borrar(sesion, co_empresa, inventario_id, conteo_id, ip)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
