"""
Pruebas de la lógica que no toca la base: preparar la copia de artículos,
comparar contado vs. stock, cantidades, el SQL armado para Profit 2K12 y el
config.xml.
"""

from decimal import Decimal

import pytest

from api.configuracion import ConfigProfit, ErrorConfiguracion, leer_configuracion
from api.errores import ErrorValidacion
from api.repositorios.articulos import patron_like
from api.repositorios.profit import sql_almacenes, sql_articulos, sql_stock_almacen
from api.servicios.conteos import validar_cantidad
from api.servicios.inventarios import comparar
from api.servicios.sincronizacion import preparar_copia


# ---------------------------------------------------------------------------
# Sincronización
# ---------------------------------------------------------------------------

def test_preparar_copia_limpia_y_arma_codigos():
    filas = [
        {"co_art": "A1  ", "art_des": "UNO ", "anulado": 0, "x_modelo": "759001", "x_ref": "R1"},
        {"co_art": "A1", "art_des": "REPETIDO", "anulado": 0, "x_modelo": None, "x_ref": None},
        {"co_art": "", "art_des": "SIN CODIGO", "anulado": 0},
        {"co_art": "B2", "art_des": "", "anulado": "1", "x_modelo": "B2", "x_ref": " "},
    ]
    barras = [{"codigo": "759999", "co_art": "B2"}, {"codigo": "111", "co_art": "NO-EXISTE"}]
    articulos, codigos = preparar_copia(filas, ["modelo", "ref"], barras)
    assert articulos == [("A1", "UNO", False), ("B2", "B2", True)]
    # B2 como modelo repite el co_art: queda solo con prioridad 0
    assert sorted(codigos) == sorted([("A1", "A1", "co_art", 0), ("759001", "A1", "modelo", 1),
                                      ("R1", "A1", "ref", 2), ("B2", "B2", "co_art", 0),
                                      ("759999", "B2", "barra", 3)])


# ---------------------------------------------------------------------------
# Comparación
# ---------------------------------------------------------------------------

def test_comparar_ordena_por_diferencia():
    stock = {"A": Decimal("10"), "B": Decimal("3"), "C": Decimal("0")}
    contados = {"A": Decimal("10"), "B": Decimal("1"), "D": Decimal("4")}
    filas = comparar(stock, contados, {"A": "UNO"}, incluir_no_contados=True, solo_diferencias=True)
    assert [(f.co_art, f.diferencia, f.contado_si) for f in filas] == [("D", "4", True), ("B", "-2", True)]
    todas = comparar(stock, contados, {}, incluir_no_contados=False, solo_diferencias=False)
    assert {f.co_art for f in todas} == {"A", "B", "D"}


@pytest.mark.parametrize("texto,valor", [("3", "3"), ("2,5", "2.5"), ("-1", "-1"), ("0", "0"), (" 1.25 ", "1.25")])
def test_validar_cantidad(texto, valor):
    assert validar_cantidad(texto) == Decimal(valor)


@pytest.mark.parametrize("texto", ["", "abc", "1e9", "NaN", "0.000001"])
def test_cantidad_no_valida(texto):
    with pytest.raises(ErrorValidacion):
        validar_cantidad(texto)


# ---------------------------------------------------------------------------
# SQL de Profit 2K12 y búsqueda
# ---------------------------------------------------------------------------

def test_sql_profit_2k12():
    cfg = ConfigProfit()
    assert sql_articulos(cfg, True, ["modelo"]) == (
        "SELECT RTRIM([co_art]) AS co_art, RTRIM([art_des]) AS art_des, [anulado] AS anulado, "
        "RTRIM(CAST([modelo] AS varchar(60))) AS [x_modelo] FROM dbo.[saArticulo]")
    assert "0 AS anulado" in sql_articulos(cfg, False, [])
    assert sql_stock_almacen(cfg) == (
        "SELECT RTRIM([co_art]) AS co_art, SUM([stock]) AS stock FROM dbo.[saStockAlmacen] "
        "WHERE [co_alma] = ? AND [tipo] = ? GROUP BY [co_art]")
    sin_tipo = ConfigProfit(col_stock_tipo="")
    assert "[tipo]" not in sql_stock_almacen(sin_tipo)
    assert sql_almacenes(cfg).startswith("SELECT RTRIM([co_alma]) AS co_alma, RTRIM([des_alma]) AS des_alma")


def test_patron_like():
    assert patron_like("") == ""
    assert patron_like("tubo*pvc") == "%tubo%pvc%"
    assert patron_like("50%_[a]") == "%50[%][_][[]a]%"


# ---------------------------------------------------------------------------
# config.xml
# ---------------------------------------------------------------------------

def test_config_ejemplo_se_lee(tmp_path):
    import os
    ruta = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config.ejemplo.xml")
    config = leer_configuracion(ruta)
    assert config.aplicacion.base_usuarios == "SIGUEC_PURACERAMICA"
    assert config.profit.tabla_articulos == "saArticulo"
    assert config.profit.columnas_codigo == ("modelo", "ref")
    assert config.profit.tabla_codigos_barra == ""
    assert config.profit.tipo_stock_actual == "ACT"


def test_config_rechaza_nombres_peligrosos(tmp_path):
    archivo = tmp_path / "config.xml"
    archivo.write_text("<configuracion><sqlserver><servidor>x</servidor></sqlserver>"
                       "<profit><tabla_articulos>saArticulo; DROP TABLE x</tabla_articulos></profit>"
                       "</configuracion>", encoding="utf-8")
    with pytest.raises(ErrorConfiguracion):
        leer_configuracion(str(archivo))
