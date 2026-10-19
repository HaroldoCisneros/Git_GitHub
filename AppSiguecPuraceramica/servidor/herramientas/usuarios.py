"""
Administración de usuarios de AppSiguecPuraceramica desde la consola del servidor.

(Para el primer administrador; después se hace desde la App: "Usuarios y permisos".) Se ejecuta en
la carpeta "servidor", con el mismo config.xml de la API:

    python herramientas/usuarios.py empresa PURACERAMICA "Pura Cerámica"
    python herramientas/usuarios.py crear HCISNEROS "Haroldo Cisneros" --admin
    python herramientas/usuarios.py ambiente HCISNEROS PURACERAMICA --almacen 01 \
                                    --sincronizar --preparar
    python herramientas/usuarios.py clave HCISNEROS          (cambia la clave)
    python herramientas/usuarios.py desactivar HCISNEROS     (y "activar")

La clave se pide en pantalla sin mostrarse (nunca va en la línea de comandos,
porque quedaría en el historial). Al cambiar la clave o desactivar al usuario
se cierran todas sus sesiones abiertas en los teléfonos.
"""

from __future__ import annotations

import argparse
import getpass
import os
import sys

# Para poder importar el paquete "api" estando en servidor/herramientas.
CARPETA_SERVIDOR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, CARPETA_SERVIDOR)

from api.base_datos import BaseDatos, validar_nombre_base  # noqa: E402
from api.configuracion import leer_configuracion  # noqa: E402
from api.modelos import Permisos  # noqa: E402
from api.repositorios import UnidadApp  # noqa: E402
from api.seguridad import cifrar_clave, validar_clave_nueva  # noqa: E402


def pedir_clave() -> str:
    """Pide la clave dos veces y la valida."""
    while True:
        clave = getpass.getpass("Clave nueva: ")
        try:
            validar_clave_nueva(clave)
        except ValueError as error:
            print(error)
            continue
        if clave != getpass.getpass("Repita la clave: "):
            print("Las claves no coinciden.")
            continue
        return clave


def _usuario(app: UnidadApp, codigo: str):
    usuario = app.usuarios.por_usuario(codigo)
    if usuario is None:
        sys.exit(f"No existe el usuario {codigo.upper()}.")
    return usuario


def main() -> None:
    parser = argparse.ArgumentParser(description="Usuarios de AppSiguecPuraceramica")
    parser.add_argument("--config", default=os.path.join(CARPETA_SERVIDOR, "config.xml"))
    ordenes = parser.add_subparsers(dest="orden", required=True)

    p = ordenes.add_parser("empresa", help="Agrega una empresa (base de Profit)")
    p.add_argument("co_empresa", help="Nombre de la base de Profit 2K12, ej. PURACERAMICA")
    p.add_argument("nombre", help="Nombre que ve el usuario")

    p = ordenes.add_parser("crear", help="Crea un usuario")
    p.add_argument("usuario")
    p.add_argument("nombre")
    p.add_argument("--profit", default="", help="Usuario de Profit (referencia)")
    p.add_argument("--admin", action="store_true")

    p = ordenes.add_parser("ambiente", help="Da acceso a una empresa (o cambia los permisos)")
    p.add_argument("usuario")
    p.add_argument("co_empresa")
    p.add_argument("--almacen", default="", help="Almacén por defecto al preparar")
    p.add_argument("--sin-consulta", action="store_true", help="NO puede consultar artículos")
    p.add_argument("--sincronizar", action="store_true", help="Puede sincronizar los artículos con Profit")
    p.add_argument("--preparar", action="store_true", help="Puede preparar/cerrar inventarios y ver diferencias")
    p.add_argument("--sin-contar", action="store_true", help="NO puede contar")

    p = ordenes.add_parser("clave", help="Cambia la clave")
    p.add_argument("usuario")
    p = ordenes.add_parser("desactivar", help="Desactiva al usuario")
    p.add_argument("usuario")
    p = ordenes.add_parser("activar", help="Activa al usuario")
    p.add_argument("usuario")

    a = parser.parse_args()
    config = leer_configuracion(a.config)
    bd = BaseDatos(config.sql)

    # La clave se pide ANTES de abrir la transacción (no la deja abierta esperando).
    clave = pedir_clave() if a.orden in ("crear", "clave") else ""

    with bd.conexion(config.aplicacion.base_usuarios) as cx:
        app = UnidadApp(cx)
        if a.orden == "empresa":
            app.ambientes.guardar_empresa(validar_nombre_base(a.co_empresa.upper()), a.nombre)
            print(f"Empresa {a.co_empresa.upper()} lista.")
        elif a.orden == "crear":
            if app.usuarios.por_usuario(a.usuario):
                sys.exit(f"El usuario {a.usuario.upper()} ya existe.")
            app.usuarios.crear(a.usuario, a.nombre, cifrar_clave(clave), a.profit, a.admin)
            print(f"Usuario {a.usuario.upper()} creado. Ahora dele un ambiente (orden 'ambiente').")
        elif a.orden == "ambiente":
            usuario = _usuario(app, a.usuario)
            permisos = Permisos(consultar_articulos=not a.sin_consulta, sincronizar_articulos=a.sincronizar,
                                preparar_inventario=a.preparar, contar_inventario=not a.sin_contar)
            app.ambientes.guardar(usuario.id, a.co_empresa.upper(), a.almacen.upper(), permisos)
            print(f"Ambiente de {usuario.usuario} en {a.co_empresa.upper()}: {permisos}")
        elif a.orden == "clave":
            usuario = _usuario(app, a.usuario)
            app.usuarios.cambiar_clave(usuario.id, cifrar_clave(clave))
            app.sesiones.revocar_de_usuario(usuario.id)
            print(f"Clave de {usuario.usuario} cambiada; se cerraron sus sesiones.")
        else:
            usuario = _usuario(app, a.usuario)
            app.usuarios.activar(usuario.id, a.orden == "activar")
            if a.orden == "desactivar":
                app.sesiones.revocar_de_usuario(usuario.id)
            print(f"Usuario {usuario.usuario}: {a.orden}do.")


if __name__ == "__main__":
    main()
