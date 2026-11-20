#!/usr/bin/env python3
"""
Editor de config.xml (servidor SQL, tabla, impresora y etiqueta).

Ejecutar solo:  python configurar.py
También se abre desde el botón «Configuración…» del programa de etiquetas.
"""

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

import config
import zpl
from db import Articulos

TIPOS = {
    "red": "Zebra en red (IP)",
    "windows": "Impresora instalada en Windows",
    "dispositivo": "Dispositivo / impresora compartida",
    "archivo": "Archivo .zpl (pruebas)",
}
AYUDA_DESTINO = {
    "red": "IP o nombre de la impresora, ej. 192.168.1.50",
    "windows": "Nombre de la impresora tal como aparece en Windows",
    "dispositivo": r"Ej. \\PC-CAJA\Zebra  o  /dev/usb/lp0",
    "archivo": "Ruta del archivo donde se guardan las etiquetas",
}

CAMPOS_SQL = [("servidor", "Servidor"), ("puerto", "Puerto"), ("base_datos", "Base de datos"),
              ("usuario", "Usuario"), ("clave", "Clave"), ("timeout", "Espera (seg.)")]
CAMPOS_TABLA = [("nombre", "Tabla"), ("codigo", "Columna código"),
                ("descripcion", "Columna descripción"), ("precio", "Columna precio"),
                ("barras", "Columna código de barras")]


def impresoras_windows() -> list[str]:
    try:
        import win32print
    except ImportError:
        return []
    flags = win32print.PRINTER_ENUM_LOCAL | win32print.PRINTER_ENUM_CONNECTIONS
    return [p[2] for p in win32print.EnumPrinters(flags)]


