package com.whoismept.justproxy.utils

import android.content.Context
import android.os.Process
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.data.ProxyType
import com.whoismept.justproxy.service.LocalProxyRelay

object ProxyManager {

    fun startProxy(context: Context, profile: ProxyProfile): Boolean {
        stopProxy()
        return if (profile.type == ProxyType.HTTP && profile.isHttp && profile.targetPackages.isNullOrBlank()) {
            startSystemProxy(profile)
        } else {
            startIptablesProxy(context, profile)
        }
    }

    private fun startSystemProxy(profile: ProxyProfile): Boolean {
        // Route through the local relay so connections appear in Traffic Log.
        // The relay intercepts at 127.0.0.1:RELAY_PORT and forwards to the real Burp endpoint.
        val relayHost = "127.0.0.1"
        val relayPort = LocalProxyRelay.RELAY_PORT
        val commands = listOf(
            "settings put global http_proxy $relayHost:$relayPort",
            "settings put global global_http_proxy_host $relayHost",
            "settings put global global_http_proxy_port $relayPort",
            "settings put global global_http_proxy_exclusion_list \"\"",
            "am broadcast -a android.intent.action.PROXY_CHANGE"
        )
        return RootHelper.execute(commands)
    }

    private fun startIptablesProxy(context: Context, profile: ProxyProfile): Boolean {
        // HTTP proxy type: redirect to local relay which converts raw TCP to proper HTTP proxy requests.
        // SOCKS4/SOCKS5: redirect directly to the proxy endpoint.
        val (host, port) = if (profile.type == ProxyType.HTTP) {
            "127.0.0.1" to LocalProxyRelay.RELAY_PORT
        } else {
            profile.host to profile.port
        }
        val cmds = mutableListOf<String>()

        // ── 1. Flush ─────────────────────────────────────────────────────────
        cmds += "iptables -t nat -F OUTPUT"
        cmds += "ip6tables -F OUTPUT 2>/dev/null"

        // ── 2. UID bypass — prevent proxy process from looping back ──────────
        // Root, system, shell, NFC, radio…
        listOf(0, 1000, 1001, 1027, 2000).forEach { uid ->
            cmds += "iptables -t nat -A OUTPUT -m owner --uid-owner $uid -j RETURN"
        }
        // Our own app UID (JustProxy should never be proxied)
        cmds += "iptables -t nat -A OUTPUT -m owner --uid-owner ${Process.myUid()} -j RETURN"

        // ── 3. IP bypass — local / private / proxy itself ────────────────────
        // When relay is active (HTTP type), real proxy host is profile.host — bypass it too.
        if (profile.type == ProxyType.HTTP) {
            cmds += "iptables -t nat -A OUTPUT -d ${profile.host} -j RETURN"
        } else {
            cmds += "iptables -t nat -A OUTPUT -d $host -j RETURN"     // proxy host
        }
        cmds += "iptables -t nat -A OUTPUT -d 127.0.0.0/8  -j RETURN" // full loopback
        cmds += "iptables -t nat -A OUTPUT -d 10.0.0.0/8   -j RETURN"
        cmds += "iptables -t nat -A OUTPUT -d 192.168.0.0/16 -j RETURN"
        cmds += "iptables -t nat -A OUTPUT -d 172.16.0.0/12 -j RETURN"
        cmds += "iptables -t nat -A OUTPUT -d 169.254.0.0/16 -j RETURN" // link-local
        cmds += "iptables -t nat -A OUTPUT -d 224.0.0.0/4  -j RETURN"  // multicast

        // ── 4. Redirect ──────────────────────────────────────────────────────
        val packages = profile.targetPackages
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

        if (!packages.isNullOrEmpty()) {
            packages.forEach { pkg ->
                runCatching {
                    val uid = context.packageManager.getPackageUid(pkg, 0)
                    cmds += "iptables -t nat -A OUTPUT -p tcp -m owner --uid-owner $uid -j DNAT --to-destination $host:$port"
                    cmds += "ip6tables -A OUTPUT -m owner --uid-owner $uid -j REJECT 2>/dev/null"
                }.onFailure { it.printStackTrace() }
            }
        } else {
            cmds += "iptables -t nat -A OUTPUT -p tcp -j DNAT --to-destination $host:$port"
            cmds += "ip6tables -A OUTPUT -j REJECT 2>/dev/null"
        }

        return RootHelper.execute(cmds)
    }

    fun stopProxy(): Boolean {
        val cmds = listOf(
            "iptables -t nat -F OUTPUT",
            "iptables -t nat -F PREROUTING",
            "ip6tables -F OUTPUT 2>/dev/null",
            "ip6tables -t nat -F 2>/dev/null",
            // Clear Android system proxy
            "settings delete global http_proxy",
            "settings delete global global_http_proxy_host",
            "settings delete global global_http_proxy_port",
            "settings delete global global_http_proxy_exclusion_list",
            "settings delete secure http_proxy",
            "settings delete system http_proxy",
            "settings put global http_proxy :0",
            "am broadcast -a android.intent.action.PROXY_CHANGE"
        )
        return RootHelper.execute(cmds)
    }
}
