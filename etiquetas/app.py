#!/usr/bin/env python3
"""
Etiquetas Zebra - ventana para imprimir etiquetas de artículos.

Modos (se cambian con el interruptor):
  * Un artículo: un código y la cantidad de etiquetas.
  * Lista de 10: hasta 10 códigos distintos, una etiqueta por cada uno.

Ejecutar:  python app.py
"""

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

import zpl
from db import Articulos, cargar_config

FILAS_LISTA = 10


class Interruptor(tk.Canvas):
    """Interruptor deslizante (switch) de encendido/apagado."""

    def __init__(self, padre, variable: tk.BooleanVar, comando=None, ancho=52, alto=26):
        super().__init__(padre, width=ancho, height=alto, highlightthickness=0,
                         bg=padre.winfo_toplevel()["bg"], cursor="hand2")
        self.var, self.comando, self.w, self.h = variable, comando, ancho, alto
        self.bind("<Button-1>", self._cambiar)
        self.var.trace_add("write", lambda *_: self._dibujar())
        self._dibujar()

    def _cambiar(self, _evento=None):
        self.var.set(not self.var.get())
        if self.comando:
            self.comando()

    def _dibujar(self):
        self.delete("all")
        w, h, r = self.w, self.h, self.h / 2
        color = "#2e7d32" if self.var.get() else "#1565c0"
        self.create_oval(0, 0, h, h, fill=color, outline=color)
        self.create_oval(w - h, 0, w, h, fill=color, outline=color)
        self.create_rectangle(r, 0, w - r, h, fill=color, outline=color)
        x = w - h + 3 if self.var.get() else 3
        self.create_oval(x, 3, x + h - 6, h - 3, fill="white", outline="white")


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Etiquetas Zebra")
        self.resizable(False, False)

        self.cfg = cargar_config()
        self.db = Articulos(self.cfg)
        imp = self.cfg["impresora"]
        self.imp_tipo = imp.get("tipo", "red")
        self.imp_destino = imp.get("destino", "")
        self.imp_puerto = imp.getint("puerto", 9100)
        self.dpi = imp.getint("dpi", 203)
        et = self.cfg["etiqueta"] if self.cfg.has_section("etiqueta") else {}
        self.moneda = et.get("moneda", "Bs.")

        self.cache: dict[str, zpl.Producto | None] = {}
        self.modo_lista = tk.BooleanVar(value=False)
        self.formato = tk.StringVar(value=et.get("formato", next(iter(zpl.FORMATOS))))
        if self.formato.get() not in zpl.FORMATOS:
            self.formato.set(next(iter(zpl.FORMATOS)))

        self._construir()
        self._aplicar_modo()
        self.cod_unico.focus_set()

    # ------------------------------------------------------------ interfaz
    def _construir(self):
        cuerpo = ttk.Frame(self, padding=12)
        cuerpo.pack(fill="both", expand=True)

        arriba = ttk.Frame(cuerpo)
        arriba.pack(fill="x")
        ttk.Label(arriba, text="Formato:").pack(side="left")
        ttk.Combobox(arriba, textvariable=self.formato, values=list(zpl.FORMATOS),
                     state="readonly", width=24).pack(side="left", padx=(4, 16))
        ttk.Label(arriba, text=f"Impresora: {self.imp_tipo} → {self.imp_destino}",
                  foreground="#555").pack(side="left")

        sw = ttk.Frame(cuerpo, padding=(0, 12))
        sw.pack(fill="x")
        self.lbl_unico = ttk.Label(sw, text="Un artículo", font=("", 10, "bold"))
        self.lbl_unico.pack(side="left")
        Interruptor(sw, self.modo_lista, self._aplicar_modo).pack(side="left", padx=8)
        self.lbl_lista = ttk.Label(sw, text=f"Lista de {FILAS_LISTA} artículos")
        self.lbl_lista.pack(side="left")

        # --- Modo un artículo
        self.marco_unico = ttk.LabelFrame(cuerpo, text=" Un artículo ", padding=10)
        self.marco_unico.pack(fill="x")
        ttk.Label(self.marco_unico, text="Código:").grid(row=0, column=0, sticky="w")
        self.cod_unico = ttk.Entry(self.marco_unico, width=22)
        self.cod_unico.grid(row=0, column=1, sticky="w", padx=4)
        ttk.Button(self.marco_unico, text="Buscar…",
                   command=lambda: self._dialogo_buscar(self.cod_unico, self.info_unico)
                   ).grid(row=0, column=2, padx=4)
        ttk.Label(self.marco_unico, text="Cantidad:").grid(row=0, column=3, sticky="e", padx=(16, 0))
        self.cantidad = ttk.Spinbox(self.marco_unico, from_=1, to=999, width=6)
        self.cantidad.set(1)
        self.cantidad.grid(row=0, column=4, sticky="w", padx=4)
        self.info_unico = ttk.Label(self.marco_unico, text="", width=70)
        self.info_unico.grid(row=1, column=0, columnspan=5, sticky="w", pady=(8, 0))
        self._enlazar(self.cod_unico, self.info_unico)

        # --- Modo lista
        self.marco_lista = ttk.LabelFrame(cuerpo, text=f" Lista de {FILAS_LISTA} artículos "
                                          "(1 etiqueta c/u — F2 para buscar) ", padding=10)
        self.marco_lista.pack(fill="x", pady=(10, 0))
        self.cod_lista: list[ttk.Entry] = []
        self.info_lista: list[ttk.Label] = []
        for i in range(FILAS_LISTA):
            ttk.Label(self.marco_lista, text=f"{i + 1:>2}.").grid(row=i, column=0, sticky="e")
            e = ttk.Entry(self.marco_lista, width=22)
            e.grid(row=i, column=1, padx=4, pady=1)
            info = ttk.Label(self.marco_lista, text="", width=60)
            info.grid(row=i, column=2, sticky="w")
            self._enlazar(e, info)
            e.bind("<F2>", lambda _ev, e=e, info=info: self._dialogo_buscar(e, info))
            self.cod_lista.append(e)
            self.info_lista.append(info)

        # --- Botones
        abajo = ttk.Frame(cuerpo, padding=(0, 12, 0, 0))
        abajo.pack(fill="x")
        ttk.Button(abajo, text="Imprimir", command=self.imprimir).pack(side="right")
        ttk.Button(abajo, text="Guardar ZPL…", command=self.guardar_zpl).pack(side="right", padx=6)
        ttk.Button(abajo, text="Limpiar", command=self.limpiar).pack(side="left")

        self.estado = ttk.Label(self, text="Listo", relief="sunken", anchor="w", padding=(6, 2))
        self.estado.pack(fill="x", side="bottom")
        self.bind("<Control-p>", lambda _e: self.imprimir())

    def _enlazar(self, entrada: ttk.Entry, info: ttk.Label):
        entrada.bind("<Return>", lambda _e: (self._mostrar(entrada, info), self._siguiente(entrada)))
        entrada.bind("<FocusOut>", lambda _e: self._mostrar(entrada, info))

    def _siguiente(self, entrada):
        if entrada is self.cod_unico:
            self.cantidad.focus_set()
            self.cantidad.selection_range(0, "end")
        else:
            entrada.tk_focusNext().focus_set()

    def _aplicar_modo(self):
        lista = self.modo_lista.get()
        for marco, activo in ((self.marco_unico, not lista), (self.marco_lista, lista)):
            for w in marco.winfo_children():
                try:
                    w.configure(state="normal" if activo else "disabled")
                except tk.TclError:
                    pass
        # Spinbox y Combobox vuelven a su estado adecuado
        self.lbl_unico.configure(font=("", 10, "normal" if lista else "bold"))
        self.lbl_lista.configure(font=("", 10, "bold" if lista else "normal"))
        (self.cod_lista[0] if lista else self.cod_unico).focus_set()

    # ------------------------------------------------------------ datos
    def _consultar(self, codigos: list[str]) -> dict[str, zpl.Producto | None]:
        faltan = [c for c in codigos if c.upper() not in self.cache]
        if faltan:
            encontrados = self.db.buscar_codigos(faltan)
            for c in faltan:
                self.cache[c.upper()] = encontrados.get(c.upper())
        return {c: self.cache[c.upper()] for c in codigos}

    def _mostrar(self, entrada: ttk.Entry, info: ttk.Label):
        codigo = entrada.get().strip()
        if not codigo:
            info.configure(text="")
            return
        try:
            p = self._consultar([codigo])[codigo]
        except Exception as e:  # noqa: BLE001 - se informa al usuario
            self._error_bd(e)
            return
        if p is None:
            info.configure(text="✗ No existe", foreground="#c62828")
        else:
            info.configure(text=f"{zpl.formatear_precio(p.precio, self.moneda)}   —   {p.nombre}",
                           foreground="")

    def _error_bd(self, e: Exception):
        self.estado.configure(text=f"Error de base de datos: {e}")
        messagebox.showerror("Base de datos", f"No se pudo consultar la base de datos:\n\n{e}")

    def _productos(self) -> list[zpl.Producto] | None:
        """Arma la lista de etiquetas según el modo activo."""
        if self.modo_lista.get():
            codigos = [e.get().strip() for e in self.cod_lista if e.get().strip()]
            copias = 1
        else:
            codigos = [self.cod_unico.get().strip()] if self.cod_unico.get().strip() else []
            try:
                copias = int(self.cantidad.get())
                if copias < 1:
                    raise ValueError
            except ValueError:
                messagebox.showwarning("Cantidad", "Indique una cantidad válida (1 o más).")
                return None
        if not codigos:
            messagebox.showwarning("Etiquetas", "Indique al menos un código de artículo.")
            return None
        try:
            resultado = self._consultar(codigos)
        except Exception as e:  # noqa: BLE001
            self._error_bd(e)
            return None
        faltan = [c for c, p in resultado.items() if p is None]
        productos = [zpl.Producto(**{**p.__dict__, "copias": copias})
                     for c in codigos if (p := resultado[c]) is not None]
        if faltan:
            msg = "Estos códigos no existen:\n  " + "\n  ".join(faltan)
            if not productos:
                messagebox.showwarning("Etiquetas", msg)
                return None
            if not messagebox.askyesno("Etiquetas", msg + "\n\n¿Imprimir los demás?"):
                return None
        return productos

    def _zpl(self, productos) -> str:
        return zpl.generar_lote(productos, zpl.FORMATOS[self.formato.get()], self.dpi, self.moneda)

    # ------------------------------------------------------------ acciones
    def imprimir(self):
        productos = self._productos()
        if not productos:
            return
        total = sum(p.copias for p in productos)
        self.estado.configure(text="Enviando a la impresora…")
        self.update_idletasks()
        try:
            zpl.enviar(self._zpl(productos), self.imp_tipo, self.imp_destino, self.imp_puerto)
        except Exception as e:  # noqa: BLE001
            self.estado.configure(text=f"Error al imprimir: {e}")
            messagebox.showerror("Impresora", f"No se pudo imprimir en {self.imp_destino}:\n\n{e}")
            return
        self.estado.configure(text=f"Enviadas {total} etiqueta(s) a {self.imp_destino}")

    def guardar_zpl(self):
        productos = self._productos()
        if not productos:
            return
        ruta = filedialog.asksaveasfilename(defaultextension=".zpl",
                                            filetypes=[("ZPL", "*.zpl"), ("Todos", "*.*")])
        if ruta:
            with open(ruta, "w", encoding="utf-8") as f:
                f.write(self._zpl(productos))
            self.estado.configure(text=f"ZPL guardado en {ruta}")

    def limpiar(self):
        for e, info in [(self.cod_unico, self.info_unico), *zip(self.cod_lista, self.info_lista)]:
            st = str(e.cget("state"))
            e.configure(state="normal")
            e.delete(0, "end")
            e.configure(state=st)
            info.configure(text="")
        self.cantidad.set(1)
        self.cache.clear()
        self.estado.configure(text="Listo")
        self._aplicar_modo()

    def _dialogo_buscar(self, destino: ttk.Entry, info: ttk.Label):
        """Ventana para buscar un artículo por descripción o código."""
        win = tk.Toplevel(self)
        win.title("Buscar artículo")
        win.transient(self)
        win.grab_set()
        texto = ttk.Entry(win, width=50)
        texto.pack(fill="x", padx=8, pady=8)
        tabla = ttk.Treeview(win, columns=("codigo", "nombre", "precio"), show="headings", height=15)
        for col, titulo, ancho in (("codigo", "Código", 130), ("nombre", "Descripción", 360),
                                   ("precio", "Precio", 100)):
            tabla.heading(col, text=titulo)
            tabla.column(col, width=ancho, anchor="e" if col == "precio" else "w")
        tabla.pack(fill="both", expand=True, padx=8, pady=(0, 8))

        ultima = {"texto": None}

        def buscar(_e=None):
            # Segundo Enter con el mismo texto: elige el artículo seleccionado
            if ultima["texto"] == texto.get() and tabla.selection():
                elegir()
                return
            ultima["texto"] = texto.get()
            tabla.delete(*tabla.get_children())
            if len(texto.get().strip()) < 2:
                return
            try:
                for p in self.db.buscar_descripcion(texto.get()):
                    self.cache[p.codigo.upper()] = p
                    tabla.insert("", "end", values=(p.codigo, p.nombre,
                                                    zpl.formatear_precio(p.precio, self.moneda)))
            except Exception as e:  # noqa: BLE001
                win.destroy()
                self._error_bd(e)
                return
            hijos = tabla.get_children()
            if hijos:
                tabla.selection_set(hijos[0])
                tabla.focus(hijos[0])

        def elegir(_e=None):
            sel = tabla.selection()
            if not sel:
                return
            destino.delete(0, "end")
            destino.insert(0, tabla.item(sel[0], "values")[0])
            win.destroy()
            self._mostrar(destino, info)
            self._siguiente(destino)

        texto.bind("<Return>", buscar)
        texto.bind("<Down>", lambda _e: tabla.focus_set())
        tabla.bind("<Double-1>", elegir)
        tabla.bind("<Return>", elegir)
        win.bind("<Escape>", lambda _e: win.destroy())
        texto.insert(0, destino.get())
        texto.focus_set()
        buscar()


def main():
    try:
        app = App()
    except Exception as e:  # noqa: BLE001
        raiz = tk.Tk()
        raiz.withdraw()
        messagebox.showerror("Etiquetas Zebra", str(e))
        return
    app.mainloop()


if __name__ == "__main__":
    main()
