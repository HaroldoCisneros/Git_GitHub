package com.sighec.movilplus.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sighec.movilplus.seguridad.Contrasenas
import com.sighec.movilplus.seguridad.Modulo
import com.sighec.movilplus.seguridad.Sesion
import kotlin.math.round

/** Error de negocio que se muestra tal cual al usuario. */
class ReglaNegocio(mensaje: String) : Exception(mensaje)

fun Double.r2(): Double = round(this * 100.0) / 100.0

private const val DIA_MS = 24L * 60 * 60 * 1000

/**
 * Punto único de acceso a los datos. Todas las operaciones que afectan
 * saldos, existencias, caja o bancos se hacen aquí dentro de una transacción
 * y quedan registradas en la bitácora.
 */
class Repositorio(val db: AppDatabase) {

    private val usuario get() = Sesion.nombreUsuario

    suspend fun bitacora(modulo: Modulo, accion: String, detalle: String = "") {
        db.bitacora().insertar(Bitacora(usuario = usuario, modulo = modulo.titulo, accion = accion, detalle = detalle))
    }

    private fun requerir(condicion: Boolean, mensaje: String) {
        if (!condicion) throw ReglaNegocio(mensaje)
    }

    /* ======================= USUARIOS ======================= */

    suspend fun hayUsuarios(): Boolean = db.usuarios().contar() > 0

    /** Primer arranque: crea el administrador y catálogos básicos. */
    suspend fun configuracionInicial(nombre: String, usuario: String, password: String) {
        requerir(!hayUsuarios(), "La aplicación ya fue configurada")
        guardarUsuario(
            Usuario(usuario = usuario, nombre = nombre, passwordHash = "", salt = "", esAdmin = true,
                permisos = Modulo.aTexto(Modulo.entries.toSet())),
            password
        )
        db.withTransaction {
            listOf("Contado" to 0, "Crédito 15 días" to 15, "Crédito 30 días" to 30)
                .forEach { (d, dias) -> db.condiciones().insertar(CondicionPago(descripcion = d, dias = dias)) }
            db.bitacora().insertar(Bitacora(usuario = usuario, modulo = "Sistema", accion = "Configuración inicial"))
        }
    }

    suspend fun login(usuario: String, password: String): Usuario {
        val u = db.usuarios().porUsuario(usuario.trim())
        val valido = u != null && withContext(Dispatchers.Default) { Contrasenas.verificar(password, u) }
        if (u == null || !valido) {
            db.bitacora().insertar(Bitacora(usuario = usuario.trim(), modulo = "Acceso", accion = "Intento fallido"))
            throw ReglaNegocio("Usuario o contraseña incorrectos")
        }
        requerir(u.activo, "El usuario está inactivo")
        Sesion.iniciar(u)
        db.bitacora().insertar(Bitacora(usuario = u.usuario, modulo = "Acceso", accion = "Inicio de sesión", detalle = "Contraseña"))
        return u
    }

    suspend fun loginHuella(usuario: String): Usuario {
        val u = db.usuarios().porUsuario(usuario) ?: throw ReglaNegocio("El usuario enrolado ya no existe")
        requerir(u.activo, "El usuario está inactivo")
        requerir(u.permiteHuella, "Este usuario no tiene permitido entrar con huella")
        Sesion.iniciar(u)
        db.bitacora().insertar(Bitacora(usuario = u.usuario, modulo = "Acceso", accion = "Inicio de sesión", detalle = "Huella digital"))
        return u
    }

    suspend fun logout() {
        db.bitacora().insertar(Bitacora(usuario = usuario, modulo = "Acceso", accion = "Cierre de sesión"))
        Sesion.cerrar()
    }

