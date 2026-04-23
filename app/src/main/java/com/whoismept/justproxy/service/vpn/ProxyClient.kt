package com.whoismept.justproxy.service.vpn

import com.whoismept.justproxy.data.ProxyType
import java.io.InputStream
import java.net.Socket

object ProxyClient {

    fun connect(socket: Socket, destIp: String, destPort: Int, type: ProxyType): Boolean {
        return try {
            when (type) {
                ProxyType.SOCKS5 -> socks5(socket, destIp, destPort)
                ProxyType.SOCKS4 -> socks4(socket, destIp, destPort)
                ProxyType.HTTP   -> httpConnect(socket, destIp, destPort)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun socks5(socket: Socket, ip: String, port: Int): Boolean {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()

        out.write(byteArrayOf(0x05, 0x01, 0x00)) // SOCKS5, 1 method: no-auth
        out.flush()

        val greeting = ByteArray(2)
        if (!inp.readFully(greeting)) return false
        if (greeting[0] != 0x05.toByte() || greeting[1] != 0x00.toByte()) return false

        val ipBytes = ip.split(".").map { it.toInt().toByte() }.toByteArray()
        val req = byteArrayOf(0x05, 0x01, 0x00, 0x01) + ipBytes +
                  byteArrayOf((port shr 8).toByte(), (port and 0xFF).toByte())
        out.write(req)
        out.flush()

        val reply = ByteArray(10)
        if (!inp.readFully(reply)) return false
        return reply[1] == 0x00.toByte()
    }

    private fun socks4(socket: Socket, ip: String, port: Int): Boolean {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()

        val ipBytes = ip.split(".").map { it.toInt().toByte() }.toByteArray()
        val req = byteArrayOf(0x04, 0x01,
            (port shr 8).toByte(), (port and 0xFF).toByte()) +
                  ipBytes + byteArrayOf(0x00) // empty user ID
        out.write(req)
        out.flush()

        val reply = ByteArray(8)
        if (!inp.readFully(reply)) return false
        return reply[1] == 0x5A.toByte() // 0x5A = request granted
    }

    private fun httpConnect(socket: Socket, ip: String, port: Int): Boolean {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()

        val request = "CONNECT $ip:$port HTTP/1.1\r\nHost: $ip:$port\r\n\r\n"
        out.write(request.toByteArray(Charsets.US_ASCII))
        out.flush()

        // Read response line (e.g. "HTTP/1.1 200 Connection established\r\n")
        val response = StringBuilder()
        var prev = 0
        while (true) {
            val b = inp.read()
            if (b == -1) return false
            response.append(b.toChar())
            // Detect end of response headers (\r\n\r\n)
            if (prev == '\r'.code && b == '\n'.code && response.endsWith("\r\n\r\n")) break
            if (response.length > 4096) return false
            prev = b
        }
        return response.contains("200")
    }

    private fun InputStream.readFully(buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = this.read(buf, read, buf.size - read)
            if (n == -1) return false
            read += n
        }
        return true
    }
}
