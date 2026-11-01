package com.sighec.movilplus

import android.app.Application
import com.sighec.movilplus.data.AppDatabase
import com.sighec.movilplus.data.Repositorio
import com.sighec.movilplus.seguridad.PreferenciasDispositivo

class SighecApp : Application() {
    lateinit var repo: Repositorio
        private set
    lateinit var prefs: PreferenciasDispositivo
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repositorio(AppDatabase.crear(this))
        prefs = PreferenciasDispositivo(this)
    }
}
