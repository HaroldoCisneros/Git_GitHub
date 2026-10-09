"""
Hoja de estilos (QSS) de la aplicación.

Una pantalla táctil necesita letras grandes y botones donde quepa un dedo,
por eso aquí se definen tamaños mínimos generosos. Los colores están
agrupados al principio para cambiarlos fácilmente.

Los botones usan la propiedad "tipo" para cambiar de color, por ejemplo:
    boton.setProperty("tipo", "peligro")   -> botón rojo
"""

# ---- Colores ---------------------------------------------------------------
COLOR_FONDO = "#F4F6F8"
COLOR_PANEL = "#FFFFFF"
COLOR_TEXTO = "#1F2933"
COLOR_PRIMARIO = "#1565C0"      # Azul: acciones normales.
COLOR_EXITO = "#2E7D32"         # Verde: finalizar / aceptar.
COLOR_PELIGRO = "#C62828"       # Rojo: salir / cancelar / quitar.
COLOR_TECLA = "#E3E7EB"         # Teclas del teclado virtual.

# ---- Tamaños ---------------------------------------------------------------
ALTO_BOTON = 70                 # Alto mínimo de los botones (px).
ALTO_TECLA = 64                 # Alto de las teclas del teclado (px).

HOJA_ESTILOS = f"""
QWidget {{
    background: {COLOR_FONDO};
    color: {COLOR_TEXTO};
    font-size: 20px;
}}

/* Paneles blancos (barra superior, totales...) */
QFrame#panel {{
    background: {COLOR_PANEL};
    border-radius: 12px;
}}

QLabel {{ background: transparent; }}
QLabel#titulo {{ font-size: 32px; font-weight: bold; }}
QLabel#subtitulo {{ font-size: 22px; color: #52606D; }}
QLabel#total {{ font-size: 44px; font-weight: bold; color: {COLOR_EXITO}; }}
QLabel#error {{ color: {COLOR_PELIGRO}; font-weight: bold; }}

/* Campos de texto */
QLineEdit {{
    background: {COLOR_PANEL};
    border: 2px solid #9AA5B1;
    border-radius: 10px;
    padding: 10px;
    font-size: 28px;
    min-height: 50px;
}}
QLineEdit:focus {{ border-color: {COLOR_PRIMARIO}; }}

/* Botones de acción */
QPushButton {{
    background: {COLOR_PRIMARIO};
    color: white;
    border: none;
    border-radius: 12px;
    padding: 10px 24px;
    font-size: 22px;
    font-weight: bold;
    min-height: {ALTO_BOTON}px;
}}
QPushButton:pressed {{ background: #0D47A1; }}
QPushButton:disabled {{ background: #9AA5B1; }}
QPushButton[tipo="exito"]   {{ background: {COLOR_EXITO}; }}
QPushButton[tipo="peligro"] {{ background: {COLOR_PELIGRO}; }}

/* Teclas del teclado virtual */
QPushButton[tipo="tecla"] {{
    background: {COLOR_TECLA};
    color: {COLOR_TEXTO};
    font-size: 26px;
    min-height: {ALTO_TECLA}px;
    min-width: 64px;
    padding: 0px;
}}
QPushButton[tipo="tecla"]:pressed {{ background: #BCCCDC; }}
QPushButton[tipo="tecla"]:checked {{ background: {COLOR_PRIMARIO}; color: white; }}

/* Tabla de artículos de la factura */
QTableWidget {{
    background: {COLOR_PANEL};
    border-radius: 12px;
    font-size: 22px;
    gridline-color: #E4E7EB;
    selection-background-color: #BBDEFB;
    selection-color: {COLOR_TEXTO};
}}
QHeaderView::section {{
    background: {COLOR_PRIMARIO};
    color: white;
    font-size: 20px;
    font-weight: bold;
    padding: 8px;
    border: none;
}}
/* Barra de desplazamiento ancha para el dedo */
QScrollBar:vertical {{ width: 40px; }}

QDialog {{
    background: {COLOR_PANEL};
    border: 3px solid {COLOR_PRIMARIO};
    border-radius: 12px;
}}
"""
