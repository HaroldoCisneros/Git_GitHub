"""
Bases de datos falsas, en memoria, para probar los servicios y la API sin SQL Server.

Imitan los métodos de los repositorios reales (mismos nombres y parámetros) y
también la transacción: si dentro del "with" ocurre un error, se deshace todo
lo hecho (igual que el ROLLBACK de base_datos.py).
"""

from __future__ import annotations

import copy
import fnmatch
from contextlib import contextmanager
from decimal import Decimal

from api.configuracion import ConfigAplicacion, ConfigProfit
from api.modelos import Ambiente, Permisos, Usuario
from api.seguridad import cifrar_clave
from api.servicios.administracion import ServicioAdministracion
from api.servicios.articulos import ServicioArticulos
from api.servicios.autenticacion import ServicioAutenticacion
from api.servicios.conteos import ServicioConteos
from api.servicios.inventarios import ServicioInventarios
from api.servicios.sincronizacion import ServicioSincronizacion

EMPRESA = "PURACERAMICA"


# ---------------------------------------------------------------------------
# Base SIGUEC_PURACERAMICA: usuarios y accesos
# ---------------------------------------------------------------------------

class _Usuarios:
    def __init__(self, datos):
        self.d = datos

    def por_usuario(self, codigo):
        u = self.d["usuarios"].get(codigo.strip().upper())
        return copy.copy(u) if u else None

    def por_id(self, usuario_id):
        return next((copy.copy(u) for u in self.d["usuarios"].values() if u.id == usuario_id), None)

    def _de_id(self, usuario_id):
        return next(u for u in self.d["usuarios"].values() if u.id == usuario_id)

    def registrar_fallo(self, usuario_id, intentos, bloqueado_hasta):
        u = self._de_id(usuario_id)
        u.intentos_fallidos, u.bloqueado_hasta = intentos, bloqueado_hasta

    def registrar_acceso(self, usuario_id, fecha):
        u = self._de_id(usuario_id)
        u.intentos_fallidos, u.bloqueado_hasta = 0, None

    def crear(self, codigo, nombre, clave_hash, usuario_profit="", es_administrador=False):
        nuevo_id = max([u.id for u in self.d["usuarios"].values()] + [0]) + 1
        self.d["usuarios"][codigo] = Usuario(id=nuevo_id, usuario=codigo, nombre=nombre, clave_hash=clave_hash,
                                             usuario_profit=usuario_profit, es_administrador=es_administrador)

    def lista(self):
        return [{"id": u.id, "usuario": u.usuario, "nombre": u.nombre, "usuario_profit": u.usuario_profit,
                 "es_administrador": u.es_administrador, "activo": u.activo,
                 "intentos_fallidos": u.intentos_fallidos, "bloqueado_hasta": u.bloqueado_hasta,
                 "ultimo_acceso": None} for u in sorted(self.d["usuarios"].values(), key=lambda u: u.usuario)]

    def actualizar(self, usuario_id, nombre, usuario_profit, es_administrador):
        u = self._de_id(usuario_id)
        u.nombre, u.usuario_profit, u.es_administrador = nombre, usuario_profit, es_administrador

    def cambiar_clave(self, usuario_id, clave_hash):
        u = self._de_id(usuario_id)
        u.clave_hash, u.intentos_fallidos, u.bloqueado_hasta = clave_hash, 0, None

    def activar(self, usuario_id, activo):
        self._de_id(usuario_id).activo = activo

    def desbloquear(self, usuario_id):
        u = self._de_id(usuario_id)
        u.intentos_fallidos, u.bloqueado_hasta = 0, None


