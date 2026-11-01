package com.sighec.movilplus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios ORDER BY usuario")
    fun observarTodos(): Flow<List<Usuario>>

    @Query("SELECT * FROM usuarios WHERE usuario = :usuario COLLATE NOCASE LIMIT 1")
    suspend fun porUsuario(usuario: String): Usuario?

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun porId(id: Long): Usuario?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contar(): Int

    @Query("SELECT COUNT(*) FROM usuarios WHERE esAdmin = 1 AND activo = 1")
    suspend fun contarAdminsActivos(): Int

    @Insert suspend fun insertar(u: Usuario): Long
    @Update suspend fun actualizar(u: Usuario)
    @Delete suspend fun eliminar(u: Usuario)
}

@Dao
interface BitacoraDao {
    @Query("SELECT * FROM bitacora WHERE (:q = '' OR usuario LIKE '%' || :q || '%' OR modulo LIKE '%' || :q || '%' OR accion LIKE '%' || :q || '%' OR detalle LIKE '%' || :q || '%') ORDER BY fecha DESC LIMIT 1000")
    fun observar(q: String): Flow<List<Bitacora>>

    @Insert suspend fun insertar(b: Bitacora)
}

@Dao
interface ArticuloDao {
    @Query("SELECT * FROM articulos WHERE (:q = '' OR codigo LIKE '%' || :q || '%' OR descripcion LIKE '%' || :q || '%') ORDER BY descripcion")
    fun observar(q: String): Flow<List<Articulo>>

    @Query("SELECT * FROM articulos WHERE activo = 1 ORDER BY descripcion")
    fun observarActivos(): Flow<List<Articulo>>

    @Query("SELECT * FROM articulos WHERE id = :id")
    suspend fun porId(id: Long): Articulo?

    @Query("UPDATE articulos SET existencia = existencia + :delta WHERE id = :id")
    suspend fun ajustarExistencia(id: Long, delta: Double)

    @Query("UPDATE articulos SET costo = :costo WHERE id = :id")
    suspend fun actualizarCosto(id: Long, costo: Double)

    @Query("SELECT (SELECT COUNT(*) FROM factura_detalle WHERE articuloId = :id) + (SELECT COUNT(*) FROM compra_detalle WHERE articuloId = :id)")
    suspend fun usos(id: Long): Int

    @Insert suspend fun insertar(a: Articulo): Long
    @Update suspend fun actualizar(a: Articulo)
    @Delete suspend fun eliminar(a: Articulo)
}

@Dao
interface CondicionPagoDao {
    @Query("SELECT * FROM condiciones_pago ORDER BY dias, descripcion")
    fun observar(): Flow<List<CondicionPago>>

    @Query("SELECT * FROM condiciones_pago WHERE id = :id")
    suspend fun porId(id: Long): CondicionPago?

    @Query("SELECT (SELECT COUNT(*) FROM clientes WHERE condicionPagoId = :id) + (SELECT COUNT(*) FROM proveedores WHERE condicionPagoId = :id) + (SELECT COUNT(*) FROM facturas WHERE condicionPagoId = :id) + (SELECT COUNT(*) FROM compras WHERE condicionPagoId = :id)")
    suspend fun usos(id: Long): Int

    @Insert suspend fun insertar(c: CondicionPago): Long
    @Update suspend fun actualizar(c: CondicionPago)
    @Delete suspend fun eliminar(c: CondicionPago)
}

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes WHERE (:q = '' OR codigo LIKE '%' || :q || '%' OR nombre LIKE '%' || :q || '%' OR rfc LIKE '%' || :q || '%') ORDER BY nombre")
    fun observar(q: String): Flow<List<Cliente>>

    @Query("SELECT * FROM clientes WHERE activo = 1 ORDER BY nombre")
    fun observarActivos(): Flow<List<Cliente>>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun porId(id: Long): Cliente?

    @Query("SELECT COUNT(*) FROM facturas WHERE clienteId = :id")
    suspend fun usos(id: Long): Int

    @Insert suspend fun insertar(c: Cliente): Long
    @Update suspend fun actualizar(c: Cliente)
    @Delete suspend fun eliminar(c: Cliente)
}