    suspend fun guardarUsuario(u: Usuario, passwordNueva: String?): Long {
        requerir(u.usuario.isNotBlank(), "Captura el usuario")
        requerir(u.nombre.isNotBlank(), "Captura el nombre")
        val existente = db.usuarios().porUsuario(u.usuario.trim())
        requerir(existente == null || existente.id == u.id, "El usuario '${u.usuario}' ya existe")
        if (u.id == 0L) requerir(!passwordNueva.isNullOrBlank(), "Captura la contraseña")
        if (!passwordNueva.isNullOrBlank()) requerir(passwordNueva.length >= 4, "La contraseña debe tener al menos 4 caracteres")

        var registro = u.copy(usuario = u.usuario.trim())
        if (!passwordNueva.isNullOrBlank()) {
            val salt = Contrasenas.nuevoSalt()
            val hash = withContext(Dispatchers.Default) { Contrasenas.hash(passwordNueva, salt) }
            registro = registro.copy(salt = salt, passwordHash = hash)
        }
        return db.withTransaction {
            if (registro.id != 0L) {
                val anterior = db.usuarios().porId(registro.id)
                if (anterior != null && anterior.esAdmin && anterior.activo && (!registro.esAdmin || !registro.activo)) {
                    requerir(db.usuarios().contarAdminsActivos() > 1, "Debe existir al menos un administrador activo")
                }
                db.usuarios().actualizar(registro)
                if (Sesion.usuario?.id == registro.id) Sesion.iniciar(registro)
                bitacora(Modulo.USUARIOS, "Modificó usuario", registro.usuario)
                registro.id
            } else {
                val id = db.usuarios().insertar(registro)
                bitacora(Modulo.USUARIOS, "Alta de usuario", registro.usuario)
                id
            }
        }
    }

    suspend fun eliminarUsuario(u: Usuario) = db.withTransaction {
        requerir(u.id != Sesion.usuario?.id, "No puedes eliminar el usuario con el que iniciaste sesión")
        if (u.esAdmin && u.activo) requerir(db.usuarios().contarAdminsActivos() > 1, "Debe existir al menos un administrador activo")
        db.usuarios().eliminar(u)
        bitacora(Modulo.USUARIOS, "Eliminó usuario", u.usuario)
    }

    /* ======================= CATÁLOGOS ======================= */

    suspend fun guardarArticulo(a: Articulo) = db.withTransaction {
        requerir(a.codigo.isNotBlank() && a.descripcion.isNotBlank(), "Captura código y descripción")
        if (a.id == 0L) {
            db.articulos().insertar(a)
            bitacora(Modulo.ARTICULOS, "Alta", "${a.codigo} ${a.descripcion}")
        } else {
            db.articulos().actualizar(a)
            bitacora(Modulo.ARTICULOS, "Modificación", "${a.codigo} ${a.descripcion}")
        }
    }

    suspend fun eliminarArticulo(a: Articulo) = db.withTransaction {
        requerir(db.articulos().usos(a.id) == 0, "El artículo tiene movimientos. Márcalo como inactivo.")
        db.articulos().eliminar(a)
        bitacora(Modulo.ARTICULOS, "Baja", "${a.codigo} ${a.descripcion}")
    }

    suspend fun guardarCondicion(c: CondicionPago) = db.withTransaction {
        requerir(c.descripcion.isNotBlank(), "Captura la descripción")
        requerir(c.dias >= 0, "Los días no pueden ser negativos")
        if (c.id == 0L) db.condiciones().insertar(c) else db.condiciones().actualizar(c)
        bitacora(Modulo.CONDICIONES_PAGO, if (c.id == 0L) "Alta" else "Modificación", "${c.descripcion} (${c.dias} días)")
    }

    suspend fun eliminarCondicion(c: CondicionPago) = db.withTransaction {
        requerir(db.condiciones().usos(c.id) == 0, "La condición de pago está en uso")
        db.condiciones().eliminar(c)
        bitacora(Modulo.CONDICIONES_PAGO, "Baja", c.descripcion)
    }

    suspend fun guardarCliente(c: Cliente) = db.withTransaction {
        requerir(c.codigo.isNotBlank() && c.nombre.isNotBlank(), "Captura código y nombre")
        if (c.id == 0L) db.clientes().insertar(c) else db.clientes().actualizar(c)
        bitacora(Modulo.CLIENTES, if (c.id == 0L) "Alta" else "Modificación", "${c.codigo} ${c.nombre}")
    }

    suspend fun eliminarCliente(c: Cliente) = db.withTransaction {
        requerir(db.clientes().usos(c.id) == 0, "El cliente tiene facturas. Márcalo como inactivo.")
        db.clientes().eliminar(c)
        bitacora(Modulo.CLIENTES, "Baja", "${c.codigo} ${c.nombre}")
    }