class _Ambientes:
    def __init__(self, datos):
        self.d = datos

    def de_usuario(self, usuario_id):
        return copy.deepcopy(self.d["ambientes"].get(usuario_id, []))

    def empresas(self):
        return [{"co_empresa": c, "nombre": n, "activa": True} for c, n in sorted(self.d["empresas"].items())]

    def existe_empresa(self, co_empresa):
        return co_empresa in self.d["empresas"]

    def guardar(self, usuario_id, co_empresa, almacen, permisos):
        lista = [a for a in self.d["ambientes"].get(usuario_id, []) if a.co_empresa != co_empresa]
        lista.append(Ambiente(co_empresa, self.d["empresas"][co_empresa], almacen, permisos))
        self.d["ambientes"][usuario_id] = lista

    def quitar(self, usuario_id, co_empresa):
        antes = self.d["ambientes"].get(usuario_id, [])
        self.d["ambientes"][usuario_id] = [a for a in antes if a.co_empresa != co_empresa]
        return len(antes) - len(self.d["ambientes"][usuario_id])


class _Sesiones:
    def __init__(self, datos):
        self.d = datos

    def crear(self, token_hash, usuario_id, creada, expira, dispositivo, ip):
        self.d["sesiones"][token_hash] = {"usuario_id": usuario_id, "expira": expira, "ultimo_uso": creada,
                                          "revocada": False, "dispositivo": dispositivo}

    def buscar(self, token_hash):
        s = self.d["sesiones"].get(token_hash)
        return dict(s) if s else None

    def anotar_uso(self, token_hash, fecha):
        self.d["sesiones"][token_hash]["ultimo_uso"] = fecha

    def revocar(self, token_hash):
        self.d["sesiones"][token_hash]["revocada"] = True

    def revocar_de_usuario(self, usuario_id):
        for s in self.d["sesiones"].values():
            if s["usuario_id"] == usuario_id:
                s["revocada"] = True


class _Bitacora:
    def __init__(self, datos):
        self.d = datos

    def registrar(self, accion, usuario_id=None, usuario="", co_empresa="", detalle="", ip=""):
        self.d["bitacora"].append((accion, usuario_id, usuario, co_empresa, detalle))

    def buscar(self, usuario, co_empresa, accion, desde, hasta, pagina, tamano):
        filas = [{"id": i + 1, "fecha": None, "usuario": b[2], "co_empresa": b[3] or "", "accion": b[0],
                  "detalle": b[4] or "", "ip": ""} for i, b in enumerate(self.d["bitacora"])]
        filas = [f for f in reversed(filas) if (not usuario or f["usuario"] == usuario)
                 and (not co_empresa or f["co_empresa"] == co_empresa) and (not accion or f["accion"] == accion)]
        desde_fila = (pagina - 1) * tamano
        return filas[desde_fila:desde_fila + tamano], len(filas) > desde_fila + tamano


# ---------------------------------------------------------------------------
# Base SIGUEC_PURACERAMICA: copia de artículos, inventarios y conteos
# ---------------------------------------------------------------------------

