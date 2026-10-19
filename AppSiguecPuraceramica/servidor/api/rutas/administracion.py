"""Administración de usuarios, permisos y bitácora (solo administradores)."""

from __future__ import annotations

from dataclasses import asdict
from datetime import date
from typing import Optional

from fastapi import APIRouter, Depends, Query, Response, status

from ..dependencias import ip_cliente, servicio_administracion, sesion_actual
from ..esquemas import (EntradaAmbiente, EntradaClave, EntradaUsuarioCambios, EntradaUsuarioNuevo,
                        ListaEmpresasSalida, ListaUsuariosSalida, PaginaBitacoraSalida, UsuarioAdminSalida)
from ..modelos import Sesion
from ..servicios.administracion import ServicioAdministracion

router = APIRouter(prefix="/admin", tags=["administracion"])


@router.get("/usuarios", response_model=ListaUsuariosSalida)
def usuarios(sesion: Sesion = Depends(sesion_actual),
             servicio: ServicioAdministracion = Depends(servicio_administracion)) -> dict:
    return {"usuarios": [asdict(u) for u in servicio.usuarios(sesion)]}


@router.post("/usuarios", response_model=UsuarioAdminSalida)
def crear(datos: EntradaUsuarioNuevo,
          sesion: Sesion = Depends(sesion_actual),
          servicio: ServicioAdministracion = Depends(servicio_administracion),
          ip: str = Depends(ip_cliente)) -> dict:
    return asdict(servicio.crear(sesion, datos.usuario, datos.nombre, datos.clave, datos.usuario_profit,
                                 datos.es_administrador, ip))


@router.get("/usuarios/{usuario_id:int}", response_model=UsuarioAdminSalida)
def usuario(usuario_id: int,
            sesion: Sesion = Depends(sesion_actual),
            servicio: ServicioAdministracion = Depends(servicio_administracion)) -> dict:
    return asdict(servicio.usuario(sesion, usuario_id))


@router.patch("/usuarios/{usuario_id:int}", response_model=UsuarioAdminSalida)
def actualizar(usuario_id: int, datos: EntradaUsuarioCambios,
               sesion: Sesion = Depends(sesion_actual),
               servicio: ServicioAdministracion = Depends(servicio_administracion),
               ip: str = Depends(ip_cliente)) -> dict:
    return asdict(servicio.actualizar(sesion, usuario_id, datos.nombre, datos.usuario_profit,
                                      datos.es_administrador, datos.activo, ip))


@router.post("/usuarios/{usuario_id:int}/clave", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def clave(usuario_id: int, datos: EntradaClave,
          sesion: Sesion = Depends(sesion_actual),
          servicio: ServicioAdministracion = Depends(servicio_administracion),
          ip: str = Depends(ip_cliente)) -> Response:
    servicio.cambiar_clave(sesion, usuario_id, datos.clave, ip)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/usuarios/{usuario_id:int}/desbloquear", response_model=UsuarioAdminSalida)
def desbloquear(usuario_id: int,
                sesion: Sesion = Depends(sesion_actual),
                servicio: ServicioAdministracion = Depends(servicio_administracion),
                ip: str = Depends(ip_cliente)) -> dict:
    return asdict(servicio.desbloquear(sesion, usuario_id, ip))


@router.put("/usuarios/{usuario_id:int}/ambientes/{co_empresa}", response_model=UsuarioAdminSalida)
def guardar_ambiente(usuario_id: int, co_empresa: str, datos: EntradaAmbiente,
                     sesion: Sesion = Depends(sesion_actual),
                     servicio: ServicioAdministracion = Depends(servicio_administracion),
                     ip: str = Depends(ip_cliente)) -> dict:
    """Da (o cambia) el acceso a una empresa con su almacén y permisos."""
    return asdict(servicio.guardar_ambiente(sesion, usuario_id, co_empresa, datos.almacen, datos.permisos, ip))


@router.delete("/usuarios/{usuario_id:int}/ambientes/{co_empresa}", response_model=UsuarioAdminSalida)
def quitar_ambiente(usuario_id: int, co_empresa: str,
                    sesion: Sesion = Depends(sesion_actual),
                    servicio: ServicioAdministracion = Depends(servicio_administracion),
                    ip: str = Depends(ip_cliente)) -> dict:
    return asdict(servicio.quitar_ambiente(sesion, usuario_id, co_empresa, ip))


@router.get("/empresas", response_model=ListaEmpresasSalida)
def empresas(sesion: Sesion = Depends(sesion_actual),
             servicio: ServicioAdministracion = Depends(servicio_administracion)) -> dict:
    return {"empresas": [asdict(e) for e in servicio.empresas(sesion)]}


@router.get("/bitacora", response_model=PaginaBitacoraSalida)
def bitacora(usuario: str = Query("", max_length=30),
             co_empresa: str = Query("", max_length=30),
             accion: str = Query("", max_length=30),
             desde: Optional[date] = None,
             hasta: Optional[date] = None,
             pagina: int = Query(1, ge=1),
             tamano: int = Query(50, ge=1, le=100),
             sesion: Sesion = Depends(sesion_actual),
             servicio: ServicioAdministracion = Depends(servicio_administracion)) -> dict:
    """Lo más reciente primero. Filtros vacíos = todos."""
    return asdict(servicio.bitacora(sesion, usuario, co_empresa, accion, desde, hasta, pagina, tamano))
