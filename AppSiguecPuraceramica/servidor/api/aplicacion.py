"""
Arma la aplicación FastAPI: servicios, rutas y manejo de errores.

    crear_aplicacion(config)        Producción: servicios sobre SQL Server.
    crear_aplicacion_con(...)       Pruebas: servicios ya creados (bases falsas).

Todos los errores salen como {"detalle": "mensaje"} con su código HTTP:
    401 sesión / clave · 403 permiso · 404 no existe · 409 conflicto (inventario
    cerrado, almacén con inventario abierto) · 422 dato no válido
    503 no hay comunicación con SQL Server
"""

from __future__ import annotations

import logging
from contextlib import contextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import __version__
from .base_datos import BaseDatos, ErrorBaseDatos
from .configuracion import Configuracion
from .errores import ErrorApp, ErrorSesion
from .repositorios import UnidadApp, UnidadProfit
from .rutas import administracion, articulos, autenticacion, conteos, inventarios, salud
from .servicios.administracion import ServicioAdministracion
from .servicios.articulos import ServicioArticulos
from .servicios.autenticacion import ServicioAutenticacion
from .servicios.conteos import ServicioConteos
from .servicios.inventarios import ServicioInventarios
from .servicios.sincronizacion import ServicioSincronizacion

log = logging.getLogger("siguec.aplicacion")

PREFIJO = "/api/v1"


def crear_aplicacion(config: Configuracion) -> FastAPI:
    bd = BaseDatos(config.sql)

    @contextmanager
    def abrir_app():
        """Base propia SIGUEC_PURACERAMICA -> UnidadApp."""
        with bd.conexion(config.aplicacion.base_usuarios) as cx:
            yield UnidadApp(cx)

    @contextmanager
    def abrir_profit(base: str):
        """Base de la empresa en Profit 2K12 -> UnidadProfit (solo lectura)."""
        with bd.conexion(base) as cx:
            yield UnidadProfit(cx, config.profit)

    return crear_aplicacion_con(
        ServicioAutenticacion(abrir_app, config.aplicacion),
        ServicioArticulos(abrir_app, config.aplicacion),
        ServicioSincronizacion(abrir_profit, abrir_app, config.profit),
        ServicioInventarios(abrir_profit, abrir_app, config.aplicacion),
        ServicioConteos(abrir_app),
        ServicioAdministracion(abrir_app),
    )


def crear_aplicacion_con(servicio_autenticacion: ServicioAutenticacion,
                         servicio_articulos: ServicioArticulos,
                         servicio_sincronizacion: ServicioSincronizacion,
                         servicio_inventarios: ServicioInventarios,
                         servicio_conteos: ServicioConteos,
                         servicio_administracion: ServicioAdministracion) -> FastAPI:
    app = FastAPI(title="AppSiguecPuraceramica API", version=__version__,
                  description="Toma de inventario desde el teléfono contra Profit Plus 2K12.")
    app.state.autenticacion = servicio_autenticacion
    app.state.articulos = servicio_articulos
    app.state.sincronizacion = servicio_sincronizacion
    app.state.inventarios = servicio_inventarios
    app.state.conteos = servicio_conteos
    app.state.administracion = servicio_administracion

    for modulo in (salud, autenticacion, articulos, inventarios, conteos, administracion):
        app.include_router(modulo.router, prefix=PREFIJO)

    # ---------- Errores -> {"detalle": ...} ----------

    @app.exception_handler(ErrorApp)
    async def _error_app(_: Request, error: ErrorApp):
        encabezados = {"WWW-Authenticate": "Bearer"} if isinstance(error, ErrorSesion) else None
        return JSONResponse(status_code=error.codigo_http, content={"detalle": error.mensaje},
                            headers=encabezados)

    @app.exception_handler(ErrorBaseDatos)
    async def _error_bd(_: Request, error: ErrorBaseDatos):
        # El detalle técnico queda en el log del servidor; al teléfono, un mensaje claro.
        log.error("Error de base de datos: %s", error.mensaje_servidor)
        return JSONResponse(status_code=503, content={
            "detalle": "No se pudo comunicar con la base de datos. Intente en un momento."})

    @app.exception_handler(RequestValidationError)
    async def _error_validacion(_: Request, error: RequestValidationError):
        partes = []
        for e in error.errors():
            lugar = ".".join(str(p) for p in e.get("loc", ()) if p not in ("body", "query", "path"))
            partes.append(f"{lugar}: {e.get('msg')}" if lugar else str(e.get("msg")))
        return JSONResponse(status_code=422, content={"detalle": "Datos no válidos. " + "; ".join(partes)})

    @app.exception_handler(Exception)
    async def _error_inesperado(_: Request, error: Exception):
        log.exception("Error inesperado", exc_info=error)
        return JSONResponse(status_code=500, content={
            "detalle": "Ocurrió un error en el servidor. Ya quedó anotado en el log."})

    return app
