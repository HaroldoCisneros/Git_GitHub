"""
Conteos individuales en un inventario abierto (escaneando el código de barras).

    anotar()    Lo escaneado + la cantidad -> un conteo a nombre de quien cuenta.   [contar_inventario]
    borrar()    Borra un conteo equivocado (el propio; quien prepara, cualquiera).
    ultimos()   Los últimos conteos (los propios; quien prepara puede ver los de todos).

Cada persona cuenta por su lado: cada anotación queda con su usuario. El stock
contado de un artículo es la SUMA de todos los conteos de ese inventario.
El código se busca en la COPIA sincronizada (co_art, modelo, ref, códigos de
barra): no se consulta Profit mientras se cuenta.
"""

from __future__ import annotations

import logging
from decimal import Decimal
from typing import List

from ..errores import ErrorConflicto, ErrorNoEncontrado, ErrorPermiso, ErrorValidacion
from ..modelos import ConteoIndividual, ResultadoConteo, Sesion
from ..repositorios import bitacora
from ..repositorios.inventarios import ABIERTO
from .comun import AbrirApp, ambiente_con_permiso, anotar as anotar_bitacora
from .valores import a_decimal, a_texto, leer_cantidad, limpio, texto_decimal

log = logging.getLogger("siguec.conteos")

LARGO_MAXIMO_CODIGO = 60
CANTIDAD_MAXIMA = Decimal("1000000")
MENSAJE_SIN_PERMISO = "Su ambiente no permite contar inventario."


def conteo_individual(f: dict, puede_borrar: bool = False) -> ConteoIndividual:
    return ConteoIndividual(
        id=int(f["id"]), co_art=limpio(f.get("co_art")), art_des=limpio(f.get("art_des")),
        codigo_leido=limpio(f.get("codigo_leido")), cantidad=texto_decimal(a_decimal(f.get("cantidad"))),
        usuario=limpio(f.get("usuario")), fecha=a_texto(f.get("fecha")), puede_borrar=puede_borrar)


def validar_cantidad(texto: str) -> Decimal:
    """
    "12", "2,5" o "-1" (negativo para corregir) -> Decimal. El 0 se acepta: "lo busqué y no hay".
    :raises ErrorValidacion: con el motivo
    """
    try:
        valor = leer_cantidad(texto)
    except ValueError as error:
        raise ErrorValidacion(str(error)) from None
    if abs(valor) >= CANTIDAD_MAXIMA:
        raise ErrorValidacion("La cantidad debe ser menor de 1.000.000.")
    if valor.as_tuple().exponent < -5:
        raise ErrorValidacion("La cantidad admite máximo 5 decimales.")
    return valor


