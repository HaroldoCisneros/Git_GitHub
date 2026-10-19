"""
Sincronizar artículos: baja los artículos de Profit 2K12 (saArticulo) a la
COPIA de la App (SIGUEC_PURACERAMICA.dbo.articulos y articulo_codigos).

    sincronizar()   Lee todo saArticulo y deja la copia igual.   [sincronizar_articulos]
    ultima()        Cuándo fue la última sincronización y qué trajo.

Pasos:
    1. En Profit (solo lectura): se revisa qué columnas existen (INFORMATION_SCHEMA)
       y se leen co_art, art_des, anulado y las columnas que sirven para escanear
       (modelo, ref... según <columnas_codigo>); si hay tabla de códigos de barra,
       también se lee.
    2. preparar_copia() limpia lo leído (sin repetidos, largos máximos) y arma la
       lista de códigos para escanear con su prioridad (co_art primero).
    3. En la base de la App, en UNA transacción: nuevos, cambiados y los que ya no
       están en Profit (no se borran: pueden tener conteos; quedan como retirados).
"""

from __future__ import annotations

import logging
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from ..configuracion import ConfigProfit
from ..errores import ErrorValidacion
from ..modelos import ResultadoSincronizacion, Sesion
from ..repositorios import bitacora
from .comun import AbrirApp, AbrirProfit, ambiente_con_permiso
from .valores import a_texto, limpio, logico

log = logging.getLogger("siguec.sincronizacion")

LARGO_CODIGO_ARTICULO = 30
LARGO_DESCRIPCION = 150
LARGO_CODIGO_ESCANEO = 60
MENSAJE_SIN_PERMISO = "Su ambiente no permite sincronizar los artículos con Profit."

Articulo = Tuple[str, str, bool]          # (co_art, art_des, anulado)
Codigo = Tuple[str, str, str, int]        # (codigo, co_art, origen, prioridad)


def preparar_copia(filas: Iterable[dict], extras: Sequence[str],
                   barras: Iterable[dict] = ()) -> Tuple[List[Articulo], List[Codigo]]:
    """
    Lo leído de Profit -> (artículos, códigos) listos para grabar (no toca la base: se prueba solo).

    - Se saltan los artículos sin código y los repetidos (gana el primero).
    - Códigos: co_art (prioridad 0), cada columna extra (1, 2...) y la tabla de
      barras (la última). Si el mismo código aparece dos veces para el mismo
      artículo, queda el de prioridad más baja. Solo de artículos que existen.
    """
    articulos: Dict[str, Articulo] = {}
    codigos: Dict[Tuple[str, str], Codigo] = {}

    def agregar_codigo(codigo: str, co_art: str, origen: str, prioridad: int) -> None:
        codigo = (codigo or "").strip()
        if not codigo or len(codigo) > LARGO_CODIGO_ESCANEO:
            return
        clave = (codigo.upper(), co_art)
        actual = codigos.get(clave)
        if actual is None or prioridad < actual[3]:
            codigos[clave] = (codigo, co_art, origen, prioridad)

    for fila in filas:
        co_art = limpio(fila.get("co_art"))
        if not co_art or co_art in articulos or len(co_art) > LARGO_CODIGO_ARTICULO:
            continue
        art_des = limpio(fila.get("art_des"))[:LARGO_DESCRIPCION] or co_art
        articulos[co_art] = (co_art, art_des, logico(fila.get("anulado")))
        agregar_codigo(co_art, co_art, "co_art", 0)
        for posicion, columna in enumerate(extras, start=1):
            agregar_codigo(a_texto(fila.get("x_" + columna.lower())) or "", co_art, columna.lower(), posicion)

    prioridad_barras = len(extras) + 1
    for fila in barras:
        co_art = limpio(fila.get("co_art"))
        if co_art in articulos:
            agregar_codigo(a_texto(fila.get("codigo")) or "", co_art, "barra", prioridad_barras)

    return list(articulos.values()), list(codigos.values())


def _resultado(fila: dict, total: int = 0) -> ResultadoSincronizacion:
    return ResultadoSincronizacion(
        fecha=a_texto(fila.get("fecha")), usuario=limpio(fila.get("usuario")),
        leidos=int(fila.get("leidos") or 0), nuevos=int(fila.get("nuevos") or 0),
        actualizados=int(fila.get("actualizados") or 0), retirados=int(fila.get("retirados") or 0),
        codigos=int(fila.get("codigos") or 0), total=total)


