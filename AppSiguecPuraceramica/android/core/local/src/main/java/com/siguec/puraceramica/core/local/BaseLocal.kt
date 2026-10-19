// MÓDULO :core:local
package com.siguec.puraceramica.core.local

// SQLite de Android
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * La base SQLite del teléfono ("siguec_local.db", en la carpeta privada de la App).
 *
 *   articulos         Copia de saArticulo (la llena "Sincronizar").
 *   codigos           Lo que se puede escanear de cada artículo (co_art, modelo, ref, barras).
 *   sincronizaciones  Última sincronización por empresa.
 *   inventarios       Tomas físicas abiertas traídas de Profit (abierto = 0 cuando ya se envió).
 *   conteos           Cada conteo individual: quién, qué artículo, código leído y cuánto.
 *
 * Las cantidades se guardan como TEXTO ("2.5") y se suman con BigDecimal
 * (sumar REAL en SQLite podría dar 2.4999999).
 */
internal class BaseLocal(contexto: Context) :
    SQLiteOpenHelper(contexto.applicationContext, NOMBRE, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE articulos (
            co_empresa TEXT NOT NULL, co_art TEXT NOT NULL, art_des TEXT NOT NULL, anulado INTEGER NOT NULL,
            PRIMARY KEY (co_empresa, co_art))""")
        db.execSQL("""CREATE TABLE codigos (
            co_empresa TEXT NOT NULL, codigo TEXT NOT NULL COLLATE NOCASE, co_art TEXT NOT NULL,
            origen TEXT NOT NULL, prioridad INTEGER NOT NULL,
            PRIMARY KEY (co_empresa, codigo, co_art))""")
        db.execSQL("""CREATE TABLE sincronizaciones (
            co_empresa TEXT PRIMARY KEY, fecha TEXT NOT NULL, usuario TEXT NOT NULL,
            total INTEGER NOT NULL, codigos INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE inventarios (
            id INTEGER PRIMARY KEY AUTOINCREMENT, co_empresa TEXT NOT NULL, numero TEXT NOT NULL,
            co_alma TEXT NOT NULL, des_alma TEXT NOT NULL, descripcion TEXT NOT NULL, fecha TEXT,
            abierto INTEGER NOT NULL DEFAULT 1, enviado_por TEXT, fecha_envio TEXT,
            clave_envio TEXT,                       -- Se genera al primer intento de envío (no duplica)
            UNIQUE (co_empresa, numero))""")
        db.execSQL("""CREATE TABLE conteos (
            id INTEGER PRIMARY KEY AUTOINCREMENT, inventario_id INTEGER NOT NULL, co_art TEXT NOT NULL,
            codigo_leido TEXT NOT NULL, cantidad TEXT NOT NULL, usuario TEXT NOT NULL, fecha TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX ix_conteos ON conteos (inventario_id, co_art)")
    }

    override fun onUpgrade(db: SQLiteDatabase, anterior: Int, nueva: Int) {
        // Versión 1: todavía no hay cambios de estructura
    }

    companion object {
        const val NOMBRE = "siguec_local.db"
        const val VERSION = 1
    }
}