class Editor(tk.Toplevel):
    def __init__(self, padre, al_guardar=None):
        super().__init__(padre)
        self.title("Configuración - Etiquetas Zebra")
        self.resizable(False, False)
        self.al_guardar = al_guardar
        try:
            self.cfg = config.cargar()
        except FileNotFoundError:
            self.cfg = config.nueva()
        except Exception as e:  # noqa: BLE001 - XML dañado
            messagebox.showwarning("Configuración",
                                   f"No se pudo leer config.xml, se usarán valores por defecto:\n\n{e}",
                                   parent=self)
            self.cfg = config.nueva()

        self.vars: dict[str, dict[str, tk.StringVar]] = {
            s: {k: tk.StringVar(value=v) for k, v in vals.items()} for s, vals in self.cfg.items()
        }
        self._construir()
        self._cambio_tipo()
        if padre.winfo_viewable():
            self.transient(padre)
        self.grab_set()
        self.bind("<Escape>", lambda _e: self.destroy())
        self.focus_force()

    # ------------------------------------------------------------ interfaz
    def _construir(self):
        cuerpo = ttk.Frame(self, padding=12)
        cuerpo.pack(fill="both", expand=True)
        izq, der = ttk.Frame(cuerpo), ttk.Frame(cuerpo)
        izq.grid(row=0, column=0, sticky="n")
        der.grid(row=0, column=1, sticky="n", padx=(12, 0))

        # --- SQL
        f = ttk.LabelFrame(izq, text=" Servidor SQL ", padding=10)
        f.pack(fill="x")
        for i, (k, titulo) in enumerate(CAMPOS_SQL):
            ttk.Label(f, text=titulo + ":").grid(row=i, column=0, sticky="w", pady=2)
            e = ttk.Entry(f, textvariable=self.vars["sql"][k], width=28,
                          show="•" if k == "clave" else "")
            e.grid(row=i, column=1, sticky="w", padx=6)
            if k == "clave":
                self.ent_clave = e
                ver = tk.BooleanVar()
                ttk.Checkbutton(f, text="Ver", variable=ver,
                                command=lambda: self.ent_clave.configure(show="" if ver.get() else "•")
                                ).grid(row=i, column=2)
        ttk.Label(f, text="Si SQL Server está en Docker en este equipo: localhost",
                  foreground="#666").grid(row=len(CAMPOS_SQL), column=0, columnspan=3, sticky="w",
                                          pady=(4, 0))

        # --- Tabla
        f = ttk.LabelFrame(izq, text=" Tabla de artículos ", padding=10)
        f.pack(fill="x", pady=(10, 0))
        for i, (k, titulo) in enumerate(CAMPOS_TABLA):
            ttk.Label(f, text=titulo + ":").grid(row=i, column=0, sticky="w", pady=2)
            ttk.Entry(f, textvariable=self.vars["tabla"][k], width=22).grid(row=i, column=1,
                                                                            sticky="w", padx=6)
        ttk.Button(f, text="Probar conexión", command=self.probar_conexion).grid(
            row=len(CAMPOS_TABLA), column=0, columnspan=2, sticky="w", pady=(8, 0))

        # --- Impresora
        imp = self.vars["impresora"]
        f = ttk.LabelFrame(der, text=" Impresora ", padding=10)
        f.pack(fill="x")
        ttk.Label(f, text="Conexión:").grid(row=0, column=0, sticky="w", pady=2)
        self.cb_tipo = ttk.Combobox(f, values=list(TIPOS.values()), state="readonly", width=34)
        self.cb_tipo.set(TIPOS.get(imp["tipo"].get(), TIPOS["red"]))
        self.cb_tipo.grid(row=0, column=1, columnspan=2, sticky="w", padx=6)
        self.cb_tipo.bind("<<ComboboxSelected>>", lambda _e: self._cambio_tipo())

        ttk.Label(f, text="Destino:").grid(row=1, column=0, sticky="w", pady=2)
        self.cb_destino = ttk.Combobox(f, textvariable=imp["destino"], width=34)
        self.cb_destino.grid(row=1, column=1, sticky="w", padx=6)
        self.btn_archivo = ttk.Button(f, text="…", width=3, command=self._elegir_archivo)
        self.btn_archivo.grid(row=1, column=2)
        self.lbl_ayuda = ttk.Label(f, text="", foreground="#666")
        self.lbl_ayuda.grid(row=2, column=1, columnspan=2, sticky="w", padx=6)

        ttk.Label(f, text="Puerto:").grid(row=3, column=0, sticky="w", pady=2)
        self.ent_puerto = ttk.Entry(f, textvariable=imp["puerto"], width=8)
        self.ent_puerto.grid(row=3, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Resolución (dpi):").grid(row=4, column=0, sticky="w", pady=2)
        ttk.Combobox(f, textvariable=imp["dpi"], values=["152", "203", "300", "600"],
                     state="readonly", width=6).grid(row=4, column=1, sticky="w", padx=6)
        ttk.Button(f, text="Imprimir etiqueta de prueba", command=self.imprimir_prueba).grid(
            row=5, column=0, columnspan=3, sticky="w", pady=(8, 0))

        # --- Etiqueta
        et = self.vars["etiqueta"]
        f = ttk.LabelFrame(der, text=" Etiqueta ", padding=10)
        f.pack(fill="x", pady=(10, 0))
        ttk.Label(f, text="Formato por defecto:").grid(row=0, column=0, sticky="w", pady=2)
        if et["formato"].get() not in zpl.FORMATOS:
            et["formato"].set(next(iter(zpl.FORMATOS)))
        ttk.Combobox(f, textvariable=et["formato"], values=list(zpl.FORMATOS), state="readonly",
                     width=24).grid(row=0, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Moneda:").grid(row=1, column=0, sticky="w", pady=2)
        ttk.Entry(f, textvariable=et["moneda"], width=8).grid(row=1, column=1, sticky="w", padx=6)

        # --- Botones
        abajo = ttk.Frame(cuerpo, padding=(0, 12, 0, 0))
        abajo.grid(row=1, column=0, columnspan=2, sticky="ew")
        ttk.Label(abajo, text=f"Archivo: {config.RUTA}", foreground="#666").pack(side="left")
        ttk.Button(abajo, text="Guardar", command=self.guardar).pack(side="right")
        ttk.Button(abajo, text="Cancelar", command=self.destroy).pack(side="right", padx=6)

    def _tipo(self) -> str:
        nombre = self.cb_tipo.get()
        return next((k for k, v in TIPOS.items() if v == nombre), "red")

    def _cambio_tipo(self):
        tipo = self._tipo()
        self.vars["impresora"]["tipo"].set(tipo)
        self.lbl_ayuda.configure(text=AYUDA_DESTINO[tipo])
        self.ent_puerto.configure(state="normal" if tipo == "red" else "disabled")
        self.btn_archivo.configure(state="normal" if tipo == "archivo" else "disabled")
        self.cb_destino.configure(values=impresoras_windows() if tipo == "windows" else [])

    def _elegir_archivo(self):
        ruta = filedialog.asksaveasfilename(parent=self, defaultextension=".zpl",
                                            filetypes=[("ZPL", "*.zpl"), ("Todos", "*.*")])
        if ruta:
            self.vars["impresora"]["destino"].set(ruta)

    # ------------------------------------------------------------ datos
    def _valores(self) -> config.Config:
        return config.Config({s: {k: v.get().strip() for k, v in vs.items()}
                              for s, vs in self.vars.items()})

    def _validar(self, cfg) -> str | None:
        for s, k, titulo in (("sql", "puerto", "Puerto SQL"), ("sql", "timeout", "Espera"),
                             ("impresora", "puerto", "Puerto de la impresora")):
            if not cfg[s][k].isdigit():
                return f"«{titulo}» debe ser un número."
        for s, k, titulo in (("sql", "servidor", "Servidor"), ("sql", "base_datos", "Base de datos"),
                             ("impresora", "destino", "Destino de la impresora")):
            if not cfg[s][k]:
                return f"Falta «{titulo}»."
        try:
            Articulos(cfg)  # valida nombres de tabla y columnas
        except ValueError as e:
            return str(e)
        return None

    # ------------------------------------------------------------ acciones
    def probar_conexion(self):
        cfg = self._valores()
        if error := self._validar(cfg):
            messagebox.showwarning("Configuración", error, parent=self)
            return
        self.configure(cursor="watch")
        self.update()
        try:
            total = Articulos(cfg).probar()
        except Exception as e:  # noqa: BLE001
            messagebox.showerror("Probar conexión", f"Error al conectar o consultar la tabla:\n\n{e}", parent=self)
        else:
            messagebox.showinfo("Probar conexión",
                                f"Conexión correcta.\nLa tabla «{cfg['tabla']['nombre']}» "
                                f"tiene {total} artículos.", parent=self)
        finally:
            self.configure(cursor="")

    def imprimir_prueba(self):
        cfg = self._valores()
        imp = cfg["impresora"]
        p = zpl.Producto(codigo="1234567890", nombre="ETIQUETA DE PRUEBA", precio=1234.56)
        texto = zpl.generar_zpl(p, zpl.FORMATOS[cfg["etiqueta"]["formato"]],
                                cfg.entero("impresora", "dpi"), cfg["etiqueta"]["moneda"])
        try:
            zpl.enviar(texto, imp["tipo"], imp["destino"], cfg.entero("impresora", "puerto"))
        except Exception as e:  # noqa: BLE001
            messagebox.showerror("Impresora", f"No se pudo imprimir:\n\n{e}", parent=self)
        else:
            messagebox.showinfo("Impresora", f"Etiqueta de prueba enviada a {imp['destino']}.",
                                parent=self)

    def guardar(self):
        cfg = self._valores()
        if error := self._validar(cfg):
            messagebox.showwarning("Configuración", error, parent=self)
            return
        try:
            config.guardar(cfg)
        except OSError as e:
            messagebox.showerror("Configuración", f"No se pudo guardar:\n\n{e}", parent=self)
            return
        self.destroy()
        if self.al_guardar:
            self.al_guardar()


def abrir_solo() -> bool:
    """Abre el editor como ventana principal. Devuelve True si se guardó."""
    raiz = tk.Tk()
    raiz.withdraw()
    guardado = []
    ed = Editor(raiz, al_guardar=lambda: guardado.append(True))
    ed.bind("<Destroy>", lambda e: raiz.after(0, raiz.destroy) if e.widget is ed else None)
    raiz.mainloop()
    return bool(guardado)


if __name__ == "__main__":
    abrir_solo()
