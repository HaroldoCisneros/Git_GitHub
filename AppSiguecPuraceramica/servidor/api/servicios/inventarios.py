"""
Inventarios por almacén: preparar (abrir), consultar, cerrar y comparar.

    almacenes()     Los almacenes de Profit 2K12 y si ya tienen un inventario abierto.
    lista()         Los inventarios de la empresa (abiertos primero).
    preparar()      Abre el inventario de un almacén y toma la FOTO del stock de     [preparar_inventario]
                    Profit (saStockAlmacen) de cada artículo en ese almacén.
    detalle()       Datos del inventario y los últimos conteos del usuario.
    cerrar()        Ya no se puede contar más.                                      [preparar_inventario]
    comparacion()   Stock contado vs. la foto del stock: sobrantes, faltantes y      [preparar_inventario]
                    artículos con stock que nadie contó.

Solo puede haber UN inventario abierto por almacén: así todos los que cuentan
ese almacén anotan en el mismo. NUNCA se escribe en Profit.
"""

from __future__ import annotations

import logging
from decimal import Decimal
from typing import Dict, List

from ..configuracion import ConfigAplicacion
from ..errores import ErrorConflicto, ErrorNoEncontrado, ErrorValidacion
from ..modelos import (Almacen, ComparacionInventario, DetalleInventario, DiferenciaInventario, Inventario,
                       Sesion)
from ..repositorios import bitacora
from ..repositorios.inventarios import ABIERTO
from .comun import AbrirApp, AbrirProfit, ambiente_con_permiso
from .conteos import conteo_individual
from .valores import CERO, a_texto, limpio, texto_decimal

log = logging.getLogger("siguec.inventarios")

LARGO_DESCRIPCION = 100
MENSAJE_SIN_PREPARAR = "Su ambiente no permite preparar, cerrar ni comparar inventarios."
MENSAJE_SIN_PERMISO = "Su ambiente no permite la toma de inventario."


def inventario_desde_fila(f: dict) -> Inventario:
    return Inventario(
        id=int(f["id"]),
        co_alma=limpio(f.get("co_alma")),
        des_alma=limpio(f.get("des_alma")),
        descripcion=limpio(f.get("descripcion")),
        abierto=limpio(f.get("estado")) == ABIERTO,
        creado_por=limpio(f.get("creado_por")),
        fecha_creacion=a_texto(f.get("fecha_creacion")),
        cerrado_por=limpio(f.get("cerrado_por")),
        fecha_cierre=a_texto(f.get("fecha_cierre")),
        articulos_stock=int(f.get("articulos_stock") or 0),
        articulos_contados=int(f.get("articulos_contados") or 0),
        conteos=int(f.get("conteos") or 0),
    )


def comparar(stock: Dict[str, Decimal], contados: Dict[str, Decimal], descripciones: Dict[str, str],
             incluir_no_contados: bool, solo_diferencias: bool) -> List[DiferenciaInventario]:
    """
    Junta la foto del stock con lo contado (no toca la base: se prueba solo).
    Ordena por la diferencia más grande (en valor absoluto) primero.
    """
    codigos = set(contados)
    if incluir_no_contados:
        codigos |= {c for c, s in stock.items() if s != 0}
    filas = []
    for co_art in codigos:
        existencia = stock.get(co_art, CERO)
        contado = contados.get(co_art, CERO)
        diferencia = contado - existencia
        if solo_diferencias and diferencia == 0:
            continue
        filas.append(DiferenciaInventario(
            co_art=co_art, art_des=descripciones.get(co_art, ""), stock=texto_decimal(existencia),
            contado=texto_decimal(contado), diferencia=texto_decimal(diferencia), contado_si=co_art in contados))
    filas.sort(key=lambda d: (-abs(Decimal(d.diferencia)), d.co_art))
    return filas


