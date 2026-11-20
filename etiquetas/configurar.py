#!/usr/bin/env python3
"""
Editor de config.xml (servidor SQL, tabla, impresora y etiqueta).

Ejecutar solo:  python configurar.py
También se abre desde el botón «Configuración…» del programa de etiquetas.
"""

import re
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog, ttk

import config
import winprint
import zpl
from db import Articulos
from iconos import poner_icono

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

BARRAS = {
    "0": "Automático (la más gruesa que quepa)",
    "1": "1 punto (0,125 mm) - muy fina",
    "2": "2 puntos (0,25 mm)",
    "3": "3 puntos (0,375 mm)",
}

CAMPOS_SQL = [("servidor", "Servidor"), ("puerto", "Puerto"), ("base_datos", "Base de datos"),
              ("usuario", "Usuario"), ("clave", "Clave"), ("timeout", "Espera (seg.)")]
CAMPOS_TABLA = [("nombre", "Tabla"), ("codigo", "Columna código"),
                ("descripcion", "Columna descripción"), ("precio", "Columna precio"),
                ("barras", "Columna código de barras"),
                ("referencia", "Columna referencia (opcional)")]


class Editor(tk.Toplevel):
    def __init__(self, padre, al_guardar=None):
        super().__init__(padre)
        self.title("Configuración - Etiquetas Zebra")
        poner_icono(self, "logoconfig.bmp")
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
        self.btn_archivo = ttk.Button(f, text="…", width=3, command=self._boton_destino)
        self.btn_archivo.grid(row=1, column=2)
        self.lbl_ayuda = ttk.Label(f, text="", foreground="#666")
        self.lbl_ayuda.grid(row=2, column=1, columnspan=2, sticky="w", padx=6)

        ttk.Label(f, text="Puerto:").grid(row=3, column=0, sticky="w", pady=2)
        self.ent_puerto = ttk.Entry(f, textvariable=imp["puerto"], width=8)
        self.ent_puerto.grid(row=3, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Resolución (dpi):").grid(row=4, column=0, sticky="w", pady=2)
        ttk.Combobox(f, textvariable=imp["dpi"], values=["152", "203", "300", "600"],
                     state="readonly", width=6).grid(row=4, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Oscuridad:").grid(row=5, column=0, sticky="w", pady=2)
        ttk.Combobox(f, textvariable=imp["oscuridad"], width=6,
                     values=["", "-15", "-10", "-8", "-5", "-3", "0", "3", "5"]).grid(
            row=5, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Velocidad (pulg./seg.):").grid(row=6, column=0, sticky="w", pady=2)
        ttk.Combobox(f, textvariable=imp["velocidad"], width=6,
                     values=["", "2", "3", "4", "5", "6"]).grid(row=6, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Vacío = lo que tenga la impresora. Oscuridad negativa = más claro.",
                  foreground="#666").grid(row=7, column=1, columnspan=2, sticky="w", padx=6)
        botones = ttk.Frame(f)
        botones.grid(row=8, column=0, columnspan=3, sticky="w", pady=(8, 0))
        ttk.Button(botones, text="Imprimir etiqueta de prueba",
                   command=self.imprimir_prueba).pack(side="left")
        ttk.Button(botones, text="Hoja de prueba de lectura…",
                   command=self.prueba_lectura).pack(side="left", padx=6)

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
        self.con_precio = tk.BooleanVar(value=et["precio"].get().lower() in ("si", "sí", "1", "true"))
        ttk.Checkbutton(f, text="Imprimir el precio", variable=self.con_precio).grid(
            row=2, column=1, sticky="w", padx=6)
        self.girar = tk.BooleanVar(value=et["girar"].get().strip() == "180")
        ttk.Checkbutton(f, text="Girar 180° (si la etiqueta sale al revés)",
                        variable=self.girar).grid(row=5, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Grosor de barra:").grid(row=6, column=0, sticky="w", pady=2)
        self.cb_barra = ttk.Combobox(f, values=list(BARRAS.values()), state="readonly", width=28)
        self.cb_barra.set(BARRAS.get(et["barra"].get().strip(), BARRAS["0"]))
        self.cb_barra.grid(row=6, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Reducción de barra:").grid(row=7, column=0, sticky="w", pady=2)
        ttk.Combobox(f, textvariable=et["reduccion"], values=["0", "1"], state="readonly",
                     width=4).grid(row=7, column=1, sticky="w", padx=6)
        ttk.Label(f, text="1 = barras 1 punto más finas (si la tinta se corre; solo con barra de 2 o 3)",
                  foreground="#666").grid(row=8, column=1, sticky="w", padx=6)
        ttk.Label(f, text="Logo:").grid(row=3, column=0, sticky="w", pady=2)
        fila = ttk.Frame(f)
        fila.grid(row=3, column=1, sticky="w", padx=6)
        ttk.Entry(fila, textvariable=et["logo"], width=28).pack(side="left")
        ttk.Button(fila, text="…", width=3, command=self._elegir_logo).pack(side="left", padx=2)
        ttk.Button(fila, text="✕", width=3, command=lambda: et["logo"].set("")).pack(side="left")
        ttk.Label(f, text="Imagen en blanco y negro (png, jpg, bmp); vacío = sin logo",
                  foreground="#666").grid(row=4, column=1, sticky="w", padx=6)

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
        self.btn_archivo.configure(state="normal" if tipo in ("archivo", "windows") else "disabled",
                                   text="↻" if tipo == "windows" else "…")
        if tipo == "windows":
            self._cargar_impresoras()
        else:
            self.cb_destino.configure(values=[])

    def _cargar_impresoras(self):
        """Llena la lista con las impresoras instaladas en Windows."""
        try:
            lista = winprint.impresoras()
        except OSError as e:
            lista = []
            self.lbl_ayuda.configure(text=f"No se pudo leer la lista de impresoras: {e}")
        self.cb_destino.configure(values=lista)
        destino = self.vars["impresora"]["destino"]
        if lista and destino.get() not in lista:
            destino.set(winprint.sugerida(lista))
        if not lista and winprint.disponible():
            self.lbl_ayuda.configure(text="No hay impresoras instaladas en Windows")

    def _boton_destino(self):
        if self._tipo() == "windows":
            self._cargar_impresoras()
        else:
            self._elegir_archivo()

    def _elegir_logo(self):
        ruta = filedialog.askopenfilename(parent=self, title="Imagen del logo", filetypes=[
            ("Imágenes", "*.png *.jpg *.jpeg *.bmp *.gif"), ("Todos", "*.*")])
        if ruta:
            try:  # si está dentro de la carpeta del programa se guarda la ruta relativa
                ruta = str(Path(ruta).resolve().relative_to(zpl.CARPETA))
            except ValueError:
                pass
            self.vars["etiqueta"]["logo"].set(ruta)

    def _elegir_archivo(self):
        ruta = filedialog.asksaveasfilename(parent=self, defaultextension=".zpl",
                                            filetypes=[("ZPL", "*.zpl"), ("Todos", "*.*")])
        if ruta:
            self.vars["impresora"]["destino"].set(ruta)

    # ------------------------------------------------------------ datos
    def _valores(self) -> config.Config:
        self.vars["etiqueta"]["precio"].set("si" if self.con_precio.get() else "no")
        self.vars["etiqueta"]["girar"].set("180" if self.girar.get() else "0")
        self.vars["etiqueta"]["barra"].set(
            next((k for k, v in BARRAS.items() if v == self.cb_barra.get()), "0"))
        return config.Config({s: {k: v.get().strip() for k, v in vs.items()}
                              for s, vs in self.vars.items()})

    def _validar(self, cfg) -> str | None:
        for s, k, titulo in (("sql", "puerto", "Puerto SQL"), ("sql", "timeout", "Espera"),
                             ("impresora", "puerto", "Puerto de la impresora")):
            if not cfg[s][k].isdigit():
                return f"«{titulo}» debe ser un número."
        for k, titulo, minimo, maximo in (("oscuridad", "Oscuridad", -30, 30),
                                          ("velocidad", "Velocidad", 1, 14)):
            valor = cfg["impresora"][k]
            if valor and not (re.fullmatch(r"[+-]?\d+", valor) and minimo <= int(valor) <= maximo):
                return f"«{titulo}» debe estar vacío o ser un número entre {minimo} y {maximo}."
        for s, k, titulo in (("sql", "servidor", "Servidor"), ("sql", "base_datos", "Base de datos"),
                             ("impresora", "destino", "Destino de la impresora")):
            if not cfg[s][k]:
                return f"Falta «{titulo}»."
        logo = zpl.ruta_logo(cfg["etiqueta"]["logo"])
        if logo and not logo.exists():
            return f"No se encuentra la imagen del logo:\n{logo}"
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

    def _enviar(self, cfg, texto: str, mensaje: str):
        imp = cfg["impresora"]
        try:
            zpl.enviar(texto, imp["tipo"], imp["destino"], cfg.entero("impresora", "puerto"))
        except Exception as e:  # noqa: BLE001
            messagebox.showerror("Impresora", f"No se pudo imprimir:\n\n{e}", parent=self)
        else:
            messagebox.showinfo("Impresora", mensaje, parent=self)

    def imprimir_prueba(self):
        cfg = self._valores()
        if error := self._validar(cfg):
            messagebox.showwarning("Configuración", error, parent=self)
            return
        p = zpl.Producto(codigo="00121008", nombre="ETIQUETA DE PRUEBA DESCRIPCION DEL ARTICULO",
                         precio=1234.56, referencia="REF-0001")
        try:
            texto = zpl.generar_zpl(p, zpl.FORMATOS[cfg["etiqueta"]["formato"]], **config.opciones(cfg))
        except Exception as e:  # noqa: BLE001
            messagebox.showerror("Impresora", f"No se pudo generar la etiqueta:\n\n{e}", parent=self)
            return
        self._enviar(cfg, texto, f"Etiqueta de prueba enviada a {cfg['impresora']['destino']}.")

    def prueba_lectura(self):
        """Imprime el mismo código con varios ajustes (A, B, C...) para ver cuál lee el lector."""
        cfg = self._valores()
        if error := self._validar(cfg):
            messagebox.showwarning("Configuración", error, parent=self)
            return
        codigo = simpledialog.askstring(
            "Hoja de prueba de lectura",
            "Código de un artículo para la prueba\n(se imprime una etiqueta por cada ajuste):",
            initialvalue="00121008", parent=self)
        if not codigo or not codigo.strip():
            return
        opciones = config.opciones(cfg)
        for clave in ("barra", "reduccion", "oscuridad", "velocidad"):
            opciones.pop(clave)
        try:
            texto, omitidas = zpl.prueba_lectura(codigo.strip(), zpl.FORMATOS[cfg["etiqueta"]["formato"]],
                                                 **opciones)
        except Exception as e:  # noqa: BLE001
            messagebox.showerror("Prueba", f"No se pudo generar la prueba:\n\n{e}", parent=self)
            return
        lista = "\n".join(f"  {letra}: {txt}" for letra, txt, aj in zpl.VARIANTES_PRUEBA
                          if f"{letra}: {txt}" not in omitidas)
        aviso = ("\n\nNo caben con este código:\n  " + "\n  ".join(omitidas)) if omitidas else ""
        self._enviar(cfg, texto,
                     f"Se enviaron {texto.count('^XA')} etiquetas. Cada una dice PRUEBA y una letra:\n\n"
                     f"{lista}{aviso}\n\nPase el lector por cada una y ponga en la configuración "
                     "el grosor, la reducción, la oscuridad y la velocidad de la que lea mejor.")

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
