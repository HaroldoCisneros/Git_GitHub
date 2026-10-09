"""
Pruebas de la lógica que no necesita SQL Server ni pantalla:
lectura del config.xml, formato de montos, cálculos de la factura y
validación del usuario (encriptación de Profit, con una base simulada).

Ejecutar desde la carpeta autoservicio:
    python -m unittest discover tests
"""

import os
import sys
import tempfile
import unittest
from decimal import Decimal

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.configuracion import (CLAVE_SALIDA_POR_DEFECTO, ErrorConfiguracion,  # noqa: E402
                               cargar_configuracion)
from app.encriptacion_profit import (clave_coincide, encriptar,  # noqa: E402
                                     texto_a_encriptar)
from app.modelos import Articulo, Caja, Factura, Usuario  # noqa: E402
from app.repositorios.usuarios import ErrorUsuario, RepositorioUsuarios  # noqa: E402
from app.utilidades import formatear_monto, solo_digitos  # noqa: E402

CARPETA = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


class PruebaConfiguracion(unittest.TestCase):

    def _escribir(self, contenido: str) -> str:
        """Guarda un XML temporal y devuelve su ruta."""
        archivo = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False, encoding="utf-8")
        archivo.write(contenido)
        archivo.close()
        self.addCleanup(os.remove, archivo.name)
        return archivo.name

    def test_lee_el_ejemplo(self):
        config = cargar_configuracion(os.path.join(CARPETA, "config.ejemplo.xml"))
        self.assertEqual(config.sql.servidor, "appsrv")
        self.assertEqual(config.sql.basedatos, "PRADO_25")
        self.assertFalse(config.sql.usa_autenticacion_windows)
        self.assertEqual(config.pantalla.segundos_producto, 20)
        self.assertEqual(config.caja.codigo, "01")
        self.assertEqual(config.seguridad.clave_salida, "9898989898")
        self.assertEqual(config.usuario.codigo, "CAJA01")
        self.assertEqual(config.usuario.base, "MasterProfit")
        self.assertEqual(config.usuario.tabla, "employee")
        # Las rutas sin carpeta se resuelven junto al programa.
        self.assertEqual(config.rutas.logo, os.path.join(CARPETA, "logo.png"))

    def test_xml_del_visor_sin_caja_da_error(self):
        ruta = self._escribir("<configuracion><sqlserver><servidor>s</servidor>"
                              "<basedatos>b</basedatos></sqlserver></configuracion>")
        with self.assertRaises(ErrorConfiguracion):
            cargar_configuracion(ruta)

    def test_seguridad_opcional_y_windows(self):
        ruta = self._escribir("<configuracion><sqlserver><servidor>s</servidor>"
                              "<basedatos>b</basedatos><usuario></usuario></sqlserver>"
                              "<caja><codigo> 02 </codigo></caja>"
                              "<usuario><codigo>ana</codigo><clave>x</clave></usuario>"
                              "</configuracion>")
        config = cargar_configuracion(ruta)
        self.assertEqual(config.usuario.base, "MasterProfit")   # Valor por defecto.
        self.assertTrue(config.sql.usa_autenticacion_windows)
        self.assertEqual(config.caja.codigo, "02")
        self.assertEqual(config.seguridad.clave_salida, CLAVE_SALIDA_POR_DEFECTO)

    def test_falta_usuario_da_error(self):
        ruta = self._escribir("<configuracion><sqlserver><servidor>s</servidor>"
                              "<basedatos>b</basedatos></sqlserver>"
                              "<caja><codigo>01</codigo></caja></configuracion>")
        with self.assertRaises(ErrorConfiguracion):
            cargar_configuracion(ruta)

    def test_tabla_con_caracteres_peligrosos_da_error(self):
        ruta = self._escribir("<configuracion><sqlserver><servidor>s</servidor>"
                              "<basedatos>b</basedatos></sqlserver>"
                              "<caja><codigo>01</codigo></caja>"
                              "<usuario><codigo>a</codigo><clave>x</clave>"
                              "<tabla>employee]; DROP TABLE x--</tabla></usuario>"
                              "</configuracion>")
        with self.assertRaises(ErrorConfiguracion):
            cargar_configuracion(ruta)

    def test_archivo_inexistente(self):
        with self.assertRaises(ErrorConfiguracion):
            cargar_configuracion(os.path.join(CARPETA, "no_existe.xml"))


class PruebaUtilidades(unittest.TestCase):

    def test_formato_monto(self):
        self.assertEqual(formatear_monto(1234567.5, 2, "$"), "$ 1.234.567,50")
        self.assertEqual(formatear_monto(Decimal("0.005"), 2, ""), "0,01")
        self.assertEqual(formatear_monto(1500, 0, "Bs", ",", "."), "Bs 1,500")

    def test_solo_digitos(self):
        self.assertEqual(solo_digitos("V-12.345.678"), "12345678")


