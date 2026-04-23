package com.whoismept.justproxy.service.vpn

import android.net.VpnService
import android.util.Log
import com.whoismept.justproxy.data.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TcpConnMgr"

class TcpConnectionManager(
    private val vpnService: VpnService,
    private val tunOut: FileOutputStream,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyType: ProxyType
) {
    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tunnels    = ConcurrentHashMap<ConnectionKey, TcpTunnel>()
    private val dnsForward = UdpDnsForwarder(vpnService, tunOut, scope)

    fun handlePacket(buffer: ByteArray, length: Int) {
        val ip = IpHeader.parse(buffer, length) ?: return

        when (ip.protocol) {
            IpHeader.PROTO_TCP -> handleTcp(ip, buffer, length)
            IpHeader.PROTO_UDP -> dnsForward.handlePacket(ip, buffer, length)
        }
    }

    private fun handleTcp(ip: IpHeader, buffer: ByteArray, length: Int) {
        val tcp = TcpHeader.parse(buffer, ip.headerLength) ?: return

        val payloadOffset = ip.headerLength + tcp.headerLength
        val payloadLen    = length - payloadOffset
        val payload       = if (payloadLen > 0) buffer.copyOfRange(payloadOffset, payloadOffset + payloadLen)
                            else byteArrayOf()

        val key = ConnectionKey(ip.srcIpStr, tcp.srcPort, ip.dstIpStr, tcp.dstPort)

        when {
            tcp.isSyn && !tcp.isAck -> {
                tunnels[key]?.close()
                val tunnel = TcpTunnel(key, vpnService, tunOut, proxyHost, proxyPort, proxyType, scope) {
                    tunnels.remove(it)
                }
                tunnels[key] = tunnel
                tunnel.handleSyn(tcp)
                Log.d(TAG, "TCP connect: ${key.srcIp}:${key.srcPort} → ${key.dstIp}:${key.dstPort}")
            }
            tcp.isRst          -> tunnels.remove(key)?.close()
            tcp.isFin          -> tunnels[key]?.handleFin(tcp)
            payload.isNotEmpty() -> tunnels[key]?.handleData(tcp, payload)
        }
    }

    fun shutdown() {
        tunnels.values.forEach { it.close() }
        tunnels.clear()
        scope.cancel()
    }
}