class _Articulos:
    """articulos, articulo_codigos y sincronizaciones en memoria."""

    def __init__(self, datos):
        self.d = datos

    def reemplazar(self, co_empresa, articulos, codigos):
        copia = self.d["articulos"].setdefault(co_empresa, {})
        leidos = {a[0] for a in articulos}
        nuevos = actualizados = retirados = 0
        for co_art, art_des, anulado in articulos:
            actual = copia.get(co_art)
            if actual is None:
                nuevos += 1
            elif (actual["art_des"], actual["anulado"], actual["en_profit"]) != (art_des, anulado, True):
                actualizados += 1
            else:
                continue
            copia[co_art] = {"co_art": co_art, "art_des": art_des, "anulado": anulado, "en_profit": True}
        for co_art, a in copia.items():
            if co_art not in leidos and a["en_profit"]:
                a["en_profit"] = False
                retirados += 1
        self.d["codigos"][co_empresa] = list(codigos)
        return nuevos, actualizados, retirados

    def total(self, co_empresa):
        return sum(1 for a in self.d["articulos"].get(co_empresa, {}).values() if a["en_profit"])

    def registrar_sincronizacion(self, co_empresa, usuario, leidos, nuevos, actualizados, retirados, codigos):
        self.d["sincronizaciones"].append({"co_empresa": co_empresa, "fecha": None, "usuario": usuario,
                                           "leidos": leidos, "nuevos": nuevos, "actualizados": actualizados,
                                           "retirados": retirados, "codigos": codigos})

    def ultima_sincronizacion(self, co_empresa):
        filas = [s for s in self.d["sincronizaciones"] if s["co_empresa"] == co_empresa]
        return dict(filas[-1]) if filas else None

    def buscar(self, co_empresa, texto, inventario_id, solo_contados, pagina, tamano):
        patron = ("*" + texto.replace("*", "*") + "*").upper() if texto else ""
        totales = _Inventarios(self.d).totales(inventario_id) if inventario_id else {}
        codigos = self.d["codigos"].get(co_empresa, [])
        filas = []
        for co_art in sorted(self.d["articulos"].get(co_empresa, {})):
            a = self.d["articulos"][co_empresa][co_art]
            if not a["en_profit"] or a["anulado"]:
                continue
            if patron and not (fnmatch.fnmatchcase(co_art.upper(), patron)
                               or fnmatch.fnmatchcase(a["art_des"].upper(), patron)
                               or any(c[1] == co_art and fnmatch.fnmatchcase(c[0].upper(), patron) for c in codigos)):
                continue
            if solo_contados and co_art not in totales:
                continue
            filas.append({"co_art": co_art, "art_des": a["art_des"], "contado": totales.get(co_art)})
        desde = (pagina - 1) * tamano
        return filas[desde:desde + tamano], len(filas) > desde + tamano

    def por_codigo(self, co_empresa, codigo):
        copia = self.d["articulos"].get(co_empresa, {})
        candidatos = [c for c in self.d["codigos"].get(co_empresa, [])
                      if c[0] == codigo and copia.get(c[1], {}).get("en_profit")]
        if not candidatos:
            return None
        c = min(candidatos, key=lambda c: (copia[c[1]]["anulado"], c[3], c[1]))
        return {"co_art": c[1], "art_des": copia[c[1]]["art_des"], "origen": c[2]}

    def descripciones(self, co_empresa):
        return {c: a["art_des"] for c, a in self.d["articulos"].get(co_empresa, {}).items()}


