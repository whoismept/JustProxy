package com.whoismept.justproxy.service.vpn

import android.net.VpnService
import android.util.Log
import com.whoismept.justproxy.data.ProxyType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "TcpTunnel"

data class ConnectionKey(
    val srcIp: String, val srcPort: Int,
    val dstIp: String, val dstPort: Int
)

enum class TunnelState { CONNECTING, ESTABLISHED, CLOSING, CLOSED }

class TcpTunnel(
    private val key: ConnectionKey,
    private val vpnService: VpnService,
    private val tunOut: FileOutputStream,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyType: ProxyType,
    private val scope: CoroutineScope,
    private val onClose: (ConnectionKey) -> Unit
) {
    var state = TunnelState.CONNECTING
        private set

    // Sequence number we use when sending to the client (starts after SYN-ACK)
    private val mySeq = AtomicLong(0L)

    // Next expected sequence number from client
    @Volatile var clientSeq = 0L

    // Buffered payloads arriving before proxy is connected
    private val upstreamChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // IPs as bytes for quick packet building (swapped: we reply as the server)
    private val replyFromIp = key.dstIp.toIpBytes()
    private val replyToIp   = key.srcIp.toIpBytes()

    fun handleSyn(tcp: TcpHeader) {
        clientSeq = (tcp.seqNum + 1) and 0xFFFFFFFFL
        mySeq.set((System.nanoTime() and 0xFFFFFFFFL)) // random-ish ISN

        sendTcp(TcpHeader.FLAG_SYN or TcpHeader.FLAG_ACK)
        mySeq.incrementMod()

        scope.launch(Dispatchers.IO) { runTunnel() }
    }

    fun handleData(tcp: TcpHeader, payload: ByteArray) {
        if (state == TunnelState.CLOSED) return
        clientSeq = (tcp.seqNum + payload.size) and 0xFFFFFFFFL
        sendTcp(TcpHeader.FLAG_ACK)
        upstreamChannel.trySend(payload)
    }

    fun handleFin(tcp: TcpHeader) {
        clientSeq = (tcp.seqNum + 1) and 0xFFFFFFFFL
        sendTcp(TcpHeader.FLAG_FIN or TcpHeader.FLAG_ACK)
        mySeq.incrementMod()
        close()
    }

    private suspend fun runTunnel() {
        var socket: Socket? = null
        try {
            socket = Socket().also { vpnService.protect(it) }
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 10_000)
            socket.soTimeout = 0 // blocking reads

            if (!ProxyClient.connect(socket, key.dstIp, key.dstPort, proxyType)) {
                Log.w(TAG, "Proxy handshake failed for ${key.dstIp}:${key.dstPort}")
                sendRst()
                return
            }

            state = TunnelState.ESTABLISHED

            // proxy → TUN forwarding
            val readJob = scope.launch(Dispatchers.IO) {
                forwardProxyToTun(socket)
            }

            // TUN → proxy forwarding (drains upstreamChannel)
            val proxyOut = socket.getOutputStream()
            for (payload in upstreamChannel) {
                if (state != TunnelState.ESTABLISHED) break
                proxyOut.write(payload)
                proxyOut.flush()
            }

            readJob.join()

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tunnel error ${key.dstIp}:${key.dstPort}", e)
            sendRst()
        } finally {
            socket?.runCatching { close() }
            close()
        }
    }

    private fun forwardProxyToTun(socket: Socket) {
        val buf = ByteArray(8192)
        try {
            val inp = socket.getInputStream()
            while (state == TunnelState.ESTABLISHED) {
                val n = inp.read(buf)
                if (n == -1) break
                if (n > 0) {
                    val payload = buf.copyOf(n)
                    sendTcp(TcpHeader.FLAG_ACK or TcpHeader.FLAG_PSH, payload)
                    mySeq.addMod(n.toLong())
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Proxy read ended: ${e.message}")
        } finally {
            if (state == TunnelState.ESTABLISHED) {
                state = TunnelState.CLOSING
                sendTcp(TcpHeader.FLAG_FIN or TcpHeader.FLAG_ACK)
                mySeq.incrementMod()
            }
            upstreamChannel.close()
        }
    }

    private fun sendTcp(flags: Int, payload: ByteArray = byteArrayOf()) {
        val pkt = PacketBuilder.buildTcpPacket(
            srcIp  = replyFromIp, srcPort  = key.dstPort,
            dstIp  = replyToIp,  dstPort  = key.srcPort,
            seqNum = mySeq.get(), ackNum  = clientSeq,
            flags  = flags, payload = payload
        )
        synchronized(tunOut) {
            runCatching { tunOut.write(pkt) }
        }
    }

    private fun sendRst() {
        sendTcp(TcpHeader.FLAG_RST or TcpHeader.FLAG_ACK)
    }

    fun close() {
        if (state == TunnelState.CLOSED) return
        state = TunnelState.CLOSED
        upstreamChannel.close()
        onClose(key)
    }
}

// ── helpers ──────────────────────────────────────────────────────────────────

private fun String.toIpBytes() = split(".").map { it.toInt().toByte() }.toByteArray()

private fun AtomicLong.incrementMod() = set((get() + 1) and 0xFFFFFFFFL)
private fun AtomicLong.addMod(n: Long) = set((get() + n) and 0xFFFFFFFFL)
