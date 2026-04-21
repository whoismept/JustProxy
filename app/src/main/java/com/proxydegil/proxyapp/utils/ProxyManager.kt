package com.proxydegil.proxyapp.utils

import android.content.Context
import com.proxydegil.proxyapp.data.ProxyProfile
import com.proxydegil.proxyapp.data.ProxyType

object ProxyManager {

    fun startProxy(context: Context, profile: ProxyProfile): Boolean {
        stopProxy() // Clean state

        return if (profile.type == ProxyType.HTTP && profile.isHttp && profile.targetPackages.isNullOrBlank()) {
            startSystemProxy(profile)
        } else {
            startIptablesProxy(context, profile)
        }
    }

    private fun startSystemProxy(profile: ProxyProfile): Boolean {
        val commands = listOf(
            "settings put global http_proxy ${profile.host}:${profile.port}",
            "settings put global global_http_proxy_host ${profile.host}",
            "settings put global global_http_proxy_port ${profile.port}",
            "settings put global global_http_proxy_exclusion_list \"\""
        )
        return RootHelper.execute(commands)
    }

    private fun startIptablesProxy(context: Context, profile: ProxyProfile): Boolean {
        val commands = mutableListOf<String>()
        val proxyHost = profile.host
        val proxyPort = profile.port
        
        // 1. Flush existing rules
        commands.add("iptables -t nat -F OUTPUT")
        commands.add("ip6tables -F OUTPUT 2>/dev/null")
        
        // 2. Bypass rules (IPv4)
        commands.add("iptables -t nat -A OUTPUT -d $proxyHost -j RETURN")
        commands.add("iptables -t nat -A OUTPUT -d 127.0.0.1 -j RETURN")
        commands.add("iptables -t nat -A OUTPUT -d 192.168.0.0/16 -j RETURN")
        commands.add("iptables -t nat -A OUTPUT -d 10.0.0.0/8 -j RETURN")
        commands.add("iptables -t nat -A OUTPUT -d 172.16.0.0/12 -j RETURN")
        
        val packages = profile.targetPackages?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        
        if (!packages.isNullOrEmpty()) {
            packages.forEach { pkg ->
                try {
                    val uid = context.packageManager.getPackageUid(pkg, 0)
                    // Redirect selected app TCP to proxy (IPv4)
                    commands.add("iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $uid -j DNAT --to-destination $proxyHost:$proxyPort")
                    // Force IPv4 fallback by rejecting IPv6 for this specific app
                    commands.add("ip6tables -A OUTPUT -m owner --uid-owner $uid -j REJECT")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Global redirection
            commands.add("iptables -t nat -A OUTPUT -p tcp -j DNAT --to-destination $proxyHost:$proxyPort")
            // Reject all global IPv6 traffic to force IPv4 fallback
            commands.add("ip6tables -A OUTPUT -j REJECT")
        }
        
        return RootHelper.execute(commands)
    }

    fun stopProxy(): Boolean {
        val commands = listOf(
            "iptables -t nat -F OUTPUT",
            "iptables -t nat -F PREROUTING",
            "ip6tables -F OUTPUT 2>/dev/null",
            "ip6tables -t nat -F 2>/dev/null",
            "settings delete global http_proxy",
            "settings delete global global_http_proxy_host",
            "settings delete global global_http_proxy_port",
            "settings delete global global_http_proxy_exclusion_list",
            "settings delete secure http_proxy",
            "settings delete system http_proxy",
            "settings put global http_proxy :0",
            "am broadcast -a android.intent.action.PROXY_CHANGE"
        )
        return RootHelper.execute(commands)
    }
}