    suspend fun guardarProveedor(p: Proveedor) = db.withTransaction {
        requerir(p.codigo.isNotBlank() && p.nombre.isNotBlank(), "Captura código y nombre")
        if (p.id == 0L) db.proveedores().insertar(p) else db.proveedores().actualizar(p)
        bitacora(Modulo.PROVEEDORES, if (p.id == 0L) "Alta" else "Modificación", "${p.codigo} ${p.nombre}")
    }

    suspend fun eliminarProveedor(p: Proveedor) = db.withTransaction {
        requerir(db.proveedores().usos(p.id) == 0, "El proveedor tiene compras. Márcalo como inactivo.")
        db.proveedores().eliminar(p)
        bitacora(Modulo.PROVEEDORES, "Baja", "${p.codigo} ${p.nombre}")
    }

    suspend fun guardarBanco(b: Banco) = db.withTransaction {
        requerir(b.nombre.isNotBlank(), "Captura el nombre del banco")
        if (b.id == 0L) db.bancos().insertar(b) else db.bancos().actualizar(b)
        bitacora(Modulo.BANCOS, if (b.id == 0L) "Alta" else "Modificación", b.nombre)
    }

    suspend fun eliminarBanco(b: Banco) = db.withTransaction {
        requerir(db.bancos().usos(b.id) == 0, "El banco tiene cuentas registradas")
        db.bancos().eliminar(b)
        bitacora(Modulo.BANCOS, "Baja", b.nombre)
    }

    suspend fun guardarCuenta(c: CuentaBancaria) = db.withTransaction {
        requerir(c.alias.isNotBlank(), "Captura el nombre de la cuenta")
        requerir(c.bancoId != 0L, "Selecciona el banco")
        if (c.id == 0L) {
            db.cuentas().insertar(c.copy(saldo = c.saldoInicial.r2()))
            bitacora(Modulo.CUENTAS, "Alta", "${c.alias} saldo inicial ${c.saldoInicial}")
        } else {
            val anterior = db.cuentas().porId(c.id) ?: throw ReglaNegocio("La cuenta no existe")
            // El saldo inicial sólo se puede corregir mientras no haya movimientos.
            val saldoInicial = if (db.cuentas().usos(c.id) == 0) c.saldoInicial.r2() else anterior.saldoInicial
            val saldo = (anterior.saldo + (saldoInicial - anterior.saldoInicial)).r2()
            db.cuentas().actualizar(c.copy(saldoInicial = saldoInicial, saldo = saldo))
            bitacora(Modulo.CUENTAS, "Modificación", c.alias)
        }
    }

    suspend fun eliminarCuenta(c: CuentaBancaria) = db.withTransaction {
        requerir(db.cuentas().usos(c.id) == 0, "La cuenta tiene movimientos. Márcala como inactiva.")
        db.cuentas().eliminar(c)
        bitacora(Modulo.CUENTAS, "Baja", c.alias)
    }

    /* ======================= TESORERÍA ======================= */

    private suspend fun movimientoBancoInterno(m: MovimientoBanco) {
        requerir(m.importe > 0, "El importe debe ser mayor a cero")
        val cuenta = db.cuentas().porId(m.cuentaId) ?: throw ReglaNegocio("Selecciona la cuenta bancaria")
        val delta = if (m.tipo == TipoMovimiento.ENTRADA) m.importe else -m.importe
        if (delta < 0) requerir(cuenta.saldo + delta >= -0.005, "Saldo insuficiente en ${cuenta.alias} (${"%,.2f".format(cuenta.saldo)})")
        db.movimientosBanco().insertar(m.copy(importe = m.importe.r2(), usuario = usuario))
        db.cuentas().ajustarSaldo(m.cuentaId, delta.r2())
    }

    private suspend fun movimientoCajaInterno(m: MovimientoCaja) {
        requerir(m.importe > 0, "El importe debe ser mayor a cero")
        db.caja().insertar(m.copy(importe = m.importe.r2(), usuario = usuario))
    }

    suspend fun registrarMovimientoBanco(m: MovimientoBanco) = db.withTransaction {
        requerir(m.concepto.isNotBlank(), "Captura el concepto")
        movimientoBancoInterno(m.copy(origen = "MANUAL"))
        bitacora(Modulo.CUENTAS, if (m.tipo == TipoMovimiento.ENTRADA) "Depósito" else "Retiro", "${m.concepto} ${m.importe}")
    }

