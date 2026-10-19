"""
Pruebas de punta a punta de la API (HTTP -> rutas -> servicios) con bases falsas.

Recorren el uso real: entrar, sincronizar artículos, preparar el inventario de
un almacén, contar escaneando (varias personas), consultar, comparar y cerrar.
"""

from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from api.aplicacion import crear_aplicacion_con
from api.modelos import Permisos
from tests.falsos import EMPRESA, BaseAppFalsa, ProfitFalso, servicios

BASE = "/api/v1"
E = f"{BASE}/empresas/{EMPRESA}"
TODO = Permisos(consultar_articulos=True, sincronizar_articulos=True, preparar_inventario=True,
                contar_inventario=True)
CONTADOR = Permisos(consultar_articulos=True, contar_inventario=True)
SOLO_CONSULTA = Permisos(consultar_articulos=True, contar_inventario=False)


class Reloj:
    """Hora que se puede adelantar en las pruebas."""

    def __init__(self):
        self.ahora = datetime(2026, 10, 19, 9, 0, 0)

    def __call__(self):
        return self.ahora


@pytest.fixture
def entorno():
    app_bd = BaseAppFalsa()
    app_bd.agregar_usuario("JEFE", "clave123", TODO, admin=True)
    app_bd.agregar_usuario("ANA", "clave123", CONTADOR)
    app_bd.agregar_usuario("LUIS", "clave123", CONTADOR)
    app_bd.agregar_usuario("MIRA", "clave123", SOLO_CONSULTA)
    app_bd.agregar_usuario("INACTIVO", "clave123", TODO, activo=False)
    profit = ProfitFalso()
    reloj = Reloj()
    cliente = TestClient(crear_aplicacion_con(*servicios(app_bd, profit, reloj)))
    return cliente, app_bd, profit, reloj


def entrar(cliente, usuario="JEFE", clave="clave123"):
    r = cliente.post(f"{BASE}/sesion", json={"usuario": usuario, "clave": clave, "dispositivo": "Zebra TC21"})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['token']}"}


def preparar(cliente, jefe, co_alma="01"):
    """Sincroniza y prepara el inventario del almacén. Devuelve su id."""
    assert cliente.post(f"{E}/articulos/sincronizar", headers=jefe).status_code == 200
    r = cliente.post(f"{E}/inventarios", headers=jefe, json={"co_alma": co_alma})
    assert r.status_code == 200, r.text
    return r.json()["id"]


# ---------------------------------------------------------------------------
# Sesión
# ---------------------------------------------------------------------------

def test_salud(entorno):
    cliente, *_ = entorno
    r = cliente.get(f"{BASE}/salud")
    assert r.json()["aplicacion"] == "AppSiguecPuraceramica"


def test_entrar_devuelve_permisos_de_inventario(entorno):
    cliente, *_ = entorno
    r = cliente.post(f"{BASE}/sesion", json={"usuario": "ana", "clave": "clave123"})
    assert r.status_code == 200
    datos = r.json()
    assert datos["token"]
    ambiente = datos["ambientes"][0]
    assert ambiente["co_empresa"] == EMPRESA and ambiente["almacen"] == "01"
    assert ambiente["permisos"] == {"consultar_articulos": True, "sincronizar_articulos": False,
                                    "preparar_inventario": False, "contar_inventario": True}


def test_clave_errada_e_inactivo(entorno):
    cliente, *_ = entorno
    assert cliente.post(f"{BASE}/sesion", json={"usuario": "ANA", "clave": "mala"}).status_code == 401
    assert cliente.post(f"{BASE}/sesion", json={"usuario": "NADIE", "clave": "x"}).json()["detalle"] == \
        "Usuario o clave incorrectos."
    assert cliente.post(f"{BASE}/sesion", json={"usuario": "INACTIVO", "clave": "clave123"}).status_code == 401


def test_bloqueo_por_claves_erradas(entorno):
    cliente, _, _, reloj = entorno
    for _ in range(5):
        cliente.post(f"{BASE}/sesion", json={"usuario": "ANA", "clave": "mala"})
    r = cliente.post(f"{BASE}/sesion", json={"usuario": "ANA", "clave": "clave123"})
    assert r.status_code == 401 and "bloqueado" in r.json()["detalle"]
    reloj.ahora += timedelta(minutes=16)
    entrar(cliente, "ANA")


def test_sin_token_y_salir(entorno):
    cliente, *_ = entorno
    assert cliente.get(f"{E}/inventarios").status_code == 401
    jefe = entrar(cliente)
    assert cliente.get(f"{BASE}/sesion", headers=jefe).status_code == 200
    assert cliente.delete(f"{BASE}/sesion", headers=jefe).status_code == 204
    assert cliente.get(f"{BASE}/sesion", headers=jefe).status_code == 401


