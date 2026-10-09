"""
Paquete principal de la Aplicación de Autoservicio.

Organización de los módulos:

    app/
    ├── configuracion.py   Lectura del config.xml (mismo formato del Visor de Precios + caja).
    ├── registro.py        Bitácora (log) de la aplicación en un archivo de texto.
    ├── utilidades.py      Funciones de apoyo (formato de montos, rutas, etc.).
    ├── modelos.py         Clases de datos: Usuario, Caja, Cliente, Articulo, Factura...
    ├── base_datos.py      Conexión a SQL Server (Profit Plus 2K8) mediante pyodbc.
    ├── repositorios/      Una clase por tabla de Profit: aquí viven TODAS las consultas SQL.
    └── ui/                Pantallas y controles táctiles (PySide6).

La regla general es: la interfaz (ui) nunca escribe SQL; le pide los datos a
los repositorios, y los repositorios usan base_datos para hablar con SQL Server.
"""

# Nombre y versión que se muestran en pantalla y en la bitácora.
NOMBRE_APLICACION = "Aplicación de Autoservicio"
VERSION = "0.1.0"