class ServicioSincronizacion:

    def __init__(self, abrir_profit: AbrirProfit, abrir_app: AbrirApp, cfg: ConfigProfit):
        """
        :param abrir_profit: abre la base de la empresa en Profit -> UnidadProfit
        :param abrir_app: abre SIGUEC_PURACERAMICA -> UnidadApp
        """
        self._abrir_profit = abrir_profit
        self._abrir_app = abrir_app
        self._cfg = cfg

    def _leer_profit(self, co_empresa: str) -> Tuple[List[dict], List[str], List[dict]]:
        """(artículos, columnas extra que sí existen, códigos de barra)."""
        cfg = self._cfg
        with self._abrir_profit(co_empresa) as empresa:
            columnas = empresa.profit.columnas(cfg.tabla_articulos)
            if not columnas:
                raise ErrorValidacion(f"No se encontró la tabla {cfg.tabla_articulos} en la base {co_empresa}. "
                                      "Revise la sección <profit> del config.xml del servidor.")
            faltan = [c for c in (cfg.col_codigo, cfg.col_descripcion) if c.lower() not in columnas]
            if faltan:
                raise ErrorValidacion(f"La tabla {cfg.tabla_articulos} no tiene la columna {', '.join(faltan)}. "
                                      "Revise la sección <profit> del config.xml del servidor.")
            con_anulado = bool(cfg.col_anulado) and cfg.col_anulado.lower() in columnas
            extras = [c for c in cfg.columnas_codigo if c.lower() in columnas]
            ignoradas = [c for c in cfg.columnas_codigo if c.lower() not in columnas]
            if ignoradas:
                log.warning("%s no tiene las columnas %s: no se usan como código", cfg.tabla_articulos, ignoradas)
            filas = empresa.profit.articulos(con_anulado, extras)
            barras: List[dict] = []
            if cfg.tabla_codigos_barra:
                if empresa.profit.columnas(cfg.tabla_codigos_barra):
                    barras = empresa.profit.codigos_barra()
                else:
                    log.warning("No existe la tabla de códigos de barra %s", cfg.tabla_codigos_barra)
        return filas, extras, barras

    def sincronizar(self, sesion: Sesion, co_empresa: str, ip: str = "") -> ResultadoSincronizacion:
        ambiente = ambiente_con_permiso(sesion, co_empresa, "sincronizar_articulos", mensaje=MENSAJE_SIN_PERMISO)
        filas, extras, barras = self._leer_profit(ambiente.co_empresa)
        articulos, codigos = preparar_copia(filas, extras, barras)
        if not articulos:
            raise ErrorValidacion(f"No se leyó ningún artículo de {self._cfg.tabla_articulos}: la copia no se cambió.")

        usuario = sesion.usuario
        with self._abrir_app() as app:
            nuevos, actualizados, retirados = app.articulos.reemplazar(ambiente.co_empresa, articulos, codigos)
            app.articulos.registrar_sincronizacion(ambiente.co_empresa, usuario.usuario, len(articulos), nuevos,
                                                   actualizados, retirados, len(codigos))
            total = app.articulos.total(ambiente.co_empresa)
            app.bitacora.registrar(bitacora.SINCRONIZAR, usuario.id, usuario.usuario, ambiente.co_empresa,
                                   f"Leídos {len(articulos)}: {nuevos} nuevos, {actualizados} actualizados, "
                                   f"{retirados} retirados, {len(codigos)} códigos", ip)
            ultima = app.articulos.ultima_sincronizacion(ambiente.co_empresa) or {}
        log.info("%s sincronizó %s: %s artículos", usuario.usuario, ambiente.co_empresa, len(articulos))
        resultado = _resultado(ultima, total)
        resultado.usuario = usuario.usuario
        resultado.leidos, resultado.nuevos, resultado.actualizados = len(articulos), nuevos, actualizados
        resultado.retirados, resultado.codigos = retirados, len(codigos)
        return resultado

    def ultima(self, sesion: Sesion, co_empresa: str) -> Optional[ResultadoSincronizacion]:
        """La última sincronización (None si nunca se ha sincronizado)."""
        ambiente = ambiente_con_permiso(sesion, co_empresa, "sincronizar_articulos", "consultar_articulos",
                                        "preparar_inventario", "contar_inventario",
                                        mensaje="Su ambiente no permite ver los artículos.")
        with self._abrir_app() as app:
            fila = app.articulos.ultima_sincronizacion(ambiente.co_empresa)
            total = app.articulos.total(ambiente.co_empresa)
        return _resultado(fila, total) if fila else None
