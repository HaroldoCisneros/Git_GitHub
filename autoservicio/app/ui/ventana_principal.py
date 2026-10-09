"""
Pantalla principal: escaneo de artículos.

Flujo de la pantalla:
    1. Al abrir (y cada vez que se termina o cancela una factura) se crea una
       factura NUEVA y se pide la cédula del cliente con el teclado numérico.
    2. Con el cliente identificado, el lector de códigos de barras escribe
       el código en el campo de escaneo y manda "Enter"; el artículo se busca
       en Profit y se agrega a la tabla.
    3. Si el código no se puede leer, el botón "Teclear código" abre el
       teclado para escribirlo a mano.
    4. "Quitar artículo" elimina el renglón seleccionado.
    5. "Cancelar compra" descarta la factura y empieza una nueva.
    6. "Finalizar compra": PENDIENTE (grabar la factura en Profit).
    7. "Salir" pide la contraseña de salida.

Sobre el lector de códigos de barras:
    Se asume que funciona como un teclado (modo HID): escribe los números y
    al final envía Enter. Por eso el campo de escaneo debe tener SIEMPRE el
    foco; después de cada diálogo se le devuelve el foco.
"""

from __future__ import annotations

import logging
import os
from decimal import Decimal

from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (QAbstractItemView, QFrame, QHBoxLayout, QHeaderView, QLabel,
                               QLineEdit, QPushButton, QTableWidget, QTableWidgetItem,
                               QVBoxLayout)

from ..base_datos import ErrorBaseDatos
from ..configuracion import Configuracion
from ..modelos import Factura, Sesion
from ..repositorios import RepositorioArticulos, RepositorioClientes
from ..utilidades import formatear_monto
from .dialogos import (MODO_ALFANUMERICO, MODO_NUMERICO, confirmar, mostrar_mensaje,
                       pedir_texto)
from .ventana_kiosco import VentanaKiosco

log = logging.getLogger("autoservicio.principal")

# Columnas de la tabla de artículos.
COLUMNAS = ["Código", "Descripción", "Cant.", "Precio", "Total"]
COL_CODIGO, COL_DESCRIPCION, COL_CANTIDAD, COL_PRECIO, COL_TOTAL = range(len(COLUMNAS))

# Alto de cada renglón de la tabla (grande para tocarlo con el dedo).
ALTO_RENGLON = 56
# Alto del logo en la barra superior.
ALTO_LOGO = 70


