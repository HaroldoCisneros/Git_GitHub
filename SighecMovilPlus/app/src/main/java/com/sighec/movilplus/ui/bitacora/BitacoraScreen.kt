package com.sighec.movilplus.ui.bitacora

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sighec.movilplus.ui.componentes.Buscador
import com.sighec.movilplus.ui.componentes.Fila
import com.sighec.movilplus.ui.componentes.PantallaBase
import com.sighec.movilplus.ui.componentes.Vacio
import com.sighec.movilplus.ui.componentes.fechaHora
import com.sighec.movilplus.ui.componentes.repo

/** Registro de todo lo que hacen los usuarios en la aplicación. */
@Composable
fun BitacoraScreen(onAtras: () -> Unit) {
    val repo = repo()
    var q by remember { mutableStateOf("") }
    val lista by remember(q) { repo.db.bitacora().observar(q.trim()) }.collectAsState(emptyList())
    PantallaBase("Bitácora", onAtras) { padding ->
        Column(Modifier.padding(padding)) {
            Buscador(q, { q = it })
            LazyColumn {
                if (lista.isEmpty()) item { Vacio("Sin registros") }
                items(lista, key = { it.id }) { b ->
                    Fila(
                        titulo = "${b.modulo} · ${b.accion}",
                        subtitulo = listOf(b.fecha.fechaHora(), b.usuario, b.detalle).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                }
            }
        }
    }
}