class ServicioConteos:

    def __init__(self, abrir_app: AbrirApp):
        self._abrir_app = abrir_app

    @staticmethod
    def _inventario_abierto(app, inventario_id: int, co_empresa: str) -> dict:
        fila = app.inventarios.por_id(inventario_id, co_empresa)
        if fila is None:
            raise ErrorNoEncontrado(f"No existe el inventario {inventario_id} en esta empresa.")
        if limpio(fila.get("estado")) != ABIERTO:
            raise ErrorConflicto("El inventario ya está cerrado: no se puede contar ni corregir.")
        return fila

    # ------------------------------------------------------------------
    def anotar(self, sesion: Sesion, co_empresa: str, inventario_id: int, codigo: str,
               cantidad: str) -> ResultadoConteo:
        """
        :param codigo: lo escaneado o escrito (co_art, modelo, ref o código de barra)
        :param cantidad: lo contado (se SUMA a lo anterior; negativo para corregir)
        """
        ambiente = ambiente_con_permiso(sesion, co_empresa, "contar_inventario", mensaje=MENSAJE_SIN_PERMISO)
        codigo = (codigo or "").strip()
        if not codigo or len(codigo) > LARGO_MAXIMO_CODIGO:
            raise ErrorValidacion("Escanee o escriba un código válido.")
        valor = validar_cantidad(cantidad)
        usuario = sesion.usuario.usuario
        with self._abrir_app() as app:
            self._inventario_abierto(app, inventario_id, ambiente.co_empresa)
            articulo = app.articulos.por_codigo(ambiente.co_empresa, codigo)
            if articulo is None:
                raise ErrorNoEncontrado(f"No hay ningún artículo con el código {codigo}. "
                                        "Si es nuevo en Profit, pida que sincronicen los artículos.")
            co_art = limpio(articulo.get("co_art"))
            conteo_id = app.inventarios.anotar(inventario_id, co_art, codigo, valor, usuario)
            total, mio, _ = app.inventarios.contado(inventario_id, co_art, usuario)
        return ResultadoConteo(
            conteo=ConteoIndividual(id=conteo_id, co_art=co_art, art_des=limpio(articulo.get("art_des")),
                                    codigo_leido=codigo, cantidad=texto_decimal(valor), usuario=usuario,
                                    fecha=None, puede_borrar=True),
            contado_usuario=texto_decimal(mio), contado_total=texto_decimal(total))

    def borrar(self, sesion: Sesion, co_empresa: str, inventario_id: int, conteo_id: int, ip: str = "") -> None:
        """Solo con el inventario abierto; cada quien borra lo suyo, quien prepara cualquiera."""
        ambiente = ambiente_con_permiso(sesion, co_empresa, "contar_inventario", "preparar_inventario",
                                        mensaje=MENSAJE_SIN_PERMISO)
        with self._abrir_app() as app:
            self._inventario_abierto(app, inventario_id, ambiente.co_empresa)
            conteo = app.inventarios.conteo(conteo_id)
            if conteo is None or int(conteo["inventario_id"]) != inventario_id:
                raise ErrorNoEncontrado("Ese conteo ya no existe.")
            propio = limpio(conteo.get("usuario")) == sesion.usuario.usuario
            if not (propio or ambiente.permisos.preparar_inventario):
                raise ErrorPermiso("Solo puede borrar lo que usted contó.")
            app.inventarios.borrar(conteo_id, inventario_id)
        if not propio:
            # Borrar lo que contó otra persona queda en la bitácora
            anotar_bitacora(self._abrir_app, bitacora.CONTEO_BORRAR, sesion, ambiente.co_empresa,
                            f"Inventario {inventario_id}: borró {texto_decimal(a_decimal(conteo.get('cantidad')))} "
                            f"de {limpio(conteo.get('co_art'))} contado por {limpio(conteo.get('usuario'))}", ip)

    def ultimos(self, sesion: Sesion, co_empresa: str, inventario_id: int, de_todos: bool = False,
                cuantos: int = 50) -> List[ConteoIndividual]:
        """Los últimos conteos del usuario (de_todos=True solo quien prepara)."""
        ambiente = ambiente_con_permiso(sesion, co_empresa, "contar_inventario", "preparar_inventario",
                                        mensaje=MENSAJE_SIN_PERMISO)
        if de_todos and not ambiente.permisos.preparar_inventario:
            raise ErrorPermiso("Solo quien prepara el inventario ve los conteos de todos.")
        yo = sesion.usuario.usuario
        with self._abrir_app() as app:
            fila = app.inventarios.por_id(inventario_id, ambiente.co_empresa)
            if fila is None:
                raise ErrorNoEncontrado(f"No existe el inventario {inventario_id} en esta empresa.")
            abierto = limpio(fila.get("estado")) == ABIERTO
            filas = app.inventarios.ultimos(inventario_id, "" if de_todos else yo, max(1, min(cuantos, 200)))
        return [conteo_individual(f, puede_borrar=abierto and (ambiente.permisos.preparar_inventario
                                                              or limpio(f.get("usuario")) == yo))
                for f in filas]
