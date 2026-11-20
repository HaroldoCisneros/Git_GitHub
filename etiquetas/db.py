"""Lectura de artículos desde la tabla de SQL Server (por defecto: art de Profit)."""

import re

from zpl import Producto

_IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?$")


def _col(nombre: str) -> str:
    """Valida un nombre de tabla/columna y lo devuelve entre corchetes."""
    if not _IDENT.match(nombre):
        raise ValueError(f"Nombre de tabla o columna no válido en la configuración: {nombre!r}")
    return ".".join(f"[{parte}]" for parte in nombre.split("."))


class Articulos:
    def __init__(self, cfg):
        self.sql = cfg["sql"]
        t = cfg["tabla"]
        self.tabla = _col(t.get("nombre", "art"))
        self.c_codigo = _col(t.get("codigo", "co_art"))
        self.c_desc = _col(t.get("descripcion", "art_des"))
        self.c_precio = _col(t.get("precio", "prec_vta1"))
        self.c_barras = _col(t.get("barras", "co_art"))
        ref = (t.get("referencia") or "").strip()
        self.c_ref = _col(ref) if ref else "''"

    def _conectar(self):
        import pymssql  # pip install pymssql

        return pymssql.connect(
            server=self.sql.get("servidor", "localhost"),
            port=str(self.sql.get("puerto") or "1433"),
            database=self.sql.get("base_datos"),
            user=self.sql.get("usuario"),
            password=self.sql.get("clave"),
            login_timeout=int(self.sql.get("timeout") or 5),
            charset="UTF-8",
        )

    def _select(self) -> str:
        return (f"SELECT {self.c_codigo}, {self.c_desc}, {self.c_precio}, {self.c_barras}, {self.c_ref} "
                f"FROM {self.tabla} ")

    @staticmethod
    def _producto(fila) -> Producto:
        codigo, desc, precio, barras, ref = fila
        return Producto(
            codigo=str(codigo or "").strip(),
            nombre=str(desc or "").strip(),
            precio=float(precio or 0),
            barras=str(barras or "").strip(),
            referencia=str(ref or "").strip(),
        )

    def buscar_codigos(self, codigos: list[str]) -> dict[str, Producto]:
        """Devuelve {codigo: Producto} para los códigos que existan."""
        codigos = [c.strip() for c in codigos if c and c.strip()]
        if not codigos:
            return {}
        marcas = ", ".join(["%s"] * len(codigos))
        with self._conectar() as cn, cn.cursor() as cur:
            cur.execute(self._select() + f"WHERE {self.c_codigo} IN ({marcas})", tuple(codigos))
            encontrados = [self._producto(f) for f in cur.fetchall()]
        # SQL Server ignora los espacios finales al comparar; se normaliza a mayúsculas
        return {p.codigo.upper(): p for p in encontrados}

    def buscar_descripcion(self, texto: str, limite: int = 100) -> list[Producto]:
        """Búsqueda por parte de la descripción o del código."""
        patron = f"%{texto.strip()}%"
        with self._conectar() as cn, cn.cursor() as cur:
            cur.execute(
                f"SELECT TOP {int(limite)} " + self._select()[len("SELECT "):]
                + f"WHERE {self.c_desc} LIKE %s OR {self.c_codigo} LIKE %s ORDER BY {self.c_desc}",
                (patron, patron),
            )
            return [self._producto(f) for f in cur.fetchall()]

    def probar(self) -> int:
        """Prueba la conexión y devuelve cuántos registros tiene la tabla."""
        with self._conectar() as cn, cn.cursor() as cur:
            cur.execute(self._select().replace("SELECT ", "SELECT TOP 1 ", 1))  # valida las columnas
            cur.fetchall()
            cur.execute(f"SELECT COUNT(*) FROM {self.tabla}")
            return cur.fetchone()[0]
