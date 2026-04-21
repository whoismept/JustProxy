package com.proxydegil.proxyapp

import android.app.Application
import com.proxydegil.proxyapp.data.AppDatabase
import com.proxydegil.proxyapp.data.ProxyRepository

class ProxyApplication : Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ProxyRepository(database.proxyDao()) }
}
