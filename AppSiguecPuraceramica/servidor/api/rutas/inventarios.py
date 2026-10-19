"""Inventarios por almacén: almacenes, preparar, consultar, cerrar y comparar."""

from __future__ import annotations

from dataclasses import asdict

from fastapi import APIRouter, Depends, Query

from ..dependencias import ip_cliente, servicio_inventarios, sesion_actual
from ..esquemas import (ComparacionSalida, DetalleInventarioSalida, EntradaInventario, InventarioSalida,
                        ListaAlmacenesSalida, ListaInventariosSalida)
from ..modelos import Sesion
from ..servicios.inventarios import ServicioInventarios

router = APIRouter(prefix="/empresas/{co_empresa}", tags=["inventarios"])


@router.get("/almacenes", response_model=ListaAlmacenesSalida)
def almacenes(co_empresa: str,
              sesion: Sesion = Depends(sesion_actual),
              servicio: ServicioInventarios = Depends(servicio_inventarios)) -> dict:
    """Almacenes de Profit y, si lo tienen, su inventario abierto."""
    return {"almacenes": [asdict(a) for a in servicio.almacenes(sesion, co_empresa)]}


@router.get("/inventarios", response_model=ListaInventariosSalida)
def lista(co_empresa: str, solo_abiertos: bool = False,
          sesion: Sesion = Depends(sesion_actual),
          servicio: ServicioInventarios = Depends(servicio_inventarios)) -> dict:
    return {"inventarios": [asdict(i) for i in servicio.lista(sesion, co_empresa, solo_abiertos)]}


@router.post("/inventarios", response_model=InventarioSalida)
def preparar(co_empresa: str, datos: EntradaInventario,
             sesion: Sesion = Depends(sesion_actual),
             servicio: ServicioInventarios = Depends(servicio_inventarios),
             ip: str = Depends(ip_cliente)) -> dict:
    """Prepara (abre) el inventario del almacén con la foto de su stock en Profit."""
    return asdict(servicio.preparar(sesion, co_empresa, datos.co_alma, datos.descripcion, ip))


@router.get("/inventarios/{inventario_id:int}", response_model=DetalleInventarioSalida)
def detalle(co_empresa: str, inventario_id: int,
            sesion: Sesion = Depends(sesion_actual),
            servicio: ServicioInventarios = Depends(servicio_inventarios)) -> dict:
    return asdict(servicio.detalle(sesion, co_empresa, inventario_id))


@router.post("/inventarios/{inventario_id:int}/cerrar", response_model=InventarioSalida)
def cerrar(co_empresa: str, inventario_id: int,
           sesion: Sesion = Depends(sesion_actual),
           servicio: ServicioInventarios = Depends(servicio_inventarios),
           ip: str = Depends(ip_cliente)) -> dict:
    return asdict(servicio.cerrar(sesion, co_empresa, inventario_id, ip))


@router.get("/inventarios/{inventario_id:int}/comparacion", response_model=ComparacionSalida)
def comparacion(co_empresa: str, inventario_id: int,
                solo_diferencias: bool = True,
                incluir_no_contados: bool = True,
                pagina: int = Query(1, ge=1),
                tamano: int = Query(50, ge=1, le=100),
                sesion: Sesion = Depends(sesion_actual),
                servicio: ServicioInventarios = Depends(servicio_inventarios)) -> dict:
    """Stock contado vs. la foto del stock de Profit (permiso preparar_inventario)."""
    return asdict(servicio.comparacion(sesion, co_empresa, inventario_id, solo_diferencias,
                                       incluir_no_contados, pagina, tamano))
