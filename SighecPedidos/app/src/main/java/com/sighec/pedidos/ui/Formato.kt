package com.sighec.pedidos.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import java.text.NumberFormat
import java.util.Locale

private val locale = Locale.forLanguageTag("es-VE")
private val formatoMonto = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}
private val formatoCantidad = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = 0; maximumFractionDigits = 3
}

fun Double.monto(): String = formatoMonto.format(this)
fun Double.cantidad(): String = formatoCantidad.format(this)

fun Context.buscarActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.buscarActivity()
    else -> null
}
