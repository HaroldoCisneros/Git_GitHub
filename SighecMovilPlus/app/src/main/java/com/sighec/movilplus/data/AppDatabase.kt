package com.sighec.movilplus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Usuario::class, Bitacora::class,
        Articulo::class, CondicionPago::class, Cliente::class, Proveedor::class,
        Banco::class, CuentaBancaria::class,
        Factura::class, FacturaDetalle::class, Cobro::class,
        Compra::class, CompraDetalle::class, Pago::class,
        MovimientoBanco::class, MovimientoCaja::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarios(): UsuarioDao
    abstract fun bitacora(): BitacoraDao
    abstract fun articulos(): ArticuloDao
    abstract fun condiciones(): CondicionPagoDao
    abstract fun clientes(): ClienteDao
    abstract fun proveedores(): ProveedorDao
    abstract fun bancos(): BancoDao
    abstract fun cuentas(): CuentaDao
    abstract fun movimientosBanco(): MovimientoBancoDao
    abstract fun caja(): MovimientoCajaDao
    abstract fun facturas(): FacturaDao
    abstract fun cobros(): CobroDao
    abstract fun compras(): CompraDao
    abstract fun pagos(): PagoDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sighec_movil_plus.db").build()
    }
}