class ServicioInventarios:

    def __init__(self, abrir_profit: AbrirProfit, abrir_app: AbrirApp, config: ConfigAplicacion):
        self._abrir_profit = abrir_profit
        self._abrir_app = abrir_app
        self._config = config

    # ------------------------------------------------------------------
    # Apoyo
    # ------------------------------------------------------------------

    @staticmethod
    def _preparador(sesion: Sesion, co_empresa: str):
        return ambiente_con_permiso(sesion, co_empresa, "preparar_inventario", mensaje=MENSAJE_SIN_PREPARAR)

    @staticmethod
    def _cualquiera(sesion: Sesion, co_empresa: str):
        return ambiente_con_permiso(sesion, co_empresa, "preparar_inventario", "contar_inventario",
                                    "consultar_articulos", mensaje=MENSAJE_SIN_PERMISO)

    @staticmethod
    def leer(app, inventario_id: int, co_empresa: str) -> dict:
        fila = app.inventarios.por_id(inventario_id, co_empresa)
        if fila is None:
            raise ErrorNoEncontrado(f"No existe el inventario {inventario_id} en esta empresa.")
        return fila

    # ------------------------------------------------------------------
    # Consulta
    # ------------------------------------------------------------------

    def almacenes(self, sesion: Sesion, co_empresa: str) -> List[Almacen]:
        ambiente = self._cualquiera(sesion, co_empresa)
        with self._abrir_profit(ambiente.co_empresa) as empresa:
            filas = empresa.profit.almacenes()
        with self._abrir_app() as app:
            abiertos = app.inventarios.abiertos_por_almacen(ambiente.co_empresa)
        return [Almacen(co_alma=limpio(f.get("co_alma")), des_alma=limpio(f.get("des_alma")),
                        inventario_abierto=abiertos.get(limpio(f.get("co_alma"))))
                for f in filas if limpio(f.get("co_alma"))]

    def lista(self, sesion: Sesion, co_empresa: str, solo_abiertos: bool = False) -> List[Inventario]:
        ambiente = self._cualquiera(sesion, co_empresa)
        with self._abrir_app() as app:
            filas = app.inventarios.lista(ambiente.co_empresa, ABIERTO if solo_abiertos else "")
        return [inventario_desde_fila(f) for f in filas]

    def detalle(self, sesion: Sesion, co_empresa: str, inventario_id: int) -> DetalleInventario:
        """Datos del inventario + los últimos conteos de quien consulta."""
        ambiente = self._cualquiera(sesion, co_empresa)
        yo = sesion.usuario.usuario
        with self._abrir_app() as app:
            inventario = inventario_desde_fila(self.leer(app, inventario_id, ambiente.co_empresa))
            mios = app.inventarios.ultimos(inventario_id, yo)
            cuantos = app.inventarios.cuantos_de_usuario(inventario_id, yo)
        return DetalleInventario(
            inventario=inventario,
            mis_conteos=[conteo_individual(f, puede_borrar=inventario.abierto) for f in mios],
            contado_por_mi=cuantos)

    # ------------------------------------------------------------------
    # Preparar y cerrar
    # ------------------------------------------------------------------

    def preparar(self, sesion: Sesion, co_empresa: str, co_alma: str, descripcion: str = "",
                 ip: str = "") -> Inventario:
        """Abre el inventario del almacén y guarda la foto del stock de Profit."""
        ambiente = self._preparador(sesion, co_empresa)
        co_alma = (co_alma or "").strip().upper()
        if not co_alma or len(co_alma) > 6:
            raise ErrorValidacion("Elija el almacén que se va a contar.")
        descripcion = (descripcion or "").strip()
        if len(descripcion) > LARGO_DESCRIPCION:
            raise ErrorValidacion(f"La descripción admite máximo {LARGO_DESCRIPCION} caracteres.")

        # 1. En Profit: que el almacén exista y la foto de su stock
        with self._abrir_profit(ambiente.co_empresa) as empresa:
            almacen = next((f for f in empresa.profit.almacenes()
                            if limpio(f.get("co_alma")).upper() == co_alma), None)
            if almacen is None:
                raise ErrorValidacion(f"El almacén {co_alma} no existe en Profit.")
            stock = empresa.profit.stock_almacen(limpio(almacen.get("co_alma")))
        co_alma = limpio(almacen.get("co_alma"))
        des_alma = limpio(almacen.get("des_alma"))
        descripcion = descripcion or f"Inventario {des_alma or 'almacén ' + co_alma}"[:LARGO_DESCRIPCION]

        # 2. En la App: uno solo abierto por almacén
        usuario = sesion.usuario
        with self._abrir_app() as app:
            abierto = app.inventarios.abierto_del_almacen(ambiente.co_empresa, co_alma)
            if abierto is not None:
                raise ErrorConflicto(f"El almacén {co_alma} ya tiene abierto el inventario N° {abierto}. "
                                     "Ciérrelo antes de preparar otro.")
            inventario_id = app.inventarios.crear(ambiente.co_empresa, co_alma, des_alma, descripcion,
                                                  usuario.usuario, sorted(stock.items()))
            fila = self.leer(app, inventario_id, ambiente.co_empresa)
            app.bitacora.registrar(bitacora.INVENTARIO_ABRIR, usuario.id, usuario.usuario, ambiente.co_empresa,
                                   f"Inventario {inventario_id}, almacén {co_alma}: {descripcion} "
                                   f"({len(stock)} artículos en la foto del stock)", ip)
        log.info("%s preparó el inventario %s del almacén %s", usuario.usuario, inventario_id, co_alma)
        return inventario_desde_fila(fila)

    def cerrar(self, sesion: Sesion, co_empresa: str, inventario_id: int, ip: str = "") -> Inventario:
        ambiente = self._preparador(sesion, co_empresa)
        usuario = sesion.usuario
        with self._abrir_app() as app:
            fila = self.leer(app, inventario_id, ambiente.co_empresa)
            if limpio(fila.get("estado")) != ABIERTO:
                raise ErrorConflicto("El inventario ya está cerrado.")
            app.inventarios.cerrar(inventario_id, usuario.usuario)
            fila = self.leer(app, inventario_id, ambiente.co_empresa)
            app.bitacora.registrar(bitacora.INVENTARIO_CERRAR, usuario.id, usuario.usuario, ambiente.co_empresa,
                                   f"Inventario {inventario_id}, almacén {limpio(fila.get('co_alma'))}", ip)
        return inventario_desde_fila(fila)

    # ------------------------------------------------------------------
    # Comparación
    # ------------------------------------------------------------------

    def comparacion(self, sesion: Sesion, co_empresa: str, inventario_id: int, solo_diferencias: bool = True,
                    incluir_no_contados: bool = True, pagina: int = 1, tamano: int = 50) -> ComparacionInventario:
        ambiente = self._preparador(sesion, co_empresa)
        pagina = max(1, pagina)
        tamano = max(1, min(tamano, self._config.tamano_pagina_maximo))
        with self._abrir_app() as app:
            inventario = inventario_desde_fila(self.leer(app, inventario_id, ambiente.co_empresa))
            stock = app.inventarios.stock(inventario_id)
            contados = app.inventarios.totales(inventario_id)
            descripciones = app.articulos.descripciones(ambiente.co_empresa)

        todas = comparar(stock, contados, descripciones, True, False)
        resumen = [d for d in todas if d.contado_si]
        filas = comparar(stock, contados, descripciones, incluir_no_contados, solo_diferencias)
        desde = (pagina - 1) * tamano
        return ComparacionInventario(
            inventario=inventario,
            diferencias=filas[desde:desde + tamano],
            articulos_contados=len(resumen),
            con_diferencia=sum(1 for d in resumen if Decimal(d.diferencia) != 0),
            sobrantes=sum(1 for d in resumen if Decimal(d.diferencia) > 0),
            faltantes=sum(1 for d in resumen if Decimal(d.diferencia) < 0),
            no_contados=sum(1 for d in todas if not d.contado_si),
            pagina=pagina, tamano=tamano, hay_mas=len(filas) > desde + tamano,
        )
