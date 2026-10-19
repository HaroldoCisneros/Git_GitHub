"""
Administración de la App desde el teléfono (solo usuarios con es_administrador).

    usuarios()           Todos los usuarios con su estado y sus empresas/permisos.
    crear()              Usuario nuevo (la clave se guarda con PBKDF2, nunca en texto).
    actualizar()         Nombre, usuario de Profit, administrador y activo.
    cambiar_clave()      Clave nueva (cierra sus sesiones abiertas).
    desbloquear()        Quita el bloqueo por claves erradas.
    guardar_ambiente()   Le da (o cambia) una empresa con su almacén y permisos.
    quitar_ambiente()    Le quita una empresa.
    empresas()           Las empresas de SIGUEC_PURACERAMICA (para elegir).
    bitacora()           Quién entró, sincronizó, preparó o cerró inventarios y cuándo.

Lo mismo que herramientas/usuarios.py hace por consola. Cada cambio queda en
la bitácora con la acción ADMIN. Para no quedarse sin administrador, nadie se
puede quitar a sí mismo el administrador ni desactivarse.
"""

from __future__ import annotations

import logging
import re
from contextlib import AbstractContextManager
from datetime import date, datetime, timedelta
from typing import Callable, Dict, List, Optional

from ..errores import ErrorConflicto, ErrorNoEncontrado, ErrorPermiso, ErrorValidacion
from ..modelos import (AmbienteAdmin, EmpresaApp, PaginaBitacora, Permisos, RenglonBitacora, Sesion,
                       UsuarioAdmin)
from ..repositorios import bitacora as acciones
from ..seguridad import cifrar_clave, validar_clave_nueva
from .valores import a_texto

log = logging.getLogger("siguec.administracion")

PATRON_USUARIO = re.compile(r"^[A-Z0-9_.\-]{1,30}$")
PATRON_PROFIT = re.compile(r"^[A-Z0-9_.\-]{0,10}$")
LARGO_NOMBRE = 100
NOMBRES_PERMISOS = tuple(Permisos.__dataclass_fields__)     # consultar_articulos, sincronizar_articulos...

AbrirApp = Callable[[], AbstractContextManager]


def _texto(valor) -> str:
    return (a_texto(valor) or "").strip()


