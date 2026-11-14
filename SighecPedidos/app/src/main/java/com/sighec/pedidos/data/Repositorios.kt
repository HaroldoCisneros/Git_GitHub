package com.sighec.pedidos.data

import com.sighec.pedidos.seguridad.ClaveHasher

class UsuarioRepository(private val sql: SqlServerClient) {

    /** Valida usuario y clave contra dbo.SighecUsuarios. Lanza excepción con mensaje si falla. */
    suspend fun login(usuario: String, clave: String): Usuario = sql.usar { con ->
        val encontrado = con.prepareStatement(
            "SELECT usuario, nombre, co_ven, clave_sal, clave_hash, activo FROM dbo.SighecUsuarios WHERE usuario = ?"
        ).use { ps ->
            ps.setString(1, usuario.trim())
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw IllegalArgumentException("Usuario o contraseña incorrectos")
                if (!rs.getBoolean("activo")) throw IllegalArgumentException("El usuario está inactivo")
                val ok = ClaveHasher.verificar(clave, rs.getBytes("clave_sal"), rs.getBytes("clave_hash"))
                if (!ok) throw IllegalArgumentException("Usuario o contraseña incorrectos")
                Usuario(
                    usuario = rs.getString("usuario").trim(),
                    nombre = rs.getString("nombre").trim(),
                    coVendedor = rs.getString("co_ven")?.trim()?.ifBlank { null },
                )
            }
        }
        runCatching {
            con.prepareStatement("UPDATE dbo.SighecUsuarios SET ultimo_acceso = GETDATE() WHERE usuario = ?")
                .use { it.setString(1, encontrado.usuario); it.executeUpdate() }
        }
        encontrado
    }
}

class CatalogoRepository(
    private val sql: SqlServerClient,
    private val config: ConfigRepository,
) {
    suspend fun lineas(): List<Linea> = sql.usar { con ->
        con.createStatement().use { st ->
            st.executeQuery(ProfitQueries.LINEAS).use { rs ->
                buildList { while (rs.next()) add(Linea(rs.getString("co_lin"), rs.getString("lin_des"))) }
            }
        }
    }

    suspend fun articulos(codigoLinea: String): List<Articulo> = sql.usar { con ->
        val c = config.config.value
        con.prepareStatement(ProfitQueries.ARTICULOS_POR_LINEA).use { ps ->
            ps.setString(1, c.tipoPrecio)
            ps.setString(2, c.almacen)
            ps.setString(3, c.almacen)
            ps.setString(4, codigoLinea)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        Articulo(
                            codigo = rs.getString("co_art"),
                            descripcion = rs.getString("art_des"),
                            codigoLinea = rs.getString("co_lin"),
                            precio = rs.getDouble("precio"),
                            stock = rs.getDouble("stock"),
                        )
                    )
                }
            }
        }
    }

    suspend fun buscarClientes(texto: String): List<Cliente> = sql.usar { con ->
        val patron = "%${texto.trim()}%"
        con.prepareStatement(ProfitQueries.BUSCAR_CLIENTES).use { ps ->
            ps.setString(1, patron); ps.setString(2, patron); ps.setString(3, patron)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(Cliente(rs.getString("co_cli"), rs.getString("cli_des"), rs.getString("rif")))
                }
            }
        }
    }

    /** URL de la imagen del artículo según la plantilla configurada, o null. */
    fun urlImagen(codigoArticulo: String): String? {
        val plantilla = config.config.value.urlImagenes
        if (plantilla.isBlank()) return null
        val cod = java.net.URLEncoder.encode(codigoArticulo.trim(), "UTF-8").replace("+", "%20")
        return plantilla.replace("{co_art}", cod)
    }
}

class PedidoRepository(private val sql: SqlServerClient) {

    /** Guarda el pedido en dbo.SighecPedido / SighecPedidoReng y devuelve su número. */
    suspend fun guardar(
        usuario: Usuario,
        cliente: Cliente,
        renglones: List<RenglonPedido>,
        ivaPorcentaje: Double,
        observacion: String,
    ): Int = sql.usar { con ->
        val subtotal = renglones.sumOf { it.subtotal }
        val iva = subtotal * ivaPorcentaje / 100.0
        con.autoCommit = false
        try {
            val id = con.prepareStatement(
                """INSERT INTO dbo.SighecPedido (co_cli, cli_des, usuario, co_ven, subtotal, iva, total, observacion)
                   OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                ps.setString(1, cliente.codigo)
                ps.setString(2, cliente.nombre.take(120))
                ps.setString(3, usuario.usuario)
                ps.setString(4, usuario.coVendedor)
                ps.setBigDecimal(5, subtotal.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP))
                ps.setBigDecimal(6, iva.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP))
                ps.setBigDecimal(7, (subtotal + iva).toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP))
                ps.setString(8, observacion.take(500).ifBlank { null })
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
            con.prepareStatement(
                """INSERT INTO dbo.SighecPedidoReng (id_pedido, reng_num, co_art, art_des, cantidad, precio, subtotal)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                renglones.forEachIndexed { i, r ->
                    ps.setInt(1, id)
                    ps.setInt(2, i + 1)
                    ps.setString(3, r.articulo.codigo)
                    ps.setString(4, r.articulo.descripcion.take(120))
                    ps.setBigDecimal(5, r.cantidad.toBigDecimal())
                    ps.setBigDecimal(6, r.articulo.precio.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP))
                    ps.setBigDecimal(7, r.subtotal.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP))
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            con.commit()
            id
        } catch (e: Exception) {
            runCatching { con.rollback() }
            throw e
        }
    }
}