    suspend fun registrarMovimientoCaja(m: MovimientoCaja) = db.withTransaction {
        requerir(m.concepto.isNotBlank(), "Captura el concepto")
        movimientoCajaInterno(m.copy(origen = "MANUAL"))
        bitacora(Modulo.CAJA, if (m.tipo == TipoMovimiento.ENTRADA) "Ingreso" else "Egreso", "${m.concepto} ${m.importe}")
    }

    /* ======================= VENTAS ======================= */

    suspend fun registrarFactura(f: Factura, detalles: List<FacturaDetalle>): Long = db.withTransaction {
        requerir(f.clienteId != 0L, "Selecciona el cliente")
        requerir(detalles.isNotEmpty(), "Agrega al menos un artículo")
        requerir(detalles.all { it.cantidad > 0 }, "Las cantidades deben ser mayores a cero")
        val subtotal = detalles.sumOf { it.importe }.r2()
        val impuesto = detalles.sumOf { it.importe * it.tasaImpuesto / 100.0 }.r2()
        val total = (subtotal + impuesto).r2()

        val cliente = db.clientes().porId(f.clienteId) ?: throw ReglaNegocio("El cliente no existe")
        if (f.diasCredito > 0 && cliente.limiteCredito > 0) {
            val saldoActual = db.facturas().saldoCliente(cliente.id)
            requerir(saldoActual + total <= cliente.limiteCredito + 0.005,
                "Excede el límite de crédito del cliente (saldo ${"%,.2f".format(saldoActual)}, límite ${"%,.2f".format(cliente.limiteCredito)})")
        }

        val folio = db.facturas().siguienteFolio()
        val id = db.facturas().insertar(
            f.copy(
                folio = folio, subtotal = subtotal, impuesto = impuesto, total = total, saldo = total,
                vencimiento = f.fecha + f.diasCredito * DIA_MS, usuario = usuario
            )
        )
        db.facturas().insertarDetalles(detalles.map { it.copy(id = 0, facturaId = id) })
        detalles.forEach { db.articulos().ajustarExistencia(it.articuloId, -it.cantidad) }
        bitacora(Modulo.FACTURACION, "Factura emitida", "Folio $folio ${cliente.nombre} total ${"%,.2f".format(total)}")
        id
    }

    suspend fun cancelarFactura(id: Long) = db.withTransaction {
        val f = db.facturas().porId(id) ?: throw ReglaNegocio("La factura no existe")
        requerir(f.estatus == Estatus.VIGENTE, "La factura ya está cancelada")
        requerir(db.cobros().contarDeFactura(id) == 0, "La factura tiene cobros aplicados, no se puede cancelar")
        db.facturas().actualizar(f.copy(estatus = Estatus.CANCELADA, saldo = 0.0))
        db.facturas().detalles(id).forEach { db.articulos().ajustarExistencia(it.articuloId, it.cantidad) }
        bitacora(Modulo.FACTURACION, "Factura cancelada", "Folio ${f.folio}")
    }

    suspend fun registrarCobro(c: Cobro) = db.withTransaction {
        val f = db.facturas().porId(c.facturaId) ?: throw ReglaNegocio("Selecciona la factura")
        requerir(f.estatus == Estatus.VIGENTE, "La factura está cancelada")
        requerir(c.importe > 0, "El importe debe ser mayor a cero")
        requerir(c.importe.r2() <= f.saldo.r2() + 0.005, "El importe excede el saldo de la factura (${"%,.2f".format(f.saldo)})")
        if (c.formaPago != FormaPago.EFECTIVO) requerir(c.cuentaId != null, "Selecciona la cuenta bancaria de depósito")

        val importe = c.importe.r2()
        db.cobros().insertar(c.copy(importe = importe, clienteId = f.clienteId, usuario = usuario))
        db.facturas().ajustarSaldo(f.id, -importe)
        val concepto = "Cobro factura ${f.folio}"
        if (c.formaPago == FormaPago.EFECTIVO) {
            movimientoCajaInterno(MovimientoCaja(fecha = c.fecha, tipo = TipoMovimiento.ENTRADA, concepto = concepto, importe = importe, referencia = c.referencia, origen = "COBRO"))
        } else {
            movimientoBancoInterno(MovimientoBanco(cuentaId = c.cuentaId!!, fecha = c.fecha, tipo = TipoMovimiento.ENTRADA, concepto = concepto, importe = importe, referencia = c.referencia, origen = "COBRO"))
        }
        bitacora(Modulo.COBROS, "Cobro registrado", "$concepto ${c.formaPago} ${"%,.2f".format(importe)}")
    }