class PruebaFactura(unittest.TestCase):

    def setUp(self):
        self.factura = Factura(caja=Caja("01", "Caja 1"), usuario=Usuario("u", "Usuario"))
        self.pan = Articulo("001", "Pan", Decimal("1.50"))
        self.leche = Articulo("002", "Leche", Decimal("10"), porcentaje_impuesto=Decimal("16"))

    def test_mismo_articulo_suma_cantidad(self):
        self.factura.agregar_articulo(self.pan)
        self.factura.agregar_articulo(self.pan)
        self.assertEqual(len(self.factura.lineas), 1)
        self.assertEqual(self.factura.lineas[0].cantidad, Decimal("2"))

    def test_totales(self):
        self.factura.agregar_articulo(self.pan)
        self.factura.agregar_articulo(self.leche)
        self.assertEqual(self.factura.subtotal, Decimal("11.50"))
        self.assertEqual(self.factura.impuesto, Decimal("1.60"))
        self.assertEqual(self.factura.total, Decimal("13.10"))

    def test_quitar_linea(self):
        self.factura.agregar_articulo(self.pan)
        self.factura.quitar_linea(0)
        self.assertTrue(self.factura.vacia)
        self.factura.quitar_linea(5)    # Índice inválido: no hace nada.


class PruebaEncriptacionProfit(unittest.TestCase):

    def test_formula_foxpro(self):
        # "A" = 65 -> ((65 + 17) * 11) % 255 = 137
        self.assertEqual(encriptar("A"), bytes([137]))

    def test_texto_a_encriptar(self):
        # Ejemplo de la documentación: clave "abc", prioridad 5, mapa "AB".
        self.assertEqual(texto_a_encriptar(" abc ", 5, "AB"), "ABC  5AB  ")

    def test_clave_coincide(self):
        guardada = encriptar(texto_a_encriptar("abc", 5, "AB  "))
        self.assertTrue(clave_coincide("ABC", guardada, 5, "AB  "))
        self.assertTrue(clave_coincide("abc", guardada, 5, "AB  "))   # No distingue mayúsculas.
        self.assertFalse(clave_coincide("abd", guardada, 5, "AB  "))
        self.assertFalse(clave_coincide("abc", guardada, 6, "AB  "))  # Otra prioridad.
        self.assertFalse(clave_coincide("", guardada, 5, "AB  "))

    def test_clave_larga_se_corta_a_15(self):
        guardada = encriptar(texto_a_encriptar("CLAVEMUYLARGA123", 10, "XY"))[:15]
        self.assertTrue(clave_coincide("clavemuylarga123", guardada, 10, "XY"))


class _BaseFalsa:
    """Imita BaseDatos: devuelve una fila fija y guarda la consulta recibida."""

    def __init__(self, fila):
        self.fila = fila
        self.sql = None
        self.parametros = None

    def consultar_uno(self, sql, parametros=()):
        self.sql, self.parametros = sql, parametros
        return self.fila


class PruebaUsuarios(unittest.TestCase):

    def _config(self, clave="secreta"):
        from app.configuracion import ConfigUsuario
        return ConfigUsuario(codigo="hcis", clave=clave)

    def _fila(self, **cambios):
        fila = {"employee_i": "HCIS", "last_name": "HAROLDO CISNEROS ",
                "clave": encriptar(texto_a_encriptar("secreta", 5, "AB  ")),
                "prioridad": 5, "mapa": "AB  ", "activo": 1, "estado": "A"}
        fila.update(cambios)
        return fila

    def test_usuario_valido(self):
        bd = _BaseFalsa(self._fila())
        usuario = RepositorioUsuarios(bd, self._config()).validar()
        self.assertEqual(usuario.codigo, "HCIS")
        self.assertEqual(usuario.nombre, "HAROLDO CISNEROS")
        self.assertEqual(bd.parametros, ("HCIS",))              # Código en mayúsculas.
        self.assertIn("[MasterProfit].[dbo].[employee]", bd.sql)

    def test_clave_incorrecta(self):
        with self.assertRaises(ErrorUsuario):
            RepositorioUsuarios(_BaseFalsa(self._fila()), self._config("otra")).validar()

    def test_usuario_inexistente(self):
        with self.assertRaises(ErrorUsuario):
            RepositorioUsuarios(_BaseFalsa(None), self._config()).validar()

    def test_usuario_inactivo(self):
        for cambios in ({"activo": 0}, {"estado": "I"}):
            with self.assertRaises(ErrorUsuario):
                RepositorioUsuarios(_BaseFalsa(self._fila(**cambios)), self._config()).validar()


if __name__ == "__main__":
    unittest.main()
