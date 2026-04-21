package com.proxydegil.proxyapp.data

enum class ProxyMode {
    ROOT, // iptables + system settings
    VPN   // VpnService based redirection
}
