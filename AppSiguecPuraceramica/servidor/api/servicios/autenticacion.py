"""
Entrada, validación del token y salida de los usuarios de la App.

    entrar()           Usuario + clave -> token nuevo + ambientes del usuario.
    sesion_de_token()  En CADA petición: token -> Sesion (o ErrorSesion).
    salir()            Cierra la sesión del token.

Reglas de seguridad:
    - Si el usuario no existe o la clave está mal, el mensaje es el MISMO
      ("Usuario o clave incorrectos"): así no se puede averiguar qué usuarios existen.
    - Después de <intentos_maximos> claves erradas seguidas el usuario queda
      bloqueado <minutos_bloqueo> minutos (contra quien prueba claves al azar).
    - Los permisos y el estado del usuario se vuelven a leer en cada petición:
      si se le quita un permiso o se desactiva, aplica de inmediato.
"""

from __future__ import annotations

import logging
from contextlib import AbstractContextManager
from datetime import datetime, timedelta
from typing import Callable, Tuple

from ..configuracion import ConfigAplicacion
from ..errores import ErrorAutenticacion, ErrorSesion
from ..modelos import Sesion
from ..repositorios import UnidadApp, bitacora
from ..seguridad import cifrar_clave, clave_coincide, nuevo_token, resumen_token

log = logging.getLogger("siguec.autenticacion")

MENSAJE_INCORRECTO = "Usuario o clave incorrectos."
MENSAJE_VENCIDA = "La sesión venció o se cerró. Entre de nuevo con su usuario y clave."

# El último uso de la sesión se anota como máximo cada 5 minutos (menos escrituras).
MINUTOS_ANOTAR_USO = 5

# Hash de relleno: si el usuario no existe se calcula igual un PBKDF2, para que
# la respuesta tarde lo mismo que con un usuario real.
_HASH_RELLENO = cifrar_clave("relleno-sin-uso")

AbrirApp = Callable[[], AbstractContextManager]


class ServicioAutenticacion:

    def __init__(self, abrir_app: AbrirApp, config: ConfigAplicacion,
                 reloj: Callable[[], datetime] = datetime.now):
        """
        :param abrir_app: función que abre la base SIGUEC_PURACERAMICA y entrega una UnidadApp.
        :param reloj: hora actual (las pruebas la cambian).
        """
        self._abrir = abrir_app
        self._config = config
        self._reloj = reloj

    # ------------------------------------------------------------------
    def entrar(self, codigo: str, clave: str, dispositivo: str = "", ip: str = "") -> Tuple[str, Sesion]:
        """
        :return: (token, sesion). El token se entrega una sola vez: en la base solo queda su SHA-256.
        :raises ErrorAutenticacion: con el motivo para mostrar en el teléfono.
        """
        codigo = (codigo or "").strip().upper()
        if not codigo or not clave:
            raise ErrorAutenticacion("Escriba el usuario y la clave.")
        ahora = self._reloj().replace(microsecond=0)

        # El error se lanza DESPUÉS del "with": así el contador de fallos y la
        # bitácora se graban (si se lanzara adentro, se desharía todo).
        error = None
        with self._abrir() as app:
            app: UnidadApp
            usuario = app.usuarios.por_usuario(codigo)

            if usuario is None:
                clave_coincide(clave, _HASH_RELLENO)
                app.bitacora.registrar(bitacora.CLAVE_ERRADA, usuario=codigo, detalle="Usuario no existe", ip=ip)
                error = MENSAJE_INCORRECTO

            elif usuario.bloqueado_hasta and usuario.bloqueado_hasta > ahora:
                error = ("Usuario bloqueado por varias claves erradas. Intente de nuevo después de las "
                         f"{usuario.bloqueado_hasta:%I:%M %p}.")

            elif not clave_coincide(clave, usuario.clave_hash):
                intentos = usuario.intentos_fallidos + 1
                bloqueo = None
                if intentos >= self._config.intentos_maximos:
                    bloqueo = ahora + timedelta(minutes=self._config.minutos_bloqueo)
                    intentos = 0
                    app.bitacora.registrar(bitacora.BLOQUEO, usuario.id, codigo, ip=ip,
                                           detalle=f"Bloqueado hasta {bloqueo:%Y-%m-%d %H:%M}")
                app.usuarios.registrar_fallo(usuario.id, intentos, bloqueo)
                app.bitacora.registrar(bitacora.CLAVE_ERRADA, usuario.id, codigo, ip=ip)
                error = MENSAJE_INCORRECTO

            elif not usuario.activo:
                error = "El usuario está inactivo. Hable con el administrador."

            else:
                ambientes = app.ambientes.de_usuario(usuario.id)
                if not ambientes:
                    error = "El usuario no tiene ambiente en ninguna empresa. Hable con el administrador."
                else:
                    token = nuevo_token()
                    expira = ahora + timedelta(hours=self._config.horas_sesion)
                    app.sesiones.crear(resumen_token(token), usuario.id, ahora, expira, dispositivo, ip)
                    app.usuarios.registrar_acceso(usuario.id, ahora)
                    app.bitacora.registrar(bitacora.ENTRADA, usuario.id, codigo, ip=ip, detalle=dispositivo)
                    log.info("Entrada de %s desde %s (%s)", codigo, ip, dispositivo)
                    return token, Sesion(usuario, ambientes, expira, dispositivo)

        log.info("Entrada rechazada de %s desde %s: %s", codigo, ip, error)
        raise ErrorAutenticacion(error)

    # ------------------------------------------------------------------
    def sesion_de_token(self, token: str) -> Sesion:
        """:raises ErrorSesion: si el token no sirve (el teléfono vuelve a pedir la clave)."""
        if not token:
            raise ErrorSesion("Falta el token de la sesión.")
        resumen = resumen_token(token)
        ahora = self._reloj().replace(microsecond=0)
        with self._abrir() as app:
            fila = app.sesiones.buscar(resumen)
            if fila is None or fila.get("revocada") or fila["expira"] <= ahora:
                raise ErrorSesion(MENSAJE_VENCIDA)
            usuario = app.usuarios.por_id(fila["usuario_id"])
            if usuario is None or not usuario.activo:
                raise ErrorSesion("El usuario está inactivo. Hable con el administrador.")
            ambientes = app.ambientes.de_usuario(usuario.id)
            ultimo = fila.get("ultimo_uso")
            if ultimo is None or ahora - ultimo >= timedelta(minutes=MINUTOS_ANOTAR_USO):
                app.sesiones.anotar_uso(resumen, ahora)
            return Sesion(usuario, ambientes, fila["expira"], (fila.get("dispositivo") or "").strip())

    # ------------------------------------------------------------------
    def salir(self, token: str, ip: str = "") -> None:
        if not token:
            return
        resumen = resumen_token(token)
        with self._abrir() as app:
            fila = app.sesiones.buscar(resumen)
            if fila is not None:
                app.sesiones.revocar(resumen)
                app.bitacora.registrar(bitacora.SALIDA, fila["usuario_id"], ip=ip)