@Dao
interface ProveedorDao {
    @Query("SELECT * FROM proveedores WHERE (:q = '' OR codigo LIKE '%' || :q || '%' OR nombre LIKE '%' || :q || '%' OR rfc LIKE '%' || :q || '%') ORDER BY nombre")
    fun observar(q: String): Flow<List<Proveedor>>

    @Query("SELECT * FROM proveedores WHERE activo = 1 ORDER BY nombre")
    fun observarActivos(): Flow<List<Proveedor>>

    @Query("SELECT * FROM proveedores WHERE id = :id")
    suspend fun porId(id: Long): Proveedor?

    @Query("SELECT COUNT(*) FROM compras WHERE proveedorId = :id")
    suspend fun usos(id: Long): Int

    @Insert suspend fun insertar(p: Proveedor): Long
    @Update suspend fun actualizar(p: Proveedor)
    @Delete suspend fun eliminar(p: Proveedor)
}

@Dao
interface BancoDao {
    @Query("SELECT * FROM bancos ORDER BY nombre")
    fun observar(): Flow<List<Banco>>

    @Query("SELECT * FROM bancos WHERE id = :id")
    suspend fun porId(id: Long): Banco?

    @Query("SELECT COUNT(*) FROM cuentas_bancarias WHERE bancoId = :id")
    suspend fun usos(id: Long): Int

    @Insert suspend fun insertar(b: Banco): Long
    @Update suspend fun actualizar(b: Banco)
    @Delete suspend fun eliminar(b: Banco)
}

@Dao
interface CuentaDao {
    @Query("SELECT c.*, b.nombre AS bancoNombre FROM cuentas_bancarias c INNER JOIN bancos b ON b.id = c.bancoId ORDER BY b.nombre, c.alias")
    fun observar(): Flow<List<CuentaConBanco>>

    @Query("SELECT c.*, b.nombre AS bancoNombre FROM cuentas_bancarias c INNER JOIN bancos b ON b.id = c.bancoId WHERE c.activo = 1 ORDER BY b.nombre, c.alias")
    fun observarActivas(): Flow<List<CuentaConBanco>>

    @Query("SELECT * FROM cuentas_bancarias WHERE id = :id")
    suspend fun porId(id: Long): CuentaBancaria?

    @Query("SELECT * FROM cuentas_bancarias WHERE id = :id")
    fun observarPorId(id: Long): Flow<CuentaBancaria?>

    @Query("UPDATE cuentas_bancarias SET saldo = saldo + :delta WHERE id = :id")
    suspend fun ajustarSaldo(id: Long, delta: Double)

    @Query("SELECT COUNT(*) FROM movimientos_banco WHERE cuentaId = :id")
    suspend fun usos(id: Long): Int

    @Insert suspend fun insertar(c: CuentaBancaria): Long
    @Update suspend fun actualizar(c: CuentaBancaria)
    @Delete suspend fun eliminar(c: CuentaBancaria)
}

@Dao
interface MovimientoBancoDao {
    @Query("SELECT * FROM movimientos_banco WHERE cuentaId = :cuentaId ORDER BY fecha DESC, id DESC")
    fun observarDeCuenta(cuentaId: Long): Flow<List<MovimientoBanco>>

    @Insert suspend fun insertar(m: MovimientoBanco): Long
}

@Dao
interface MovimientoCajaDao {
    @Query("SELECT * FROM movimientos_caja WHERE fecha BETWEEN :desde AND :hasta ORDER BY fecha DESC, id DESC")
    fun observar(desde: Long, hasta: Long): Flow<List<MovimientoCaja>>

