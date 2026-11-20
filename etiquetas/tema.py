"""Colores de Ferretería Prados (tomados del logo) para todas las ventanas."""

import tkinter as tk
from tkinter import ttk

ROJO = "#E60000"
ROJO_OSCURO = "#B80000"
AZUL = "#004894"
AZUL_OSCURO = "#00346B"
AZUL_CLARO = "#E8F0FA"
VERDE = "#59CF19"
AMARILLO = "#FFD500"
BLANCO = "#FFFFFF"
FONDO = "#F7F9FC"
TEXTO = "#1F2933"
GRIS = "#6B7785"
BORDE = "#C9D3E0"
ERROR = "#C62828"


def aplicar(raiz: tk.Misc) -> ttk.Style:
    """Aplica los colores a los controles ttk de la aplicación."""
    st = ttk.Style(raiz)
    st.theme_use("clam")
    raiz.configure(bg=FONDO)
    try:
        raiz.option_add("*TCombobox*Listbox.selectBackground", AZUL)
        raiz.option_add("*TCombobox*Listbox.selectForeground", BLANCO)
    except tk.TclError:
        pass

    st.configure(".", background=FONDO, foreground=TEXTO, bordercolor=BORDE,
                 focuscolor=AZUL, lightcolor=FONDO, darkcolor=BORDE)
    st.configure("TFrame", background=FONDO)
    st.configure("TLabel", background=FONDO, foreground=TEXTO)
    st.map("TLabel", background=[("disabled", FONDO)], foreground=[("disabled", GRIS)])
    st.configure("Ayuda.TLabel", background=FONDO, foreground=GRIS)
    st.configure("Error.TLabel", background=FONDO, foreground=ERROR)
    st.configure("Titulo.TLabel", background=FONDO, foreground=AZUL, font=("", 10, "bold"))

    st.configure("TLabelframe", background=FONDO, bordercolor=BORDE, relief="solid", borderwidth=1)
    st.configure("TLabelframe.Label", background=FONDO, foreground=AZUL, font=("", 10, "bold"))

    st.configure("TEntry", fieldbackground=BLANCO, bordercolor=BORDE, lightcolor=BORDE,
                 insertcolor=TEXTO, padding=3)
    st.map("TEntry", bordercolor=[("focus", AZUL)], lightcolor=[("focus", AZUL)],
           fieldbackground=[("disabled", "#EEF1F5")], foreground=[("disabled", GRIS)])
    st.configure("TSpinbox", fieldbackground=BLANCO, bordercolor=BORDE, arrowcolor=AZUL, padding=3)
    st.map("TSpinbox", fieldbackground=[("disabled", "#EEF1F5")], foreground=[("disabled", GRIS)])
    st.configure("TCombobox", fieldbackground=BLANCO, bordercolor=BORDE, arrowcolor=AZUL, padding=3)
    st.map("TCombobox", fieldbackground=[("readonly", BLANCO), ("disabled", "#EEF1F5")],
           bordercolor=[("focus", AZUL)], selectbackground=[("readonly", BLANCO)],
           selectforeground=[("readonly", TEXTO)])

    # Botones: azul normal, rojo para la acción principal (Imprimir)
    st.configure("TButton", background=AZUL, foreground=BLANCO, bordercolor=AZUL,
                 lightcolor=AZUL, darkcolor=AZUL, padding=(12, 5), font=("", 9, "bold"))
    st.map("TButton",
           background=[("disabled", "#AFC2D9"), ("pressed", AZUL_OSCURO), ("active", "#0A5BB5")],
           foreground=[("disabled", BLANCO)],
           bordercolor=[("active", AZUL_OSCURO)])
    st.configure("Principal.TButton", background=ROJO, bordercolor=ROJO, lightcolor=ROJO,
                 darkcolor=ROJO, padding=(18, 6), font=("", 10, "bold"))
    st.map("Principal.TButton",
           background=[("disabled", "#F0A0A0"), ("pressed", ROJO_OSCURO), ("active", "#FF1A1A")])
    st.configure("Suave.TButton", background=BLANCO, foreground=AZUL, bordercolor=BORDE,
                 lightcolor=BLANCO, darkcolor=BLANCO)
    st.map("Suave.TButton",
           background=[("disabled", BLANCO), ("active", AZUL_CLARO), ("pressed", AZUL_CLARO)],
           foreground=[("disabled", "#AFC2D9")])

    st.configure("TCheckbutton", background=FONDO, foreground=TEXTO, indicatorcolor=BLANCO)
    st.map("TCheckbutton", indicatorcolor=[("selected", AZUL)], background=[("active", FONDO)])
    st.configure("TRadiobutton", background=FONDO, foreground=TEXTO, indicatorcolor=BLANCO)
    st.map("TRadiobutton", indicatorcolor=[("selected", ROJO)], background=[("active", FONDO)])

    st.configure("Treeview", background=BLANCO, fieldbackground=BLANCO, foreground=TEXTO,
                 bordercolor=BORDE, rowheight=24)
    st.map("Treeview", background=[("selected", AZUL)], foreground=[("selected", BLANCO)])
    st.configure("Treeview.Heading", background=AZUL, foreground=BLANCO, bordercolor=AZUL_OSCURO,
                 font=("", 9, "bold"), padding=4)
    st.map("Treeview.Heading", background=[("active", AZUL_OSCURO)])

    st.configure("Estado.TLabel", background=AZUL, foreground=BLANCO, padding=(8, 3))
    return st


def encabezado(padre: tk.Misc, titulo: str, subtitulo: str = "") -> tk.Frame:
    """Franja roja con el nombre de la ferretería, y una línea azul debajo (como el logo)."""
    marco = tk.Frame(padre, bg=ROJO)
    tk.Label(marco, text="PRADOS", bg=ROJO, fg=BLANCO,
             font=("Arial Black", 18, "bold")).pack(side="left", padx=(14, 6), pady=6)
    tk.Label(marco, text="FERRETERÍA", bg=ROJO, fg=BLANCO,
             font=("Arial", 9, "bold")).pack(side="left", pady=(14, 6))
    derecha = tk.Frame(marco, bg=ROJO)
    derecha.pack(side="right", padx=14)
    tk.Label(derecha, text=titulo, bg=ROJO, fg=BLANCO, font=("", 12, "bold")).pack(anchor="e")
    if subtitulo:
        tk.Label(derecha, text=subtitulo, bg=ROJO, fg="#FFE5E5", font=("", 8)).pack(anchor="e")
    return marco


def colocar_encabezado(padre: tk.Misc, titulo: str, subtitulo: str = "") -> None:
    """Pone el encabezado arriba de la ventana (con pack): franja roja y, debajo,
    una línea con los colores de las herramientas del logo."""
    encabezado(padre, titulo, subtitulo).pack(fill="x", side="top")
    linea = tk.Frame(padre, bg=BLANCO, height=4)
    linea.pack(fill="x", side="top")
    for color in (AZUL, VERDE, AMARILLO, ROJO, AZUL):
        tk.Frame(linea, bg=color, height=4).pack(side="left", fill="both", expand=True)