class _Inventarios:
    def __init__(self, datos):
        self.d = datos

    def _con_totales(self, i):
        conteos = [c for c in self.d["conteos"] if c["inventario_id"] == i["id"]]
        stock = self.d["stock"].get(i["id"], {})
        return dict(i, articulos_stock=sum(1 for s in stock.values() if s != 0),
                    articulos_contados=len({c["co_art"] for c in conteos}), conteos=len(conteos))

    def lista(self, co_empresa, estado=""):
        return [self._con_totales(i) for i in sorted(self.d["inventarios"], key=lambda i: (i["estado"], -i["id"]))
                if i["co_empresa"] == co_empresa and (not estado or i["estado"] == estado)]

    def por_id(self, inventario_id, co_empresa):
        i = next((i for i in self.d["inventarios"] if i["id"] == inventario_id and i["co_empresa"] == co_empresa),
                 None)
        return self._con_totales(i) if i else None

    def abierto_del_almacen(self, co_empresa, co_alma):
        return next((i["id"] for i in self.d["inventarios"] if i["co_empresa"] == co_empresa
                     and i["co_alma"] == co_alma and i["estado"] == "A"), None)

    def abiertos_por_almacen(self, co_empresa):
        return {i["co_alma"]: i["id"] for i in self.d["inventarios"]
                if i["co_empresa"] == co_empresa and i["estado"] == "A"}

    def crear(self, co_empresa, co_alma, des_alma, descripcion, usuario, stock):
        nuevo = {"id": len(self.d["inventarios"]) + 1, "co_empresa": co_empresa, "co_alma": co_alma,
                 "des_alma": des_alma, "descripcion": descripcion, "estado": "A", "creado_por": usuario,
                 "fecha_creacion": None, "cerrado_por": None, "fecha_cierre": None}
        self.d["inventarios"].append(nuevo)
        self.d["stock"][nuevo["id"]] = dict(stock)
        return nuevo["id"]

    def cerrar(self, inventario_id, usuario):
        for i in self.d["inventarios"]:
            if i["id"] == inventario_id and i["estado"] == "A":
                i["estado"], i["cerrado_por"] = "C", usuario
                return 1
        return 0

    def stock(self, inventario_id):
        return dict(self.d["stock"].get(inventario_id, {}))

    def anotar(self, inventario_id, co_art, codigo_leido, cantidad, usuario):
        nuevo_id = len(self.d["conteos"]) + 1
        self.d["conteos"].append({"id": nuevo_id, "inventario_id": inventario_id, "co_art": co_art,
                                  "codigo_leido": codigo_leido, "cantidad": cantidad, "usuario": usuario,
                                  "fecha": None})
        return nuevo_id

    def conteo(self, conteo_id):
        return next((dict(c) for c in self.d["conteos"] if c["id"] == conteo_id), None)

    def borrar(self, conteo_id, inventario_id):
        antes = len(self.d["conteos"])
        self.d["conteos"] = [c for c in self.d["conteos"]
                             if not (c["id"] == conteo_id and c["inventario_id"] == inventario_id)]
        return antes - len(self.d["conteos"])

    def contado(self, inventario_id, co_art, usuario):
        filas = [c for c in self.d["conteos"] if c["inventario_id"] == inventario_id and c["co_art"] == co_art]
        return (sum((c["cantidad"] for c in filas), Decimal(0)),
                sum((c["cantidad"] for c in filas if c["usuario"] == usuario), Decimal(0)), len(filas))

    def totales(self, inventario_id):
        totales = {}
        for c in self.d["conteos"]:
            if c["inventario_id"] == inventario_id:
                totales[c["co_art"]] = totales.get(c["co_art"], Decimal(0)) + c["cantidad"]
        return totales

    def ultimos(self, inventario_id, usuario="", cuantos=30):
        filas = [dict(c, art_des="") for c in reversed(self.d["conteos"])
                 if c["inventario_id"] == inventario_id and (not usuario or c["usuario"] == usuario)]
        return filas[:cuantos]

    def cuantos_de_usuario(self, inventario_id, usuario):
        return sum(1 for c in self.d["conteos"] if c["inventario_id"] == inventario_id and c["usuario"] == usuario)


class BaseAppFalsa:
    """La base SIGUEC_PURACERAMICA en memoria."""

    def __init__(self):
        self.datos = {"usuarios": {}, "ambientes": {}, "sesiones": {}, "bitacora": [],
                      "empresas": {EMPRESA: "Pura Cerámica", "PRUEBA": "Empresa PRUEBA"},
                      "articulos": {}, "codigos": {}, "sincronizaciones": [],
                      "inventarios": [], "stock": {}, "conteos": []}

    def agregar_usuario(self, codigo, clave, permisos: Permisos, activo=True, empresas=(EMPRESA,), admin=False):
        usuario_id = len(self.datos["usuarios"]) + 1
        self.datos["usuarios"][codigo] = Usuario(id=usuario_id, usuario=codigo, nombre=f"Nombre {codigo}",
                                                 clave_hash=cifrar_clave(clave, 1000), activo=activo,
                                                 es_administrador=admin)
        self.datos["ambientes"][usuario_id] = [Ambiente(e, self.datos["empresas"][e], "01", permisos)
                                               for e in empresas]
        return usuario_id

    @contextmanager
    def abrir(self, base=""):
        respaldo = copy.deepcopy(self.datos)
        unidad = type("UnidadAppFalsa", (), {})()
        unidad.usuarios = _Usuarios(self.datos)
        unidad.ambientes = _Ambientes(self.datos)
        unidad.sesiones = _Sesiones(self.datos)
        unidad.bitacora = _Bitacora(self.datos)
        unidad.articulos = _Articulos(self.datos)
        unidad.inventarios = _Inventarios(self.datos)
        try:
            yield unidad
        except BaseException:
            self.datos.clear()
            self.datos.update(respaldo)   # ROLLBACK
            raise


