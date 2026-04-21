package com.proxydegil.proxyapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProxyType {
    HTTP, SOCKS4, SOCKS5
}

@Entity(tableName = "proxy_profiles")
data class ProxyProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.HTTP,
    val isHttp: Boolean = true,
    val isActive: Boolean = false,
    val targetPackages: String? = null // Comma-separated list of package names
)
