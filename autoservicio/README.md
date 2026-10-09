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
El usuario de SQL del XML necesita leer las tablas de la empresa (incluida `PPV_AMBIENTE`) y `MasterProfit.dbo.employee`.
La caja ya no va en el XML: sale del ambiente del usuario.

## Configuración (`config.xml`)

Va en la **misma carpeta del ejecutable**. Es el mismo XML del Visor de
Precios con estas secciones nuevas:

| Sección | Etiqueta | Uso |
|---|---|---|
| `<usuario>` | `<codigo>`, `<clave>` | Usuario y clave de Profit. Se validan al arrancar contra `MasterProfit.dbo.employee` (activo, Estado `A`, encriptación de Profit). **Obligatorio.** |
| `<usuario>` | `<base>`, `<tabla>` | Opcionales. Por defecto `MasterProfit` / `employee`. |
| `<ambiente>` | `<cod_emp>` | Opcional. `COD_EMP` de `PPV_AMBIENTE` (por defecto `<basedatos>`). |
| `<seguridad>` | `<clave_salida>` | Contraseña para salir. Si no se indica: `9898989898`. |

## Flujo

1. Arranca y lee `config.xml`.
2. Valida el usuario y la clave del XML (no se piden en pantalla). Si no son
   correctos muestra el error y no entra.
3. Carga el ambiente del usuario de `PPV_AMBIENTE` (`COD_EMP` + `COD_USU`) con
   **todos** sus parámetros. Si no tiene ambiente creado, muestra el error y no entra.
   Del ambiente salen la **caja** (`VD_CAJA`, se valida en `cajas`), la **lista de
   precios** (`VD_LISTPREC`, 1 a 5 → `prec_vta1`…`prec_vta5`) y el **cliente por
   defecto** (`VD_CLIENTE`).
4. Pantalla principal: factura nueva → pide la cédula del cliente. Hay un botón
   para continuar sin cédula con el cliente por defecto del ambiente.
5. Se escanean los artículos (lector en modo teclado, termina con Enter).
6. **Salir** pide la contraseña de salida.

## Parámetros del ambiente

Quedan en `sesion.ambiente` (objeto `Ambiente` de `app/modelos.py`). Cualquier
columna de `PPV_AMBIENTE` se lee por su nombre:

```python
ambiente["VD_ALMACEN"]     # como diccionario
ambiente.VD_LISTPREC       # como atributo
ambiente.get("VE_BSALIR")  # None si la columna no existe
ambiente.almacen           # alias en español de los más usados (ver Ambiente.ALIAS)
```

## Estructura

```
main.py                      Punto de entrada
app/configuracion.py         Lectura del config.xml
app/encriptacion_profit.py   Validación de claves de Profit (igual que el Sistema Web)
app/aplicacion.py            Arranque: valida caja y usuario y abre la pantalla principal
app/base_datos.py            Conexión a SQL Server (pyodbc)
app/modelos.py               Usuario, Caja, Ambiente, Sesion, Cliente, Articulo, Factura
app/repositorios/            TODAS las consultas SQL, una clase por tabla
app/ui/teclado.py            Teclado virtual (alfanumérico y numérico)
app/ui/dialogos.py           Diálogos táctiles (pedir dato, mensaje, confirmar)
app/ui/ventana_kiosco.py     Pantalla completa, salida solo con contraseña
app/ui/ventana_principal.py  Escaneo de artículos / factura
tests/                       Pruebas: python -m unittest discover tests
```

## Pendientes

- **Confirmar campos de Profit**: `cajas` (`cod_caja`, `descrip`), formato de `VD_LISTPREC`, % de IVA según `tipo_imp`.
- **Grabar la factura en Profit** (botón "Finalizar compra").
