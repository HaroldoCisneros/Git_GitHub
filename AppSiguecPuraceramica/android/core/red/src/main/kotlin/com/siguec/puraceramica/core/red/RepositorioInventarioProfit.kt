// MÓDULO :core:red
package com.siguec.puraceramica.core.red

// Cantidades exactas
import java.math.BigDecimal

/**
 * Las dos operaciones del inventario que necesitan WiFi:
 *   traerAbiertas()  Las tomas físicas abiertas en Profit (para contar en el teléfono).  [contar_inventario]
 *   enviar()         Lo contado en el teléfono -> toma física de Profit.                [cerrar_inventario]
 *
 * El envío va en UNA transacción: auditoría en SIGUEC_PURACERAMICA (envios y
 * envio_conteos) + toma física de Profit. Si algo falla no queda nada a medias.
 * [claveEnvio] la genera el teléfono: si se reintenta un envío que sí llegó
 * (se cortó el WiFi justo al terminar), no se duplica.
 *
 * @param tomaFisica el SQL de la toma física de Profit (ver TomaFisica.kt)
 */
class RepositorioInventarioProfit(
    private val bd: BaseDatos,
    private val acceso: RepositorioAcceso,
    private val tomaFisica: TomaFisicaProfit = TomaFisicaProfit.actual
) {
    /** Las tomas físicas abiertas en Profit. */
    suspend fun traerAbiertas(usuario: UsuarioDto, coEmpresa: String): Resultado<List<TomaProfitDto>> =
        bd.usar(coEmpresa) { cx ->
            acceso.exigirPermiso(cx, usuario, coEmpresa, { it.contarInventario }, "Su ambiente no permite contar inventario.")
            tomaFisica.abiertas(cx)
        }

    /** Anota en la bitácora que se trajo una toma al teléfono. */
    suspend fun anotarTraida(usuario: UsuarioDto, coEmpresa: String, toma: TomaProfitDto): Resultado<Unit> =
        bd.usar { cx ->
            cx.anotar(Acciones.TRAER_TOMA, usuario.id, usuario.usuario, coEmpresa,
                "Toma ${toma.numero}, almacén ${toma.coAlma}", acceso.dispositivo)
        }

    /**
     * Envía lo contado a la toma física de Profit.
     * @param renglones los conteos individuales del teléfono (se suman por artículo para Profit)
     */
    suspend fun enviar(
        usuario: UsuarioDto,
        coEmpresa: String,
        toma: InventarioDto,
        renglones: List<RenglonEnvio>,
        claveEnvio: String
    ): Resultado<ResultadoEnvio> = bd.usar(coEmpresa) { cx ->
        acceso.exigirPermiso(cx, usuario, coEmpresa, { it.cerrarInventario },
            "Su ambiente no permite cerrar (enviar) el inventario.")
        val app = bd.baseApp
        // ¿Ya llegó este mismo envío antes? Se devuelve lo que se grabó, sin repetir.
        cx.consultarUno("SELECT id, articulos, conteos FROM $app.dbo.envios WHERE clave_envio = ?", claveEnvio)?.let {
            return@usar ResultadoEnvio(it.entero("id"), it.entero("articulos"), it.entero("conteos"))
        }
        if (renglones.isEmpty()) throw ErrorApp("No hay nada contado para enviar.")
        if (!tomaFisica.estaAbierta(cx, toma.numero)) {
            throw ErrorApp("La toma física N° ${toma.numero} ya no está abierta en Profit.", TipoError.CONFLICTO)
        }
        val totales = totalesPorArticulo(renglones)

        // 1. Auditoría en la base de la App (quién contó qué)
        val envioId = cx.consultarUno(
            """SET NOCOUNT ON; INSERT INTO $app.dbo.envios (clave_envio, co_empresa, toma, co_alma, usuario, dispositivo,
               articulos, conteos) VALUES (?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS int) AS id""",
            claveEnvio, coEmpresa, toma.numero, toma.coAlma, usuario.usuario, acceso.dispositivo.take(100), totales.size,
            renglones.size
        )!!.entero("id")
        cx.ejecutarLote(
            "INSERT INTO $app.dbo.envio_conteos (envio_id, co_art, codigo_leido, cantidad, usuario, fecha_conteo) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            renglones.map { listOf(envioId, it.coArt, it.codigoLeido, it.cantidad, it.usuario, it.fecha) }
        )
        // 2. Toma física de Profit (Profit se encarga de cerrarla)
        tomaFisica.enviar(cx, toma.numero, totales, usuario.usuarioProfit.ifEmpty { usuario.usuario })
        acceso.anotarDesde(cx, Acciones.ENVIAR_TOMA, usuario, coEmpresa,
            "Toma ${toma.numero}, almacén ${toma.coAlma}: ${totales.size} artículos, ${renglones.size} conteos")
        ResultadoEnvio(envioId, totales.size, renglones.size)
    }

    companion object {
        /** Stock contado por artículo = suma de sus conteos (se prueba solo). */
        fun totalesPorArticulo(renglones: List<RenglonEnvio>): Map<String, BigDecimal> =
            renglones.groupBy { it.coArt }.mapValues { (_, r) -> r.fold(BigDecimal.ZERO) { suma, c -> suma + c.cantidad } }
    }
}
