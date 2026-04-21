package com.proxydegil.proxyapp.service.vpn

import android.net.VpnService
import android.util.Log
import com.proxydegil.proxyapp.data.ProxyType
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tunnels = ConcurrentHashMap<ConnectionKey, TcpTunnel>()

    fun handlePacket(buffer: ByteArray, length: Int) {
        val ip = IpHeader.parse(buffer, length) ?: return
        if (ip.protocol != IpHeader.PROTO_TCP) return
        val tcp = TcpHeader.parse(buffer, ip.headerLength) ?: return

        val payloadOffset = ip.headerLength + tcp.headerLength
        val payloadLen = length - payloadOffset
        val payload = if (payloadLen > 0) buffer.copyOfRange(payloadOffset, payloadOffset + payloadLen)
                      else byteArrayOf()

        val key = ConnectionKey(ip.srcIpStr, tcp.srcPort, ip.dstIpStr, tcp.dstPort)

        when {
            tcp.isSyn && !tcp.isAck -> {
                // New connection request
                tunnels[key]?.close()
                val tunnel = TcpTunnel(key, vpnService, tunOut, proxyHost, proxyPort, proxyType, scope) {
                    tunnels.remove(it)
                }
                tunnels[key] = tunnel
                tunnel.handleSyn(tcp)
                Log.d(TAG, "New connection: ${key.srcIp}:${key.srcPort} → ${key.dstIp}:${key.dstPort}")
            }
            tcp.isRst -> {
                tunnels.remove(key)?.close()
            }
            tcp.isFin -> {
                tunnels[key]?.handleFin(tcp)
            }
            payload.isNotEmpty() -> {
                tunnels[key]?.handleData(tcp, payload)
            }
            // Pure ACK with no data — nothing to do
        }
    }

    fun shutdown() {
        tunnels.values.forEach { it.close() }
        tunnels.clear()
        scope.cancel()
    }
}