# ---------------------------------------------------------------------------
# Base de la empresa en Profit 2K12 (solo lectura)
# ---------------------------------------------------------------------------

class _ProfitFalso:
    def __init__(self, datos):
        self.d = datos

    def columnas(self, tabla):
        return set(self.d["columnas"].get(tabla, ()))

    def articulos(self, con_anulado, extras):
        filas = []
        for a in self.d["saArticulo"]:
            fila = {"co_art": a["co_art"], "art_des": a["art_des"], "anulado": a.get("anulado", 0) if con_anulado else 0}
            for c in extras:
                fila["x_" + c.lower()] = a.get(c)
            filas.append(fila)
        return filas

    def codigos_barra(self):
        return list(self.d["barras"])

    def almacenes(self):
        return list(self.d["saAlmacen"])

    def stock_almacen(self, co_alma):
        return {s["co_art"]: Decimal(str(s["stock"])) for s in self.d["saStockAlmacen"]
                if s["co_alma"] == co_alma and s["tipo"] == "ACT"}


class ProfitFalso:
    """Una empresa de Profit 2K12 con artículos, almacenes y stock."""

    def __init__(self):
        self.datos = {
            "columnas": {"saArticulo": {"co_art", "art_des", "anulado", "modelo", "ref"},
                         "saAlmacen": {"co_alma", "des_alma"}, "saStockAlmacen": {"co_alma", "co_art", "tipo", "stock"}},
            "saArticulo": [
                {"co_art": "CER-001", "art_des": "CERAMICA BLANCA 45X45", "anulado": 0,
                 "modelo": "7591111000017", "ref": "BL45"},
                {"co_art": "CER-002", "art_des": "CERAMICA GRIS 60X60", "anulado": 0, "modelo": None, "ref": "GR60"},
                {"co_art": "POR-003", "art_des": "PORCELANATO MADERA", "anulado": 0, "modelo": "7592222000025",
                 "ref": ""},
                {"co_art": "ANU-004", "art_des": "ARTICULO ANULADO", "anulado": 1, "modelo": None, "ref": None},
            ],
            "barras": [],
            "saAlmacen": [{"co_alma": "01", "des_alma": "PRINCIPAL"}, {"co_alma": "02", "des_alma": "DEPOSITO"}],
            "saStockAlmacen": [
                {"co_alma": "01", "co_art": "CER-001", "tipo": "ACT", "stock": "10"},
                {"co_alma": "01", "co_art": "CER-001", "tipo": "COM", "stock": "4"},
                {"co_alma": "01", "co_art": "CER-002", "tipo": "ACT", "stock": "5"},
                {"co_alma": "01", "co_art": "POR-003", "tipo": "ACT", "stock": "0"},
                {"co_alma": "02", "co_art": "CER-001", "tipo": "ACT", "stock": "7"},
            ],
        }
        self.bases_abiertas = []

    @contextmanager
    def abrir(self, base):
        self.bases_abiertas.append(base)
        unidad = type("UnidadProfitFalsa", (), {})()
        unidad.co_empresa = base
        unidad.profit = _ProfitFalso(self.datos)
        yield unidad


def servicios(app: BaseAppFalsa, profit: ProfitFalso, reloj=None):
    """Los seis servicios sobre las bases falsas (en el orden de crear_aplicacion_con)."""
    config = ConfigAplicacion()
    kwargs = {"reloj": reloj} if reloj else {}
    return (ServicioAutenticacion(app.abrir, config, **kwargs),
            ServicioArticulos(app.abrir, config),
            ServicioSincronizacion(profit.abrir, app.abrir, ConfigProfit()),
            ServicioInventarios(profit.abrir, app.abrir, config),
            ServicioConteos(app.abrir),
            ServicioAdministracion(app.abrir, **kwargs))
