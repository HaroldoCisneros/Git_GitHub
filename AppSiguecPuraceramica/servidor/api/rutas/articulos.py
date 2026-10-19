"""
Artículos para la toma de inventario: consulta (código, descripción y stock
contado) y la acción de sincronizar con Profit 2K12.

Las rutas fijas (por-codigo, sincronizar, sincronizacion) van ANTES de cualquier
ruta con parámetro para que no se confundan con un código de artículo.
"""

from __future__ import annotations

from dataclasses import asdict
from typing import Optional

from fastapi import APIRouter, Depends, Query

from ..dependencias import ip_cliente, servicio_articulos, servicio_sincronizacion, sesion_actual
from ..esquemas import (ArticuloSalida, PaginaArticulosSalida, SincronizacionSalida,
                        UltimaSincronizacionSalida)
from ..modelos import Sesion
from ..servicios.articulos import ServicioArticulos
from ..servicios.sincronizacion import ServicioSincronizacion

router = APIRouter(prefix="/empresas/{co_empresa}/articulos", tags=["articulos"])


@router.get("", response_model=PaginaArticulosSalida)
def buscar(co_empresa: str,
           texto: str = Query("", max_length=60),
           inventario_id: Optional[int] = Query(None, ge=1),
           solo_contados: bool = False,
           pagina: int = Query(1, ge=1),
           tamano: int = Query(30, ge=1, le=100),
           sesion: Sesion = Depends(sesion_actual),
           servicio: ServicioArticulos = Depends(servicio_articulos)) -> dict:
    """Código, descripción y stock contado en el inventario elegido (si lo hay)."""
    return asdict(servicio.buscar(sesion, co_empresa, texto, inventario_id, solo_contados, pagina, tamano))


@router.get("/por-codigo", response_model=ArticuloSalida)
def por_codigo(co_empresa: str,
               codigo: str = Query(..., max_length=60),
               inventario_id: Optional[int] = Query(None, ge=1),
               sesion: Sesion = Depends(sesion_actual),
               servicio: ServicioArticulos = Depends(servicio_articulos)) -> dict:
    """El artículo de un código escaneado (404 si no está en la copia)."""
    return asdict(servicio.por_codigo(sesion, co_empresa, codigo, inventario_id))


@router.post("/sincronizar", response_model=SincronizacionSalida)
def sincronizar(co_empresa: str,
                sesion: Sesion = Depends(sesion_actual),
                servicio: ServicioSincronizacion = Depends(servicio_sincronizacion),
                ip: str = Depends(ip_cliente)) -> dict:
    """Baja los artículos de Profit (saArticulo) a la copia de la App."""
    return asdict(servicio.sincronizar(sesion, co_empresa, ip))


@router.get("/sincronizacion", response_model=UltimaSincronizacionSalida)
def ultima_sincronizacion(co_empresa: str,
                          sesion: Sesion = Depends(sesion_actual),
                          servicio: ServicioSincronizacion = Depends(servicio_sincronizacion)) -> dict:
    ultima = servicio.ultima(sesion, co_empresa)
    return {"sincronizacion": asdict(ultima) if ultima else None}
