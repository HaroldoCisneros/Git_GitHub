"""
Repositorio de la COPIA de los artículos (SIGUEC_PURACERAMICA.dbo.articulos
y articulo_codigos) que se usa para la toma de inventario.

    reemplazar()      Sincronización: deja la copia igual a lo leído de Profit.
    buscar()          Consulta: código, descripción y stock contado (si lo hay).
    por_codigo()      Lo escaneado -> artículo (co_art, modelo, ref o código de barra).

Se cuenta contra esta copia (y no directo contra Profit) para que escanear
sea rápido y no le pese a Profit mientras varias personas cuentan.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional, Sequence, Tuple

from ..base_datos import Conexion

# ---------- Sincronización ----------
# Se cargan los artículos leídos en una tabla temporal (#sinc) y con ella se
# actualiza la copia en tres pasos: nuevos, cambiados y los que ya no están.
SQL_CREAR_TEMPORAL = (
    "IF OBJECT_ID('tempdb..#sinc') IS NOT NULL DROP TABLE #sinc; "
    "CREATE TABLE #sinc (co_art varchar(30) COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY, "
    "art_des varchar(150) COLLATE DATABASE_DEFAULT NOT NULL, anulado bit NOT NULL)"
)
SQL_LLENAR_TEMPORAL = "INSERT INTO #sinc (co_art, art_des, anulado) VALUES (?, ?, ?)"
SQL_ACTUALIZAR = (
    "UPDATE a SET a.art_des = s.art_des, a.anulado = s.anulado, a.en_profit = 1, a.fecha_sync = GETDATE() "
    "FROM dbo.articulos a JOIN #sinc s ON s.co_art = a.co_art "
    "WHERE a.co_empresa = ? AND (a.art_des <> s.art_des OR a.anulado <> s.anulado OR a.en_profit = 0)"
)
SQL_INSERTAR = (
    "INSERT INTO dbo.articulos (co_empresa, co_art, art_des, anulado, en_profit, fecha_sync) "
    "SELECT ?, s.co_art, s.art_des, s.anulado, 1, GETDATE() FROM #sinc s "
    "WHERE NOT EXISTS (SELECT 1 FROM dbo.articulos a WHERE a.co_empresa = ? AND a.co_art = s.co_art)"
)
# No se borran (pueden tener conteos): quedan marcados como "ya no está en Profit".
SQL_RETIRAR = (
    "UPDATE a SET a.en_profit = 0, a.fecha_sync = GETDATE() FROM dbo.articulos a "
    "WHERE a.co_empresa = ? AND a.en_profit = 1 AND NOT EXISTS (SELECT 1 FROM #sinc s WHERE s.co_art = a.co_art)"
)
SQL_BORRAR_TEMPORAL = "DROP TABLE #sinc"
SQL_BORRAR_CODIGOS = "DELETE FROM dbo.articulo_codigos WHERE co_empresa = ?"
SQL_INSERTAR_CODIGO = (
    "INSERT INTO dbo.articulo_codigos (co_empresa, codigo, co_art, origen, prioridad) VALUES (?, ?, ?, ?, ?)"
)
SQL_TOTAL = "SELECT COUNT(*) AS total FROM dbo.articulos WHERE co_empresa = ? AND en_profit = 1"

SQL_REGISTRAR_SINC = (
    "INSERT INTO dbo.sincronizaciones (co_empresa, usuario, leidos, nuevos, actualizados, retirados, codigos) "
    "VALUES (?, ?, ?, ?, ?, ?, ?)"
)
SQL_ULTIMA_SINC = (
    "SELECT TOP 1 fecha, usuario, leidos, nuevos, actualizados, retirados, codigos "
    "FROM dbo.sincronizaciones WHERE co_empresa = ? ORDER BY id DESC"
)

# ---------- Consulta ----------
# texto: código o descripción ("*" = comodín). El stock contado sale de los conteos
# del inventario elegido (@inv = 0: sin inventario, contado NULL).
SQL_BUSCAR = (
    "SET NOCOUNT ON; DECLARE @e varchar(30) = ?, @t varchar(200) = ?, @inv int = ?, @solo bit = ?; "
    "SELECT TOP (?) * FROM ("
    " SELECT ROW_NUMBER() OVER (ORDER BY a.co_art) AS fila, a.co_art, a.art_des, c.contado "
    " FROM dbo.articulos a "
    " LEFT JOIN (SELECT co_art, SUM(cantidad) AS contado FROM dbo.inventario_conteos "
    "            WHERE inventario_id = @inv GROUP BY co_art) c ON c.co_art = a.co_art "
    " WHERE a.co_empresa = @e AND a.en_profit = 1 AND a.anulado = 0 "
    " AND (@t = '' OR a.co_art LIKE @t OR a.art_des LIKE @t "
    "      OR EXISTS (SELECT 1 FROM dbo.articulo_codigos k WHERE k.co_empresa = @e AND k.co_art = a.co_art "
    "                 AND k.codigo LIKE @t)) "
    " AND (@solo = 0 OR c.contado IS NOT NULL)"
    ") AS t WHERE t.fila > ? ORDER BY t.fila"
)

SQL_POR_CODIGO = (
    "SELECT TOP 1 a.co_art, a.art_des, k.origen FROM dbo.articulo_codigos k "
    "JOIN dbo.articulos a ON a.co_empresa = k.co_empresa AND a.co_art = k.co_art "
    "WHERE k.co_empresa = ? AND k.codigo = ? AND a.en_profit = 1 "
    "ORDER BY a.anulado, k.prioridad, a.co_art"
)

SQL_DESCRIPCIONES = "SELECT co_art, art_des FROM dbo.articulos WHERE co_empresa = ?"


def patron_like(texto: str) -> str:
    """
    Lo escrito -> patrón de LIKE: "tubo*pvc" -> "%tubo%pvc%". Se escapan [ % _
    para que se busquen como letras (no como comodines de SQL Server).
    """
    texto = (texto or "").strip()
    if not texto:
        return ""
    for especial in ("[", "%", "_"):
        texto = texto.replace(especial, f"[{especial}]")
    return "%" + texto.replace("*", "%") + "%"


class RepositorioArticulos:

    def __init__(self, cx: Conexion):
        self._cx = cx

    # ------------------------------------------------------------------
    def reemplazar(self, co_empresa: str, articulos: Sequence[Tuple[str, str, bool]],
                   codigos: Sequence[Tuple[str, str, str, int]]) -> Tuple[int, int, int]:
        """
        Deja la copia igual a lo leído de Profit (en la misma transacción).

        :param articulos: (co_art, art_des, anulado)
        :param codigos: (codigo, co_art, origen, prioridad)
        :return: (nuevos, actualizados, retirados)
        """
        self._cx.ejecutar(SQL_CREAR_TEMPORAL)
        self._cx.ejecutar_muchos(SQL_LLENAR_TEMPORAL, articulos)
        actualizados = self._cx.ejecutar(SQL_ACTUALIZAR, (co_empresa,))
        nuevos = self._cx.ejecutar(SQL_INSERTAR, (co_empresa, co_empresa))
        retirados = self._cx.ejecutar(SQL_RETIRAR, (co_empresa,))
        self._cx.ejecutar(SQL_BORRAR_TEMPORAL)
        self._cx.ejecutar(SQL_BORRAR_CODIGOS, (co_empresa,))
        self._cx.ejecutar_muchos(SQL_INSERTAR_CODIGO, [(co_empresa,) + tuple(c) for c in codigos])
        return nuevos, actualizados, retirados

    def total(self, co_empresa: str) -> int:
        fila = self._cx.consultar_uno(SQL_TOTAL, (co_empresa,))
        return int(fila["total"]) if fila else 0

    def registrar_sincronizacion(self, co_empresa: str, usuario: str, leidos: int, nuevos: int,
                                 actualizados: int, retirados: int, codigos: int) -> None:
        self._cx.ejecutar(SQL_REGISTRAR_SINC, (co_empresa, usuario, leidos, nuevos, actualizados, retirados, codigos))

    def ultima_sincronizacion(self, co_empresa: str) -> Optional[Dict[str, Any]]:
        return self._cx.consultar_uno(SQL_ULTIMA_SINC, (co_empresa,))

    # ------------------------------------------------------------------
    def buscar(self, co_empresa: str, texto: str, inventario_id: int, solo_contados: bool,
               pagina: int, tamano: int) -> Tuple[List[Dict[str, Any]], bool]:
        """:return: (filas de la página, hay_mas)"""
        filas = self._cx.consultar(SQL_BUSCAR, (co_empresa, patron_like(texto), inventario_id or 0,
                                                bool(solo_contados), tamano + 1, (pagina - 1) * tamano))
        return filas[:tamano], len(filas) > tamano

    def por_codigo(self, co_empresa: str, codigo: str) -> Optional[Dict[str, Any]]:
        """El artículo de lo escaneado (o None si ningún artículo tiene ese código)."""
        return self._cx.consultar_uno(SQL_POR_CODIGO, (co_empresa, codigo))

    def descripciones(self, co_empresa: str) -> Dict[str, str]:
        """{co_art: art_des} de toda la copia (para armar las diferencias)."""
        return {(f["co_art"] or "").strip(): (f["art_des"] or "").strip()
                for f in self._cx.consultar(SQL_DESCRIPCIONES, (co_empresa,))}