class VentanaPrincipal(VentanaKiosco):
    """Pantalla donde el cliente escanea sus artículos."""

    def __init__(self, config: Configuracion, sesion: Sesion,
                 repo_clientes: RepositorioClientes, repo_articulos: RepositorioArticulos):
        """
        :param sesion: caja, usuario y ambiente (PPV_AMBIENTE) validados al arrancar.
            Los parámetros del ambiente se leen con ``self._sesion.ambiente``.
        """
        super().__init__(config.seguridad.clave_salida)
        self._config = config
        self._sesion = sesion
        self._caja = sesion.caja
        self._usuario = sesion.usuario
        self._repo_clientes = repo_clientes
        self._repo_articulos = repo_articulos
        self._factura = Factura(caja=self._caja, usuario=self._usuario)
        # Para pedir la cédula solo la primera vez que se muestra la ventana.
        self._primera_vez = True

        self._construir()
        self._refrescar()

    # ==================================================================
    # Construcción de la pantalla
    # ==================================================================

    def _construir(self) -> None:
        """Arma las tres zonas: barra superior, tabla de artículos y barra inferior."""
        principal = QVBoxLayout(self)
        principal.setContentsMargins(20, 16, 20, 16)
        principal.setSpacing(12)

        principal.addWidget(self._construir_barra_superior())
        principal.addWidget(self._construir_barra_cliente())
        principal.addLayout(self._construir_zona_escaneo())
        principal.addWidget(self._construir_tabla(), stretch=1)
        principal.addWidget(self._construir_barra_inferior())

    def _construir_barra_superior(self) -> QFrame:
        """Logo, caja, usuario y botón Salir."""
        panel = QFrame()
        panel.setObjectName("panel")
        layout = QHBoxLayout(panel)

        # Logo o nombre del negocio.
        logo = QLabel()
        ruta_logo = self._config.rutas.logo
        if ruta_logo and os.path.isfile(ruta_logo):
            logo.setPixmap(QPixmap(ruta_logo).scaledToHeight(ALTO_LOGO, Qt.SmoothTransformation))
        else:
            logo.setText(self._config.pantalla.nombre_negocio)
            logo.setObjectName("titulo")
        layout.addWidget(logo)
        layout.addStretch()

        datos = QLabel(f"Caja {self._caja.codigo} - {self._caja.descripcion}\n"
                       f"Usuario: {self._usuario.nombre or self._usuario.codigo}")
        datos.setObjectName("subtitulo")
        datos.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        layout.addWidget(datos)

        salir = QPushButton("Salir")
        salir.setProperty("tipo", "peligro")
        salir.setFocusPolicy(Qt.NoFocus)
        salir.clicked.connect(self._al_salir)
        layout.addWidget(salir)
        return panel

    def _construir_barra_cliente(self) -> QFrame:
        """Muestra el cliente de la factura y permite cambiarlo."""
        panel = QFrame()
        panel.setObjectName("panel")
        layout = QHBoxLayout(panel)

        self.etiqueta_cliente = QLabel()
        self.etiqueta_cliente.setStyleSheet("font-size: 24px; font-weight: bold;")
        layout.addWidget(self.etiqueta_cliente, stretch=1)

        self.boton_cliente = QPushButton("Ingresar cédula")
        self.boton_cliente.setFocusPolicy(Qt.NoFocus)
        self.boton_cliente.clicked.connect(self._pedir_cliente)
        layout.addWidget(self.boton_cliente)
        return panel

    def _construir_zona_escaneo(self) -> QHBoxLayout:
        """Campo que recibe el lector de códigos de barras + botón para teclear."""
        layout = QHBoxLayout()

        etiqueta = QLabel("Escanee el producto:")
        etiqueta.setStyleSheet("font-size: 24px;")
        layout.addWidget(etiqueta)

        self.campo_escaner = QLineEdit()
        self.campo_escaner.setPlaceholderText("Código de barras")
        self.campo_escaner.returnPressed.connect(self._al_escanear)
        layout.addWidget(self.campo_escaner, stretch=1)

        teclear = QPushButton("Teclear código")
        teclear.setFocusPolicy(Qt.NoFocus)
        teclear.clicked.connect(self._teclear_codigo)
        layout.addWidget(teclear)
        return layout

    def _construir_tabla(self) -> QTableWidget:
        """Tabla con los artículos escaneados."""
        self.tabla = QTableWidget(0, len(COLUMNAS))
        self.tabla.setHorizontalHeaderLabels(COLUMNAS)
        self.tabla.setEditTriggers(QAbstractItemView.NoEditTriggers)        # Solo lectura.
        self.tabla.setSelectionBehavior(QAbstractItemView.SelectRows)       # Selecciona renglón completo.
        self.tabla.setSelectionMode(QAbstractItemView.SingleSelection)
        self.tabla.setFocusPolicy(Qt.NoFocus)                               # El foco es del escáner.
        self.tabla.verticalHeader().setVisible(False)
        self.tabla.verticalHeader().setDefaultSectionSize(ALTO_RENGLON)

        # La descripción ocupa el espacio sobrante; las demás se ajustan al contenido.
        cabecera = self.tabla.horizontalHeader()
        for columna in range(len(COLUMNAS)):
            cabecera.setSectionResizeMode(columna, QHeaderView.ResizeToContents)
        cabecera.setSectionResizeMode(COL_DESCRIPCION, QHeaderView.Stretch)
        return self.tabla

    def _construir_barra_inferior(self) -> QFrame:
        """Botones de acción y total de la factura."""
        panel = QFrame()
        panel.setObjectName("panel")
        layout = QHBoxLayout(panel)

        self.boton_quitar = QPushButton("Quitar artículo")
        self.boton_quitar.setProperty("tipo", "peligro")
        self.boton_quitar.setFocusPolicy(Qt.NoFocus)
        self.boton_quitar.clicked.connect(self._quitar_articulo)
        layout.addWidget(self.boton_quitar)

        self.boton_cancelar = QPushButton("Cancelar compra")
        self.boton_cancelar.setProperty("tipo", "peligro")
        self.boton_cancelar.setFocusPolicy(Qt.NoFocus)
        self.boton_cancelar.clicked.connect(self._cancelar_factura)
        layout.addWidget(self.boton_cancelar)

        layout.addStretch()

        # Cantidad de artículos y total.
        totales = QVBoxLayout()
        self.etiqueta_articulos = QLabel()
        self.etiqueta_articulos.setObjectName("subtitulo")
        self.etiqueta_articulos.setAlignment(Qt.AlignRight)
        self.etiqueta_total = QLabel()
        self.etiqueta_total.setObjectName("total")
        self.etiqueta_total.setAlignment(Qt.AlignRight)
        totales.addWidget(self.etiqueta_articulos)
        totales.addWidget(self.etiqueta_total)
        layout.addLayout(totales)

        self.boton_finalizar = QPushButton("Finalizar compra")
        self.boton_finalizar.setProperty("tipo", "exito")
        self.boton_finalizar.setFocusPolicy(Qt.NoFocus)
        self.boton_finalizar.clicked.connect(self._finalizar_factura)
        layout.addWidget(self.boton_finalizar)
        return panel

    # ==================================================================
    # Eventos de la ventana
    # ==================================================================

    def showEvent(self, evento) -> None:
        """
        La primera vez que aparece la ventana se pide la cédula.
        Se usa un QTimer para que el diálogo salga cuando la ventana ya está dibujada.
        """
        super().showEvent(evento)
        if self._primera_vez:
            self._primera_vez = False
            QTimer.singleShot(0, self._pedir_cliente)

    # ==================================================================
    # Factura y cliente
    # ==================================================================

    def _nueva_factura(self) -> None:
        """Descarta la factura actual, crea una nueva y pide la cédula."""
        self._factura = Factura(caja=self._caja, usuario=self._usuario)
        self._refrescar()
        log.info("Nueva factura en caja %s", self._caja.codigo)
        self._pedir_cliente()

    def _pedir_cliente(self) -> None:
        """
        Pide la cédula con el teclado numérico y busca al cliente en Profit.
        Si no existe, lo informa y vuelve a preguntar. Si se toca Cancelar,
        la factura queda sin cliente (no se podrá escanear hasta indicarlo).
        """
        while True:
            cedula = pedir_texto(self, "Bienvenido",
                                 "Introduzca su número de cédula para comenzar",
                                 modo=MODO_NUMERICO)
            if cedula is None:          # Canceló.
                break

            try:
                cliente = self._repo_clientes.buscar_por_cedula(cedula)
            except ErrorBaseDatos as error:
                mostrar_mensaje(self, "Error", str(error), es_error=True)
                break

            if cliente is None:
                # PENDIENTE: definir si se crea el cliente o se usa uno genérico.
                mostrar_mensaje(self, "Cliente no registrado",
                                f"No se encontró la cédula {cedula}.\n"
                                "Verifique el número o diríjase a una caja atendida.",
                                es_error=True)
                continue

            self._factura.cliente = cliente
            log.info("Cliente de la factura: %s (%s)", cliente.codigo, cliente.nombre)
            break

        self._refrescar()

    # ==================================================================
    # Escaneo de artículos
    # ==================================================================

    def _al_escanear(self) -> None:
        """El lector envió Enter: se procesa el código y se limpia el campo."""
        codigo = self.campo_escaner.text().strip()
        self.campo_escaner.clear()
        if codigo:
            self._agregar_por_codigo(codigo)

    def _teclear_codigo(self) -> None:
        """Abre el teclado para escribir el código a mano."""
        codigo = pedir_texto(self, "Teclear código",
                             "Escriba el código de barras del producto",
                             modo=MODO_ALFANUMERICO)
        if codigo:
            self._agregar_por_codigo(codigo)
        self._enfocar_escaner()

    def _agregar_por_codigo(self, codigo: str) -> None:
        """Busca el artículo en Profit y lo agrega a la factura."""
        if self._factura.cliente is None:
            mostrar_mensaje(self, "Falta la cédula",
                            "Antes de escanear debe ingresar su número de cédula.")
            self._pedir_cliente()
            return

        try:
            articulo = self._repo_articulos.buscar_por_codigo(codigo)
        except ErrorBaseDatos as error:
            mostrar_mensaje(self, "Error", str(error), es_error=True)
            self._enfocar_escaner()
            return

        if articulo is None:
            log.info("Código no encontrado: %s", codigo)
            mostrar_mensaje(self, "Producto no encontrado",
                            f"El código {codigo} no está registrado.", es_error=True)
            self._enfocar_escaner()
            return

        linea = self._factura.agregar_articulo(articulo)
        self._refrescar()
        # Selecciona el renglón afectado para que el cliente vea qué se agregó.
        self.tabla.selectRow(self._factura.lineas.index(linea))

    def _quitar_articulo(self) -> None:
        """Quita el renglón seleccionado (pide confirmación)."""
        fila = self.tabla.currentRow()
        if fila < 0:
            mostrar_mensaje(self, "Quitar artículo",
                            "Toque primero el artículo que desea quitar.")
        else:
            descripcion = self._factura.lineas[fila].articulo.descripcion
            if confirmar(self, "Quitar artículo", f"¿Desea quitar\n{descripcion}?"):
                self._factura.quitar_linea(fila)
                self._refrescar()
        self._enfocar_escaner()

    def _cancelar_factura(self) -> None:
        """Descarta toda la compra (pide confirmación) y empieza una nueva."""
        if confirmar(self, "Cancelar compra", "¿Desea cancelar toda la compra?"):
            log.info("Factura cancelada por el cliente")
            self._nueva_factura()
        self._enfocar_escaner()

    def _finalizar_factura(self) -> None:
        """
        PENDIENTE: grabar la factura en Profit Plus 2K8.

        Falta definir si se graba directo en las tablas factura / reng_fac
        o como un documento intermedio. Mientras tanto solo se avisa.
        """
        mostrar_mensaje(self, "En construcción",
                        "El grabado de la factura en Profit todavía no está implementado.")
        self._enfocar_escaner()

    def _al_salir(self) -> None:
        """Botón Salir: pide la contraseña (ver VentanaKiosco)."""
        self.solicitar_salida()
        self._enfocar_escaner()

    # ==================================================================
    # Actualización de la pantalla
    # ==================================================================

    def _monto(self, valor: Decimal) -> str:
        """Formatea un monto con la configuración de <pantalla>."""
        p = self._config.pantalla
        return formatear_monto(valor, p.decimales, p.simbolo_moneda,
                               p.separador_miles, p.separador_decimal)

    def _cantidad(self, valor: Decimal) -> str:
        """Muestra la cantidad sin decimales si es entera (3 en vez de 3,00)."""
        if valor == valor.to_integral_value():
            return str(int(valor))
        p = self._config.pantalla
        return formatear_monto(valor, p.decimales, "", p.separador_miles, p.separador_decimal)

    def _refrescar(self) -> None:
        """Vuelve a dibujar el cliente, la tabla, los totales y el estado de los botones."""
        factura = self._factura

        # --- Cliente ---
        if factura.cliente:
            self.etiqueta_cliente.setText(f"Cliente: {factura.cliente.nombre}  ({factura.cliente.rif})")
            self.boton_cliente.setText("Cambiar cliente")
        else:
            self.etiqueta_cliente.setText("Toque \"Ingresar cédula\" para comenzar")
            self.boton_cliente.setText("Ingresar cédula")

        # --- Tabla de artículos ---
        self.tabla.setRowCount(len(factura.lineas))
        for fila, linea in enumerate(factura.lineas):
            valores = {
                COL_CODIGO: linea.articulo.codigo,
                COL_DESCRIPCION: linea.articulo.descripcion,
                COL_CANTIDAD: self._cantidad(linea.cantidad),
                COL_PRECIO: self._monto(linea.articulo.precio),
                COL_TOTAL: self._monto(linea.total),
            }
            for columna, texto in valores.items():
                celda = QTableWidgetItem(texto)
                if columna in (COL_CANTIDAD, COL_PRECIO, COL_TOTAL):
                    celda.setTextAlignment(Qt.AlignRight | Qt.AlignVCenter)
                self.tabla.setItem(fila, columna, celda)

        # --- Totales ---
        self.etiqueta_articulos.setText(f"Artículos: {self._cantidad(factura.cantidad_articulos)}")
        self.etiqueta_total.setText(f"Total: {self._monto(factura.total)}")

        # --- Botones: solo activos si hay artículos ---
        hay_articulos = not factura.vacia
        self.boton_quitar.setEnabled(hay_articulos)
        self.boton_finalizar.setEnabled(hay_articulos)

        self._enfocar_escaner()

    def _enfocar_escaner(self) -> None:
        """Devuelve el foco al campo del lector de códigos de barras."""
        self.campo_escaner.setFocus()
