"""
Consulta de artículos para la toma de inventario (sobre la COPIA sincronizada).

Solo se muestra: código, descripción y stock contado (si lo hay) en el
inventario elegido. Ni precios ni existencia de Profit (el conteo es "a ciegas").

    buscar()       Por código, descripción o cualquier código para escanear.
    por_codigo()   Lo escaneado -> el artículo y lo que lleva contado.
"""

from __future__ import annotations

from typing import Optional

from ..configuracion import ConfigAplicacion
from ..errores import ErrorNoEncontrado, ErrorValidacion
from ..modelos import ArticuloConsulta, PaginaArticulos, Sesion
from .comun import AbrirApp, ambiente_con_permiso
from .valores import a_decimal, limpio, texto_decimal

LARGO_MAXIMO_TEXTO = 60
MENSAJE_SIN_PERMISO = "Su ambiente no permite consultar artículos."


def _consulta(fila: dict) -> ArticuloConsulta:
    contado = fila.get("contado")
    return ArticuloConsulta(co_art=limpio(fila.get("co_art")), art_des=limpio(fila.get("art_des")),
                            contado=None if contado is None else texto_decimal(a_decimal(contado)))


class ServicioArticulos:

    def __init__(self, abrir_app: AbrirApp, config: ConfigAplicacion):
        self._abrir_app = abrir_app
        self._config = config

    def _ambiente(self, sesion: Sesion, co_empresa: str):
        # Quien cuenta también consulta (para saber cuánto lleva un artículo)
        return ambiente_con_permiso(sesion, co_empresa, "consultar_articulos", "contar_inventario",
                                    mensaje=MENSAJE_SIN_PERMISO)

    @staticmethod
    def _revisar_inventario(app, inventario_id: Optional[int], co_empresa: str) -> int:
        """0 = sin inventario. Si viene uno, debe ser de la misma empresa."""
        if not inventario_id:
            return 0
        if app.inventarios.por_id(inventario_id, co_empresa) is None:
            raise ErrorNoEncontrado(f"No existe el inventario {inventario_id} en esta empresa.")
        return inventario_id

    def buscar(self, sesion: Sesion, co_empresa: str, texto: str = "", inventario_id: Optional[int] = None,
               solo_contados: bool = False, pagina: int = 1, tamano: int = 30) -> PaginaArticulos:
        """
        :param texto: código, descripción o código de barra ("*" = comodín; vacío = todos)
        :param inventario_id: de qué inventario sale el stock contado (None = no se muestra)
        :param solo_contados: True = solo los que ya tienen conteo en ese inventario
        """
        ambiente = self._ambiente(sesion, co_empresa)
        texto = (texto or "").strip()
        if len(texto) > LARGO_MAXIMO_TEXTO:
            raise ErrorValidacion(f"El texto a buscar admite máximo {LARGO_MAXIMO_TEXTO} caracteres.")
        pagina = max(1, pagina)
        tamano = max(1, min(tamano, self._config.tamano_pagina_maximo))
        with self._abrir_app() as app:
            inv = self._revisar_inventario(app, inventario_id, ambiente.co_empresa)
            filas, hay_mas = app.articulos.buscar(ambiente.co_empresa, texto, inv, solo_contados and inv > 0,
                                                  pagina, tamano)
        return PaginaArticulos(articulos=[_consulta(f) for f in filas], pagina=pagina, tamano=tamano,
                               hay_mas=hay_mas, inventario_id=inv or None)

    def por_codigo(self, sesion: Sesion, co_empresa: str, codigo: str,
                   inventario_id: Optional[int] = None) -> ArticuloConsulta:
        """:raises ErrorNoEncontrado: si ningún artículo de la copia tiene ese código."""
        ambiente = self._ambiente(sesion, co_empresa)
        codigo = (codigo or "").strip()
        if not codigo or len(codigo) > LARGO_MAXIMO_TEXTO:
            raise ErrorValidacion("Escriba o escanee un código válido.")
        with self._abrir_app() as app:
            inv = self._revisar_inventario(app, inventario_id, ambiente.co_empresa)
            fila = app.articulos.por_codigo(ambiente.co_empresa, codigo)
            if fila is None:
                raise ErrorNoEncontrado(f"No hay ningún artículo con el código {codigo}. "
                                        "Si es nuevo en Profit, sincronice los artículos.")
            articulo = ArticuloConsulta(co_art=limpio(fila.get("co_art")), art_des=limpio(fila.get("art_des")))
            if inv:
                total, _, veces = app.inventarios.contado(inv, articulo.co_art, sesion.usuario.usuario)
                # Sin conteos = None ("no se ha contado"); un 0 contado sí se muestra
                articulo.contado = texto_decimal(total) if veces else None
        return articulo