    @Query("SELECT COALESCE(SUM(CASE WHEN tipo = 'ENTRADA' THEN importe ELSE -importe END), 0) FROM movimientos_caja")
    fun observarSaldo(): Flow<Double>

    @Insert suspend fun insertar(m: MovimientoCaja): Long
}

@Dao
interface FacturaDao {
    @Query("SELECT f.*, c.nombre AS clienteNombre FROM facturas f INNER JOIN clientes c ON c.id = f.clienteId WHERE (:q = '' OR c.nombre LIKE '%' || :q || '%' OR CAST(f.folio AS TEXT) LIKE '%' || :q || '%') ORDER BY f.fecha DESC, f.id DESC")
    fun observar(q: String): Flow<List<FacturaConCliente>>

    @Query("SELECT f.*, c.nombre AS clienteNombre FROM facturas f INNER JOIN clientes c ON c.id = f.clienteId WHERE f.estatus = 'VIGENTE' AND f.saldo > 0.005 AND (:clienteId = 0 OR f.clienteId = :clienteId) ORDER BY f.vencimiento, f.folio")
    fun observarPendientes(clienteId: Long): Flow<List<FacturaConCliente>>

    @Query("""SELECT c.id AS id, c.nombre AS nombre, COUNT(f.id) AS documentos, SUM(f.saldo) AS saldo,
              SUM(CASE WHEN f.vencimiento < :hoy THEN f.saldo ELSE 0 END) AS vencido
              FROM facturas f INNER JOIN clientes c ON c.id = f.clienteId
              WHERE f.estatus = 'VIGENTE' AND f.saldo > 0.005
              GROUP BY c.id, c.nombre ORDER BY c.nombre""")
    fun observarSaldosPorCliente(hoy: Long): Flow<List<SaldoTercero>>

    @Query("SELECT * FROM facturas WHERE id = :id")
    suspend fun porId(id: Long): Factura?

    @Query("SELECT COALESCE(SUM(saldo), 0) FROM facturas WHERE clienteId = :clienteId AND estatus = 'VIGENTE'")
    suspend fun saldoCliente(clienteId: Long): Double

    @Query("SELECT f.*, c.nombre AS clienteNombre FROM facturas f INNER JOIN clientes c ON c.id = f.clienteId WHERE f.id = :id")
    fun observarPorId(id: Long): Flow<FacturaConCliente?>

    @Query("SELECT * FROM factura_detalle WHERE facturaId = :facturaId ORDER BY id")
    fun observarDetalles(facturaId: Long): Flow<List<FacturaDetalle>>

    @Query("SELECT * FROM factura_detalle WHERE facturaId = :facturaId")
    suspend fun detalles(facturaId: Long): List<FacturaDetalle>

    @Query("SELECT COALESCE(MAX(folio), 0) + 1 FROM facturas")
    suspend fun siguienteFolio(): Long

    @Query("UPDATE facturas SET saldo = saldo + :delta WHERE id = :id")
    suspend fun ajustarSaldo(id: Long, delta: Double)

    @Insert suspend fun insertar(f: Factura): Long
    @Insert suspend fun insertarDetalles(d: List<FacturaDetalle>)
    @Update suspend fun actualizar(f: Factura)
}

@Dao
interface CobroDao {
    @Query("SELECT p.*, c.nombre AS clienteNombre, f.folio AS facturaFolio FROM cobros p INNER JOIN clientes c ON c.id = p.clienteId INNER JOIN facturas f ON f.id = p.facturaId ORDER BY p.fecha DESC, p.id DESC")
    fun observar(): Flow<List<CobroConDatos>>

    @Query("SELECT p.*, c.nombre AS clienteNombre, f.folio AS facturaFolio FROM cobros p INNER JOIN clientes c ON c.id = p.clienteId INNER JOIN facturas f ON f.id = p.facturaId WHERE p.facturaId = :facturaId ORDER BY p.fecha")
    fun observarDeFactura(facturaId: Long): Flow<List<CobroConDatos>>