class ServicioAdministracion:

    def __init__(self, abrir_app: AbrirApp, reloj: Callable[[], datetime] = datetime.now):
        self._abrir = abrir_app
        self._reloj = reloj

    # ------------------------------------------------------------------
    # Apoyo
    # ------------------------------------------------------------------

    @staticmethod
    def _admin(sesion: Sesion) -> None:
        if not sesion.usuario.es_administrador:
            raise ErrorPermiso("Solo el administrador puede usar esta opción.")

    @staticmethod
    def _anotar(app, sesion: Sesion, detalle: str, ip: str, co_empresa: str = "") -> None:
        app.bitacora.registrar(acciones.ADMIN, sesion.usuario.id, sesion.usuario.usuario, co_empresa, detalle, ip)

    @staticmethod
    def _existe(app, usuario_id: int):
        usuario = app.usuarios.por_id(usuario_id)
        if usuario is None:
            raise ErrorNoEncontrado("Ese usuario no existe.")
        return usuario

    def _usuario_admin(self, fila: dict, ambientes) -> UsuarioAdmin:
        bloqueado_hasta = fila.get("bloqueado_hasta")
        return UsuarioAdmin(
            id=int(fila["id"]),
            usuario=_texto(fila.get("usuario")),
            nombre=_texto(fila.get("nombre")),
            usuario_profit=_texto(fila.get("usuario_profit")),
            es_administrador=bool(fila.get("es_administrador")),
            activo=bool(fila.get("activo")),
            bloqueado=bool(bloqueado_hasta and bloqueado_hasta > self._reloj()),
            bloqueado_hasta=a_texto(bloqueado_hasta),
            intentos_fallidos=int(fila.get("intentos_fallidos") or 0),
            ultimo_acceso=a_texto(fila.get("ultimo_acceso")),
            ambientes=[AmbienteAdmin(a.co_empresa, a.nombre_empresa, a.almacen, a.permisos)
                       for a in ambientes],
        )

    # ------------------------------------------------------------------
    # Consulta
    # ------------------------------------------------------------------

    def usuarios(self, sesion: Sesion) -> List[UsuarioAdmin]:
        self._admin(sesion)
        with self._abrir() as app:
            return [self._usuario_admin(f, app.ambientes.de_usuario(int(f["id"]))) for f in app.usuarios.lista()]

    def usuario(self, sesion: Sesion, usuario_id: int) -> UsuarioAdmin:
        self._admin(sesion)
        with self._abrir() as app:
            fila = next((f for f in app.usuarios.lista() if int(f["id"]) == usuario_id), None)
            if fila is None:
                raise ErrorNoEncontrado("Ese usuario no existe.")
            return self._usuario_admin(fila, app.ambientes.de_usuario(usuario_id))

    def empresas(self, sesion: Sesion) -> List[EmpresaApp]:
        self._admin(sesion)
        with self._abrir() as app:
            return [EmpresaApp(_texto(f.get("co_empresa")), _texto(f.get("nombre")), bool(f.get("activa")))
                    for f in app.ambientes.empresas()]

    # ------------------------------------------------------------------
    # Cambios
    # ------------------------------------------------------------------

    def crear(self, sesion: Sesion, usuario: str, nombre: str, clave: str, usuario_profit: str = "",
              es_administrador: bool = False, ip: str = "") -> UsuarioAdmin:
        self._admin(sesion)
        codigo = (usuario or "").strip().upper()
        nombre = (nombre or "").strip()
        profit = (usuario_profit or "").strip().upper()
        if not PATRON_USUARIO.match(codigo):
            raise ErrorValidacion("El usuario admite letras, números, punto, guion y guion bajo (máximo 30).")
        if not nombre or len(nombre) > LARGO_NOMBRE:
            raise ErrorValidacion(f"Escriba el nombre (máximo {LARGO_NOMBRE} caracteres).")
        if not PATRON_PROFIT.match(profit):
            raise ErrorValidacion("El usuario de Profit admite máximo 10 letras o números.")
        try:
            validar_clave_nueva(clave)
        except ValueError as error:
            raise ErrorValidacion(str(error)) from None
        with self._abrir() as app:
            if app.usuarios.por_usuario(codigo) is not None:
                raise ErrorConflicto(f"El usuario {codigo} ya existe.")
            app.usuarios.crear(codigo, nombre, cifrar_clave(clave), profit, es_administrador)
            self._anotar(app, sesion, f"Creó el usuario {codigo}" + (" (administrador)" if es_administrador else ""), ip)
            nuevo = app.usuarios.por_usuario(codigo)
        log.info("%s creó el usuario %s", sesion.usuario.usuario, codigo)
        return self.usuario(sesion, nuevo.id)

    def actualizar(self, sesion: Sesion, usuario_id: int, nombre: Optional[str] = None,
                   usuario_profit: Optional[str] = None, es_administrador: Optional[bool] = None,
                   activo: Optional[bool] = None, ip: str = "") -> UsuarioAdmin:
        """Solo cambia lo que viene (None = se deja igual)."""
        self._admin(sesion)
        propio = usuario_id == sesion.usuario.id
        if propio and es_administrador is False:
            raise ErrorValidacion("No se puede quitar el administrador a usted mismo.")
        if propio and activo is False:
            raise ErrorValidacion("No se puede desactivar a usted mismo.")
        with self._abrir() as app:
            actual = self._existe(app, usuario_id)
            nuevo_nombre = actual.nombre if nombre is None else nombre.strip()
            nuevo_profit = actual.usuario_profit if usuario_profit is None else usuario_profit.strip().upper()
            nuevo_admin = actual.es_administrador if es_administrador is None else es_administrador
            if not nuevo_nombre or len(nuevo_nombre) > LARGO_NOMBRE:
                raise ErrorValidacion(f"Escriba el nombre (máximo {LARGO_NOMBRE} caracteres).")
            if not PATRON_PROFIT.match(nuevo_profit):
                raise ErrorValidacion("El usuario de Profit admite máximo 10 letras o números.")
            app.usuarios.actualizar(usuario_id, nuevo_nombre, nuevo_profit, nuevo_admin)
            cambios = []
            if nuevo_nombre != actual.nombre:
                cambios.append(f"nombre '{nuevo_nombre}'")
            if nuevo_profit != actual.usuario_profit:
                cambios.append(f"usuario Profit '{nuevo_profit}'")
            if nuevo_admin != actual.es_administrador:
                cambios.append("administrador " + ("SÍ" if nuevo_admin else "NO"))
            if activo is not None and activo != actual.activo:
                app.usuarios.activar(usuario_id, activo)
                if not activo:
                    app.sesiones.revocar_de_usuario(usuario_id)    # Sale de inmediato de los teléfonos
                cambios.append("activado" if activo else "desactivado")
            if cambios:
                self._anotar(app, sesion, f"Usuario {actual.usuario}: " + ", ".join(cambios), ip)
        return self.usuario(sesion, usuario_id)

    def cambiar_clave(self, sesion: Sesion, usuario_id: int, clave: str, ip: str = "") -> None:
        self._admin(sesion)
        try:
            validar_clave_nueva(clave)
        except ValueError as error:
            raise ErrorValidacion(str(error)) from None
        with self._abrir() as app:
            usuario = self._existe(app, usuario_id)
            app.usuarios.cambiar_clave(usuario_id, cifrar_clave(clave))
            app.sesiones.revocar_de_usuario(usuario_id)
            self._anotar(app, sesion, f"Cambió la clave de {usuario.usuario} (se cerraron sus sesiones)", ip)

    def desbloquear(self, sesion: Sesion, usuario_id: int, ip: str = "") -> UsuarioAdmin:
        self._admin(sesion)
        with self._abrir() as app:
            usuario = self._existe(app, usuario_id)
            app.usuarios.desbloquear(usuario_id)
            self._anotar(app, sesion, f"Desbloqueó a {usuario.usuario}", ip)
        return self.usuario(sesion, usuario_id)

    def guardar_ambiente(self, sesion: Sesion, usuario_id: int, co_empresa: str, almacen: str,
                         permisos: Dict[str, bool], ip: str = "") -> UsuarioAdmin:
        self._admin(sesion)
        co_empresa = (co_empresa or "").strip().upper()
        almacen = (almacen or "").strip().upper()
        if len(almacen) > 6:
            raise ErrorValidacion("El almacén admite máximo 6 caracteres.")
        desconocidos = set(permisos) - set(NOMBRES_PERMISOS)
        if desconocidos:
            raise ErrorValidacion("Permisos no válidos: " + ", ".join(sorted(desconocidos)))
        nuevos = Permisos(**{n: bool(permisos.get(n, False)) for n in NOMBRES_PERMISOS})
        with self._abrir() as app:
            usuario = self._existe(app, usuario_id)
            if not app.ambientes.existe_empresa(co_empresa):
                raise ErrorNoEncontrado(f"La empresa {co_empresa} no está registrada.")
            app.ambientes.guardar(usuario_id, co_empresa, almacen, nuevos)
            activos = [n for n in NOMBRES_PERMISOS if getattr(nuevos, n)]
            self._anotar(app, sesion, f"Ambiente de {usuario.usuario} en {co_empresa}: " + (", ".join(activos) or
                                                                                          "sin permisos"),
                         ip, co_empresa)
        return self.usuario(sesion, usuario_id)

    def quitar_ambiente(self, sesion: Sesion, usuario_id: int, co_empresa: str, ip: str = "") -> UsuarioAdmin:
        self._admin(sesion)
        co_empresa = (co_empresa or "").strip().upper()
        with self._abrir() as app:
            usuario = self._existe(app, usuario_id)
            if app.ambientes.quitar(usuario_id, co_empresa) == 0:
                raise ErrorNoEncontrado(f"{usuario.usuario} no tiene acceso a {co_empresa}.")
            self._anotar(app, sesion, f"Quitó a {usuario.usuario} la empresa {co_empresa}", ip, co_empresa)
        return self.usuario(sesion, usuario_id)

    # ------------------------------------------------------------------
    # Bitácora
    # ------------------------------------------------------------------

    def bitacora(self, sesion: Sesion, usuario: str = "", co_empresa: str = "", accion: str = "",
                 desde: Optional[date] = None, hasta: Optional[date] = None, pagina: int = 1,
                 tamano: int = 50) -> PaginaBitacora:
        """desde / hasta: días incluidos (hasta = todo ese día)."""
        self._admin(sesion)
        pagina, tamano = max(1, pagina), max(1, min(tamano, 100))
        inicio = datetime.combine(desde, datetime.min.time()) if desde else None
        fin = datetime.combine(hasta + timedelta(days=1), datetime.min.time()) if hasta else None
        with self._abrir() as app:
            filas, hay_mas = app.bitacora.buscar((usuario or "").strip().upper(), (co_empresa or "").strip().upper(),
                                                 (accion or "").strip().upper(), inicio, fin, pagina, tamano)
        return PaginaBitacora(
            renglones=[RenglonBitacora(id=int(f["id"]), fecha=a_texto(f.get("fecha")), usuario=_texto(f.get("usuario")),
                                       co_empresa=_texto(f.get("co_empresa")), accion=_texto(f.get("accion")),
                                       detalle=_texto(f.get("detalle")), ip=_texto(f.get("ip")))
                       for f in filas],
            pagina=pagina, tamano=tamano, hay_mas=hay_mas)