# ---------------------------------------------------------------------------
# Sincronizar y consultar artículos
# ---------------------------------------------------------------------------

def test_sincronizar_copia_los_articulos(entorno):
    cliente, app_bd, *_ = entorno
    jefe = entrar(cliente)
    assert cliente.get(f"{E}/articulos/sincronizacion", headers=jefe).json() == {"sincronizacion": None}
    r = cliente.post(f"{E}/articulos/sincronizar", headers=jefe)
    assert r.status_code == 200, r.text
    datos = r.json()
    assert (datos["leidos"], datos["nuevos"], datos["actualizados"], datos["retirados"]) == (4, 4, 0, 0)
    # co_art de los 4 + modelo de 2 + ref de 2 (las vacías no cuentan)
    assert datos["codigos"] == 8
    assert datos["total"] == 4
    assert app_bd.datos["bitacora"][-1][0] == "SINCRONIZAR"
    assert cliente.get(f"{E}/articulos/sincronizacion", headers=jefe).json()["sincronizacion"]["usuario"] == "JEFE"


def test_sincronizar_otra_vez_actualiza_y_retira(entorno):
    cliente, _, profit, _ = entorno
    jefe = entrar(cliente)
    cliente.post(f"{E}/articulos/sincronizar", headers=jefe)
    profit.datos["saArticulo"][0]["art_des"] = "CERAMICA BLANCA 45X45 BRILLANTE"
    del profit.datos["saArticulo"][1]
    datos = cliente.post(f"{E}/articulos/sincronizar", headers=jefe).json()
    assert (datos["nuevos"], datos["actualizados"], datos["retirados"], datos["total"]) == (0, 1, 1, 3)


def test_sincronizar_sin_permiso(entorno):
    cliente, *_ = entorno
    r = cliente.post(f"{E}/articulos/sincronizar", headers=entrar(cliente, "ANA"))
    assert r.status_code == 403


def test_sincronizar_tabla_inexistente_avisa(entorno):
    cliente, _, profit, _ = entorno
    profit.datos["columnas"]["saArticulo"] = set()
    r = cliente.post(f"{E}/articulos/sincronizar", headers=entrar(cliente))
    assert r.status_code == 422 and "saArticulo" in r.json()["detalle"]


def test_consultar_solo_codigo_descripcion_y_contado(entorno):
    cliente, *_ = entorno
    jefe = entrar(cliente)
    cliente.post(f"{E}/articulos/sincronizar", headers=jefe)
    r = cliente.get(f"{E}/articulos", headers=entrar(cliente, "MIRA"), params={"texto": "ceramica"})
    assert r.status_code == 200
    articulos = r.json()["articulos"]
    assert [a["co_art"] for a in articulos] == ["CER-001", "CER-002"]
    assert set(articulos[0]) == {"co_art", "art_des", "contado"}
    assert articulos[0]["contado"] is None
    # Los anulados no se muestran; se busca también por referencia o código de barras
    todos = cliente.get(f"{E}/articulos", headers=jefe).json()["articulos"]
    assert "ANU-004" not in [a["co_art"] for a in todos]
    assert cliente.get(f"{E}/articulos", headers=jefe, params={"texto": "GR60"}).json()["articulos"][0]["co_art"] == \
        "CER-002"


def test_por_codigo_de_barras(entorno):
    cliente, *_ = entorno
    jefe = entrar(cliente)
    cliente.post(f"{E}/articulos/sincronizar", headers=jefe)
    r = cliente.get(f"{E}/articulos/por-codigo", headers=jefe, params={"codigo": "7592222000025"})
    assert r.json() == {"co_art": "POR-003", "art_des": "PORCELANATO MADERA", "contado": None}
    r = cliente.get(f"{E}/articulos/por-codigo", headers=jefe, params={"codigo": "NO-EXISTE"})
    assert r.status_code == 404 and "sincronice" in r.json()["detalle"]


# ---------------------------------------------------------------------------
# Preparar inventario por almacén
# ---------------------------------------------------------------------------

