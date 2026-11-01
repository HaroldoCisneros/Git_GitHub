package com.sighec.movilplus.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/* ======================= SEGURIDAD ======================= */

@Entity(tableName = "usuarios", indices = [Index(value = ["usuario"], unique = true)])
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuario: String,
    val nombre: String,
    val passwordHash: String,
    val salt: String,
    val esAdmin: Boolean = false,
    val activo: Boolean = true,
    /** Permite que el usuario entre con huella digital en este teléfono. */
    val permiteHuella: Boolean = true,
    /** Módulos permitidos separados por coma (ver [com.sighec.movilplus.seguridad.Modulo]). */
    val permisos: String = "",
    val creado: Long = System.currentTimeMillis()
)

@Entity(tableName = "bitacora", indices = [Index("fecha")])
data class Bitacora(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long = System.currentTimeMillis(),
    val usuario: String,
    val modulo: String,
    val accion: String,
    val detalle: String = ""
)

/* ======================= CATÁLOGOS ======================= */

@Entity(tableName = "articulos", indices = [Index(value = ["codigo"], unique = true)])
data class Articulo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigo: String,
    val descripcion: String,
    val unidad: String = "PZA",
    val precioVenta: Double = 0.0,
    val costo: Double = 0.0,
    val existencia: Double = 0.0,
    val tasaImpuesto: Double = 16.0,
    val activo: Boolean = true
)

@Entity(tableName = "condiciones_pago")
data class CondicionPago(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descripcion: String,
    val dias: Int = 0
)

@Entity(tableName = "clientes", indices = [Index(value = ["codigo"], unique = true)])
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigo: String,
    val nombre: String,
    val rfc: String = "",
    val telefono: String = "",
    val email: String = "",
    val direccion: String = "",
    val condicionPagoId: Long? = null,
    val limiteCredito: Double = 0.0,
    val activo: Boolean = true
)

@Entity(tableName = "proveedores", indices = [Index(value = ["codigo"], unique = true)])
data class Proveedor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigo: String,
    val nombre: String,
    val rfc: String = "",
    val telefono: String = "",
    val email: String = "",
    val direccion: String = "",
    val condicionPagoId: Long? = null,
    val activo: Boolean = true
)

@Entity(tableName = "bancos")
data class Banco(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val activo: Boolean = true
)

@Entity(tableName = "cuentas_bancarias", indices = [Index("bancoId")])
data class CuentaBancaria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bancoId: Long,
    val alias: String,
    val numero: String = "",
    val clabe: String = "",
    val saldoInicial: Double = 0.0,
    val saldo: Double = 0.0,
    val activo: Boolean = true
)

/* ======================= VENTAS ======================= */

object Estatus {
    const val VIGENTE = "VIGENTE"
    const val CANCELADA = "CANCELADA"
}

@Entity(tableName = "facturas", indices = [Index("clienteId"), Index(value = ["folio"], unique = true)])
data class Factura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folio: Long,
    val fecha: Long,
    val clienteId: Long,
    val condicionPagoId: Long? = null,
    val diasCredito: Int = 0,
    val vencimiento: Long,
    val subtotal: Double,
    val impuesto: Double,
    val total: Double,
    val saldo: Double,
    val estatus: String = Estatus.VIGENTE,
    val notas: String = "",
    val usuario: String = ""
)

@Entity(tableName = "factura_detalle", indices = [Index("facturaId"), Index("articuloId")])
data class FacturaDetalle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val facturaId: Long = 0,
    val articuloId: Long,
    val descripcion: String,
    val cantidad: Double,
    val precio: Double,
    val tasaImpuesto: Double,
    val importe: Double
)

object FormaPago {
    const val EFECTIVO = "EFECTIVO"
    const val TRANSFERENCIA = "TRANSFERENCIA"
    const val CHEQUE = "CHEQUE"
    const val TARJETA = "TARJETA"
    val todas = listOf(EFECTIVO, TRANSFERENCIA, CHEQUE, TARJETA)
}

@Entity(tableName = "cobros", indices = [Index("clienteId"), Index("facturaId")])
data class Cobro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val clienteId: Long,
    val facturaId: Long,
    val importe: Double,
    val formaPago: String,
    /** Cuenta bancaria destino cuando no es efectivo. */
    val cuentaId: Long? = null,
    val referencia: String = "",
    val usuario: String = ""
)

/* ======================= COMPRAS ======================= */

@Entity(tableName = "compras", indices = [Index("proveedorId")])
data class Compra(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Número de factura del proveedor. */
    val folio: String,
    val fecha: Long,
    val proveedorId: Long,
    val condicionPagoId: Long? = null,
    val diasCredito: Int = 0,
    val vencimiento: Long,
    val subtotal: Double,
    val impuesto: Double,
    val total: Double,
    val saldo: Double,
    val estatus: String = Estatus.VIGENTE,
    val notas: String = "",
    val usuario: String = ""
)

@Entity(tableName = "compra_detalle", indices = [Index("compraId"), Index("articuloId")])
data class CompraDetalle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val compraId: Long = 0,
    val articuloId: Long,
    val descripcion: String,
    val cantidad: Double,
    val costo: Double,
    val tasaImpuesto: Double,
    val importe: Double
)

@Entity(tableName = "pagos", indices = [Index("proveedorId"), Index("compraId")])
data class Pago(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val proveedorId: Long,
    val compraId: Long,
    val importe: Double,
    val formaPago: String,
    /** Cuenta bancaria origen cuando no es efectivo. */
    val cuentaId: Long? = null,
    val referencia: String = "",
    val usuario: String = ""
)

/* ======================= TESORERÍA ======================= */

object TipoMovimiento {
    const val ENTRADA = "ENTRADA"   // depósito en banco / ingreso a caja
    const val SALIDA = "SALIDA"     // retiro en banco / egreso de caja
}

@Entity(tableName = "movimientos_banco", indices = [Index("cuentaId"), Index("fecha")])
data class MovimientoBanco(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cuentaId: Long,
    val fecha: Long,
    val tipo: String,
    val concepto: String,
    val importe: Double,
    val referencia: String = "",
    val origen: String = "MANUAL",
    val usuario: String = ""
)

@Entity(tableName = "movimientos_caja", indices = [Index("fecha")])
data class MovimientoCaja(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val tipo: String,
    val concepto: String,
    val importe: Double,
    val referencia: String = "",
    val origen: String = "MANUAL",
    val usuario: String = ""
)

/* ======================= CONSULTAS ======================= */

data class FacturaConCliente(
    @Embedded val factura: Factura,
    val clienteNombre: String
)

data class CompraConProveedor(
    @Embedded val compra: Compra,
    val proveedorNombre: String
)

data class CobroConDatos(
    @Embedded val cobro: Cobro,
    val clienteNombre: String,
    val facturaFolio: Long
)

data class PagoConDatos(
    @Embedded val pago: Pago,
    val proveedorNombre: String,
    val compraFolio: String
)

data class CuentaConBanco(
    @Embedded val cuenta: CuentaBancaria,
    val bancoNombre: String
)

/** Resumen de saldo por cliente o proveedor (CxC / CxP). */
data class SaldoTercero(
    val id: Long,
    val nombre: String,
    val documentos: Int,
    val saldo: Double,
    val vencido: Double
)