    @Query("SELECT COUNT(*) FROM cobros WHERE facturaId = :facturaId")
    suspend fun contarDeFactura(facturaId: Long): Int

    @Insert suspend fun insertar(c: Cobro): Long
}

@Dao
interface CompraDao {
    @Query("SELECT f.*, p.nombre AS proveedorNombre FROM compras f INNER JOIN proveedores p ON p.id = f.proveedorId WHERE (:q = '' OR p.nombre LIKE '%' || :q || '%' OR f.folio LIKE '%' || :q || '%') ORDER BY f.fecha DESC, f.id DESC")
    fun observar(q: String): Flow<List<CompraConProveedor>>

    @Query("SELECT f.*, p.nombre AS proveedorNombre FROM compras f INNER JOIN proveedores p ON p.id = f.proveedorId WHERE f.estatus = 'VIGENTE' AND f.saldo > 0.005 AND (:proveedorId = 0 OR f.proveedorId = :proveedorId) ORDER BY f.vencimiento, f.id")
    fun observarPendientes(proveedorId: Long): Flow<List<CompraConProveedor>>

    @Query("""SELECT p.id AS id, p.nombre AS nombre, COUNT(f.id) AS documentos, SUM(f.saldo) AS saldo,
              SUM(CASE WHEN f.vencimiento < :hoy THEN f.saldo ELSE 0 END) AS vencido
              FROM compras f INNER JOIN proveedores p ON p.id = f.proveedorId
              WHERE f.estatus = 'VIGENTE' AND f.saldo > 0.005
              GROUP BY p.id, p.nombre ORDER BY p.nombre""")
    fun observarSaldosPorProveedor(hoy: Long): Flow<List<SaldoTercero>>

    @Query("SELECT * FROM compras WHERE id = :id")
    suspend fun porId(id: Long): Compra?

    @Query("SELECT f.*, p.nombre AS proveedorNombre FROM compras f INNER JOIN proveedores p ON p.id = f.proveedorId WHERE f.id = :id")
    fun observarPorId(id: Long): Flow<CompraConProveedor?>

    @Query("SELECT * FROM compra_detalle WHERE compraId = :compraId ORDER BY id")
    fun observarDetalles(compraId: Long): Flow<List<CompraDetalle>>

    @Query("SELECT * FROM compra_detalle WHERE compraId = :compraId")
    suspend fun detalles(compraId: Long): List<CompraDetalle>

    @Query("UPDATE compras SET saldo = saldo + :delta WHERE id = :id")
    suspend fun ajustarSaldo(id: Long, delta: Double)

    @Insert suspend fun insertar(c: Compra): Long
    @Insert suspend fun insertarDetalles(d: List<CompraDetalle>)
    @Update suspend fun actualizar(c: Compra)
}

@Dao
interface PagoDao {
    @Query("SELECT p.*, v.nombre AS proveedorNombre, f.folio AS compraFolio FROM pagos p INNER JOIN proveedores v ON v.id = p.proveedorId INNER JOIN compras f ON f.id = p.compraId ORDER BY p.fecha DESC, p.id DESC")
    fun observar(): Flow<List<PagoConDatos>>

    @Query("SELECT p.*, v.nombre AS proveedorNombre, f.folio AS compraFolio FROM pagos p INNER JOIN proveedores v ON v.id = p.proveedorId INNER JOIN compras f ON f.id = p.compraId WHERE p.compraId = :compraId ORDER BY p.fecha")
    fun observarDeCompra(compraId: Long): Flow<List<PagoConDatos>>

    @Query("SELECT COUNT(*) FROM pagos WHERE compraId = :compraId")
    suspend fun contarDeCompra(compraId: Long): Int

    @Insert suspend fun insertar(p: Pago): Long
}
