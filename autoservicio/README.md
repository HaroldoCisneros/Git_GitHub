# Aplicación de Autoservicio

Aplicación de escritorio táctil (Python + PySide6) para que el cliente escanee
sus artículos y genere su factura en **Profit Plus 2K8** (SQL Server).

## Instalación

```bash
pip install -r requirements.txt
copy config.ejemplo.xml config.xml     # y colocar los datos reales
python main.py                         # pantalla completa
python main.py --ventana               # en ventana, para pruebas
```

Requisitos en la caja: Python 3.9+ y el driver **ODBC Driver 17 for SQL Server**.
El usuario de SQL del XML necesita leer las tablas de la empresa y `MasterProfit.dbo.employee`.

## Configuración (`config.xml`)

Va en la **misma carpeta del ejecutable**. Es el mismo XML del Visor de
Precios con tres secciones nuevas:

| Sección | Etiqueta | Uso |
|---|---|---|
| `<caja>` | `<codigo>` | Código de la caja; se valida contra la tabla `cajas` de Profit. **Obligatorio.** |
| `<usuario>` | `<codigo>`, `<clave>` | Usuario y clave de Profit. Se validan al arrancar contra `MasterProfit.dbo.employee` (activo, Estado `A`, encriptación de Profit). **Obligatorio.** |
| `<usuario>` | `<base>`, `<tabla>` | Opcionales. Por defecto `MasterProfit` / `employee`. |
| `<seguridad>` | `<clave_salida>` | Contraseña para salir. Si no se indica: `9898989898`. |

## Flujo

1. Arranca, lee `config.xml` y valida la caja en Profit.
2. Valida el usuario y la clave del XML (no se piden en pantalla). Si no son
   correctos muestra el error y no entra.
3. Pantalla principal: factura nueva → pide la cédula del cliente.
4. Se escanean los artículos (lector en modo teclado, termina con Enter).
5. **Salir** pide la contraseña de salida.

## Estructura

```
main.py                      Punto de entrada
app/configuracion.py         Lectura del config.xml
app/encriptacion_profit.py   Validación de claves de Profit (igual que el Sistema Web)
app/aplicacion.py            Arranque: valida caja y usuario y abre la pantalla principal
app/base_datos.py            Conexión a SQL Server (pyodbc)
app/modelos.py               Usuario, Caja, Cliente, Articulo, Factura
app/repositorios/            TODAS las consultas SQL, una clase por tabla
app/ui/teclado.py            Teclado virtual (alfanumérico y numérico)
app/ui/dialogos.py           Diálogos táctiles (pedir dato, mensaje, confirmar)
app/ui/ventana_kiosco.py     Pantalla completa, salida solo con contraseña
app/ui/ventana_principal.py  Escaneo de artículos / factura
tests/                       Pruebas: python -m unittest discover tests
```

## Pendientes

- **Confirmar campos de Profit**: `cajas` (`cod_caja`, `descrip`), lista de precios en `art` (hoy `prec_vta1`), % de IVA según `tipo_imp`.
- **Cliente no registrado**: crear, usar genérico o rechazar (hoy se rechaza).
- **Grabar la factura en Profit** (botón "Finalizar compra").
