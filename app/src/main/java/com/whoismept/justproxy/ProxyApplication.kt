package com.whoismept.justproxy

import android.app.Application
import com.whoismept.justproxy.data.AppDatabase
import com.whoismept.justproxy.data.ProxyRepository

class ProxyApplication : Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ProxyRepository(database.proxyDao()) }
}
