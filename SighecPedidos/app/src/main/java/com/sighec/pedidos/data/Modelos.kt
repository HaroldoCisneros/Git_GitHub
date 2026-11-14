package com.sighec.pedidos.data

data class Usuario(
    val usuario: String,
    val nombre: String,
    val coVendedor: String?,
)

data class Linea(
    val codigo: String,
    val descripcion: String,
)

data class Articulo(
    val codigo: String,
    val descripcion: String,
    val codigoLinea: String,
    val precio: Double,
    val stock: Double,
)

data class Cliente(
    val codigo: String,
    val nombre: String,
    val rif: String,
)

data class RenglonPedido(
    val articulo: Articulo,
    val cantidad: Double,
) {
    val subtotal: Double get() = articulo.precio * cantidad
}
