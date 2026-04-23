package com.whoismept.justproxy.data

enum class ProxyMode {
    ROOT, // iptables + system settings
    VPN   // VpnService based redirection
}