def test_almacenes_y_preparar_toma_la_foto_del_stock(entorno):
    cliente, app_bd, *_ = entorno
    jefe = entrar(cliente)
    almacenes = cliente.get(f"{E}/almacenes", headers=jefe).json()["almacenes"]
    assert [a["co_alma"] for a in almacenes] == ["01", "02"] and almacenes[0]["inventario_abierto"] is None
    inventario_id = preparar(cliente, jefe)
    inventario = cliente.get(f"{E}/inventarios/{inventario_id}", headers=jefe).json()["inventario"]
    assert inventario["abierto"] and inventario["co_alma"] == "01" and inventario["des_alma"] == "PRINCIPAL"
    assert inventario["descripcion"] == "Inventario PRINCIPAL"
    # Solo el stock "ACT" (actual) del almacén 01; el COM no cuenta
    assert app_bd.datos["stock"][inventario_id] == {"CER-001": 10, "CER-002": 5, "POR-003": 0}
    assert inventario["articulos_stock"] == 2
    assert cliente.get(f"{E}/almacenes", headers=jefe).json()["almacenes"][0]["inventario_abierto"] == inventario_id
    assert app_bd.datos["bitacora"][-1][0] == "INVENTARIO_ABRIR"


def test_un_solo_inventario_abierto_por_almacen(entorno):
    cliente, *_ = entorno
    jefe = entrar(cliente)
    preparar(cliente, jefe)
    r = cliente.post(f"{E}/inventarios", headers=jefe, json={"co_alma": "01"})
    assert r.status_code == 409
    assert cliente.post(f"{E}/inventarios", headers=jefe, json={"co_alma": "02"}).status_code == 200
    assert cliente.post(f"{E}/inventarios", headers=jefe, json={"co_alma": "99"}).status_code == 422


def test_preparar_sin_permiso(entorno):
    cliente, *_ = entorno
    r = cliente.post(f"{E}/inventarios", headers=entrar(cliente, "ANA"), json={"co_alma": "01"})
    assert r.status_code == 403


# ---------------------------------------------------------------------------
# Contar (individual, escaneando)
# ---------------------------------------------------------------------------

def test_conteos_individuales_se_suman(entorno):
    cliente, *_ = entorno
    inventario_id = preparar(cliente, entrar(cliente))
    ana, luis = entrar(cliente, "ANA"), entrar(cliente, "LUIS")
    url = f"{E}/inventarios/{inventario_id}/conteos"

    r = cliente.post(url, headers=ana, json={"codigo": "7591111000017", "cantidad": "6"})
    assert r.status_code == 200, r.text
    assert r.json()["conteo"]["co_art"] == "CER-001" and r.json()["conteo"]["usuario"] == "ANA"
    assert (r.json()["contado_usuario"], r.json()["contado_total"]) == ("6", "6")

    r = cliente.post(url, headers=luis, json={"codigo": "CER-001", "cantidad": "2,5"})
    assert (r.json()["contado_usuario"], r.json()["contado_total"]) == ("2.5", "8.5")

    # Cada quien ve sus propios conteos
    mios = cliente.get(f"{E}/inventarios/{inventario_id}", headers=ana).json()
    assert mios["contado_por_mi"] == 1 and mios["mis_conteos"][0]["cantidad"] == "6"

    # La consulta muestra el stock contado del inventario elegido
    r = cliente.get(f"{E}/articulos", headers=ana, params={"inventario_id": inventario_id, "solo_contados": True})
    assert r.json()["articulos"] == [{"co_art": "CER-001", "art_des": "CERAMICA BLANCA 45X45", "contado": "8.5"}]


def test_contar_codigo_desconocido_o_cantidad_mala(entorno):
    cliente, *_ = entorno
    inventario_id = preparar(cliente, entrar(cliente))
    ana = entrar(cliente, "ANA")
    url = f"{E}/inventarios/{inventario_id}/conteos"
    assert cliente.post(url, headers=ana, json={"codigo": "XYZ", "cantidad": "1"}).status_code == 404
    assert cliente.post(url, headers=ana, json={"codigo": "CER-001", "cantidad": "abc"}).status_code == 422
    assert cliente.post(url, headers=ana, json={"codigo": "CER-001", "cantidad": "2000000"}).status_code == 422
    # Quien solo consulta no puede contar
    assert cliente.post(url, headers=entrar(cliente, "MIRA"), json={"codigo": "CER-001", "cantidad": "1"}) \
        .status_code == 403