    /* ======================= COMPRAS ======================= */

    suspend fun registrarCompra(c: Compra, detalles: List<CompraDetalle>): Long = db.withTransaction {
        requerir(c.proveedorId != 0L, "Selecciona el proveedor")
        requerir(c.folio.isNotBlank(), "Captura el número de factura del proveedor")
        requerir(detalles.isNotEmpty(), "Agrega al menos un artículo")
        requerir(detalles.all { it.cantidad > 0 }, "Las cantidades deben ser mayores a cero")
        val subtotal = detalles.sumOf { it.importe }.r2()
        val impuesto = detalles.sumOf { it.importe * it.tasaImpuesto / 100.0 }.r2()
        val total = (subtotal + impuesto).r2()
        val proveedor = db.proveedores().porId(c.proveedorId) ?: throw ReglaNegocio("El proveedor no existe")

        val id = db.compras().insertar(
            c.copy(
                subtotal = subtotal, impuesto = impuesto, total = total, saldo = total,
                vencimiento = c.fecha + c.diasCredito * DIA_MS, usuario = usuario
            )
        )
        db.compras().insertarDetalles(detalles.map { it.copy(id = 0, compraId = id) })
        detalles.forEach {
            db.articulos().ajustarExistencia(it.articuloId, it.cantidad)
            db.articulos().actualizarCosto(it.articuloId, it.costo)
        }
        bitacora(Modulo.COMPRAS, "Compra registrada", "Factura ${c.folio} ${proveedor.nombre} total ${"%,.2f".format(total)}")
        id
    }

    suspend fun cancelarCompra(id: Long) = db.withTransaction {
        val c = db.compras().porId(id) ?: throw ReglaNegocio("La compra no existe")
        requerir(c.estatus == Estatus.VIGENTE, "La compra ya está cancelada")
        requerir(db.pagos().contarDeCompra(id) == 0, "La compra tiene pagos aplicados, no se puede cancelar")
        db.compras().actualizar(c.copy(estatus = Estatus.CANCELADA, saldo = 0.0))
        db.compras().detalles(id).forEach { db.articulos().ajustarExistencia(it.articuloId, -it.cantidad) }
        bitacora(Modulo.COMPRAS, "Compra cancelada", "Factura ${c.folio}")
    }

    suspend fun registrarPago(p: Pago) = db.withTransaction {
        val c = db.compras().porId(p.compraId) ?: throw ReglaNegocio("Selecciona la factura de compra")
        requerir(c.estatus == Estatus.VIGENTE, "La compra está cancelada")
        requerir(p.importe > 0, "El importe debe ser mayor a cero")
        requerir(p.importe.r2() <= c.saldo.r2() + 0.005, "El importe excede el saldo de la compra (${"%,.2f".format(c.saldo)})")
        if (p.formaPago != FormaPago.EFECTIVO) requerir(p.cuentaId != null, "Selecciona la cuenta bancaria de retiro")

        val importe = p.importe.r2()
        db.pagos().insertar(p.copy(importe = importe, proveedorId = c.proveedorId, usuario = usuario))
        db.compras().ajustarSaldo(c.id, -importe)
        val concepto = "Pago compra ${c.folio}"
        if (p.formaPago == FormaPago.EFECTIVO) {
            movimientoCajaInterno(MovimientoCaja(fecha = p.fecha, tipo = TipoMovimiento.SALIDA, concepto = concepto, importe = importe, referencia = p.referencia, origen = "PAGO"))
        } else {
            movimientoBancoInterno(MovimientoBanco(cuentaId = p.cuentaId!!, fecha = p.fecha, tipo = TipoMovimiento.SALIDA, concepto = concepto, importe = importe, referencia = p.referencia, origen = "PAGO"))
        }
        bitacora(Modulo.PAGOS, "Pago registrado", "$concepto ${p.formaPago} ${"%,.2f".format(importe)}")
    }
}
