package com.whoismept.justproxy.service.vpn

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG = "UdpDnsForwarder"
private const val DNS_PORT = 53
private const val DNS_TIMEOUT_MS = 5_000

class UdpDnsForwarder(
    private val vpnService: VpnService,
    private val tunOut: FileOutputStream,
    private val scope: CoroutineScope,
    private val dnsServer: String = "8.8.8.8"
) {
    fun handlePacket(ip: IpHeader, buffer: ByteArray, length: Int) {
        val udpOffset = ip.headerLength
        if (length < udpOffset + 8) return

        val srcPort = ((buffer[udpOffset].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((buffer[udpOffset + 2].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != DNS_PORT) return

        val payloadOffset = udpOffset + 8
        val payloadLen = length - payloadOffset
        if (payloadLen <= 0) return

        val query = buffer.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        val clientIp = ip.srcIp.copyOf()

        scope.launch(Dispatchers.IO) {
            forwardDns(query, clientIp, srcPort)
        }
    }

    private fun forwardDns(query: ByteArray, clientIp: ByteArray, clientPort: Int) {
        try {
            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = DNS_TIMEOUT_MS

            val server = InetAddress.getByName(dnsServer)
            socket.send(DatagramPacket(query, query.size, server, DNS_PORT))

            val respBuf = ByteArray(512)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)
            socket.close()

            val response = respBuf.copyOf(respPacket.length)
            val pkt = buildUdpIpPacket(
                srcIp   = InetAddress.getByName(dnsServer).address,
                srcPort = DNS_PORT,
                dstIp   = clientIp,
                dstPort = clientPort,
                payload = response
            )
            synchronized(tunOut) { tunOut.write(pkt) }

        } catch (e: Exception) {
            Log.d(TAG, "DNS forward failed: ${e.message}")
        }
    }

    // ── packet builder ────────────────────────────────────────────────────────

    private fun buildUdpIpPacket(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipLen  = 20
        val udpLen = 8
        val total  = ipLen + udpLen + payload.size
        val pkt    = ByteArray(total)

        // IPv4 header
        pkt[0] = 0x45.toByte()
        pkt[1] = 0
        pkt[2] = (total shr 8).toByte()
        pkt[3] = (total and 0xFF).toByte()
        pkt[4] = 0; pkt[5] = 0
        pkt[6] = 0x40; pkt[7] = 0   // don't fragment
        pkt[8] = 64                  // TTL
        pkt[9] = 17                  // UDP
        pkt[10] = 0; pkt[11] = 0    // checksum placeholder
        srcIp.copyInto(pkt, 12)
        dstIp.copyInto(pkt, 16)
        val ipCsum = checksum(pkt, 0, ipLen)
        pkt[10] = (ipCsum shr 8).toByte()
        pkt[11] = (ipCsum and 0xFF).toByte()

        // UDP header
        val u = ipLen
        pkt[u]     = (srcPort shr 8).toByte()
        pkt[u + 1] = (srcPort and 0xFF).toByte()
        pkt[u + 2] = (dstPort shr 8).toByte()
        pkt[u + 3] = (dstPort and 0xFF).toByte()
        val udpTotal = udpLen + payload.size
        pkt[u + 4] = (udpTotal shr 8).toByte()
        pkt[u + 5] = (udpTotal and 0xFF).toByte()
        pkt[u + 6] = 0; pkt[u + 7] = 0   // checksum placeholder
        payload.copyInto(pkt, u + udpLen)

        val udpCsum = udpChecksum(srcIp, dstIp, pkt, u, udpTotal)
        pkt[u + 6] = (udpCsum shr 8).toByte()
        pkt[u + 7] = (udpCsum and 0xFF).toByte()

        return pkt
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0; var i = offset; val end = offset + length
        while (i < end - 1) { sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2 }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun udpChecksum(srcIp: ByteArray, dstIp: ByteArray, pkt: ByteArray, udpOffset: Int, udpLength: Int): Int {
        val pseudo = ByteArray(12 + udpLength)
        srcIp.copyInto(pseudo, 0); dstIp.copyInto(pseudo, 4)
        pseudo[8] = 0; pseudo[9] = 17
        pseudo[10] = (udpLength shr 8).toByte(); pseudo[11] = (udpLength and 0xFF).toByte()
        pkt.copyInto(pseudo, 12, udpOffset, udpOffset + udpLength)
        pseudo[12 + 6] = 0; pseudo[12 + 7] = 0
        return checksum(pseudo, 0, pseudo.size)
    }
}