def test_borrar_solo_lo_propio(entorno):
    cliente, app_bd, *_ = entorno
    jefe = entrar(cliente)
    inventario_id = preparar(cliente, jefe)
    ana, luis = entrar(cliente, "ANA"), entrar(cliente, "LUIS")
    url = f"{E}/inventarios/{inventario_id}/conteos"
    conteo_id = cliente.post(url, headers=ana, json={"codigo": "BL45", "cantidad": "3"}).json()["conteo"]["id"]
    assert cliente.delete(f"{url}/{conteo_id}", headers=luis).status_code == 403
    assert cliente.delete(f"{url}/{conteo_id}", headers=ana).status_code == 204
    conteo_id = cliente.post(url, headers=ana, json={"codigo": "BL45", "cantidad": "4"}).json()["conteo"]["id"]
    # Quien prepara puede borrar el de otro (queda en la bitácora)
    assert cliente.delete(f"{url}/{conteo_id}", headers=jefe).status_code == 204
    assert app_bd.datos["bitacora"][-1][0] == "CONTEO_BORRAR"
    # Ver los de todos: solo quien prepara
    assert cliente.get(url, headers=ana, params={"de_todos": True}).status_code == 403
    assert cliente.get(url, headers=jefe, params={"de_todos": True}).json() == {"conteos": []}


# ---------------------------------------------------------------------------
# Comparar y cerrar
# ---------------------------------------------------------------------------

def test_comparacion_y_cierre(entorno):
    cliente, *_ = entorno
    jefe = entrar(cliente)
    inventario_id = preparar(cliente, jefe)
    ana = entrar(cliente, "ANA")
    url = f"{E}/inventarios/{inventario_id}/conteos"
    cliente.post(url, headers=ana, json={"codigo": "CER-001", "cantidad": "12"})     # sobran 2
    cliente.post(url, headers=ana, json={"codigo": "POR-003", "cantidad": "0"})      # cuadra (0 = no hay)

    assert cliente.get(f"{E}/inventarios/{inventario_id}/comparacion", headers=ana).status_code == 403
    r = cliente.get(f"{E}/inventarios/{inventario_id}/comparacion", headers=jefe).json()
    assert (r["articulos_contados"], r["con_diferencia"], r["sobrantes"], r["faltantes"], r["no_contados"]) == \
        (2, 1, 1, 0, 1)
    # CER-002 tiene 5 y nadie lo contó: falta 5 (la más grande va primero)
    assert [(d["co_art"], d["diferencia"], d["contado_si"]) for d in r["diferencias"]] == \
        [("CER-002", "-5", False), ("CER-001", "2", True)]

    cerrado = cliente.post(f"{E}/inventarios/{inventario_id}/cerrar", headers=jefe).json()
    assert not cerrado["abierto"] and cerrado["cerrado_por"] == "JEFE"
    assert cliente.post(url, headers=ana, json={"codigo": "CER-001", "cantidad": "1"}).status_code == 409
    assert cliente.post(f"{E}/inventarios/{inventario_id}/cerrar", headers=jefe).status_code == 409
    # Ya cerrado se puede preparar otro en el mismo almacén
    assert cliente.post(f"{E}/inventarios", headers=jefe, json={"co_alma": "01"}).status_code == 200
    assert len(cliente.get(f"{E}/inventarios", headers=ana, params={"solo_abiertos": True}).json()["inventarios"]) == 1


def test_otra_empresa_no_se_puede_usar(entorno):
    cliente, *_ = entorno
    r = cliente.get(f"{BASE}/empresas/PRUEBA/inventarios", headers=entrar(cliente, "ANA"))
    assert r.status_code == 403


# ---------------------------------------------------------------------------
# Administración (misma lógica de AppSighecMovil)
# ---------------------------------------------------------------------------

def test_admin_crea_usuario_y_le_da_permisos(entorno):
    cliente, *_ = entorno
    jefe = entrar(cliente)
    assert cliente.get(f"{BASE}/admin/usuarios", headers=entrar(cliente, "ANA")).status_code == 403
    r = cliente.post(f"{BASE}/admin/usuarios", headers=jefe,
                     json={"usuario": "pedro", "nombre": "Pedro Pérez", "clave": "secreta1"})
    assert r.status_code == 200, r.text
    nuevo = r.json()
    r = cliente.put(f"{BASE}/admin/usuarios/{nuevo['id']}/ambientes/{EMPRESA}", headers=jefe,
                    json={"almacen": "02", "permisos": {"contar_inventario": True, "consultar_articulos": True}})
    assert r.status_code == 200, r.text
    ambiente = r.json()["ambientes"][0]
    assert ambiente["almacen"] == "02" and ambiente["permisos"]["contar_inventario"]
    assert not ambiente["permisos"]["preparar_inventario"]
    r = cliente.put(f"{BASE}/admin/usuarios/{nuevo['id']}/ambientes/{EMPRESA}", headers=jefe,
                    json={"permisos": {"ver_ventas": True}})
    assert r.status_code == 422
    entrar(cliente, "PEDRO", "secreta1")
