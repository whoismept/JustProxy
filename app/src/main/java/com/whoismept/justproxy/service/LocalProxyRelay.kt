package com.whoismept.justproxy.service

import android.util.Log
import com.whoismept.justproxy.data.ConnectionLog
import com.whoismept.justproxy.data.ConnectionLogStore
import com.whoismept.justproxy.data.ConnectionStatus
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "LocalProxyRelay"

private const val TLS_ALERT     = 0x15.toByte()
private const val TLS_HANDSHAKE = 0x16.toByte()

// Synthetic TLS fatal alert: certificate_unknown (46)
// Sent to Burp when the app closes the socket without a proper TLS Alert,
// so Burp logs "certificate_unknown" instead of "Unsupported SSL message".
private val SYNTHETIC_CERT_UNKNOWN = byteArrayOf(
    0x15,       // record type = Alert
    0x03, 0x03, // TLS 1.2 legacy version
    0x00, 0x02, // length = 2
    0x02,       // level = fatal
    0x2E        // description = 46 = certificate_unknown
)

class LocalProxyRelay(
    private val upstreamHost: String,
    private val upstreamPort: Int
) {
    companion object {
        const val RELAY_PORT = 8887
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null

    fun start() {
        scope.launch {
            try {
                serverSocket = ServerSocket(RELAY_PORT, 128, InetAddress.getByName("127.0.0.1"))
                Log.i(TAG, "Relay up on 127.0.0.1:$RELAY_PORT → $upstreamHost:$upstreamPort")
                while (isActive) {
                    val client = try { serverSocket!!.accept() } catch (_: Exception) { break }
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Relay fatal: ${e.message}")
            }
        }
    }

    fun stop() {
        scope.cancel()
        serverSocket?.runCatching { close() }
        serverSocket = null
    }

    // ── Per-connection handler ────────────────────────────────────────────────

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.soTimeout = 15_000
            val cin  = client.getInputStream()
            val peek = ByteArray(4096)
            val n    = cin.read(peek)
            if (n <= 0) { client.close(); return@withContext }
            val first = peek.copyOf(n)

            when {
                isTls(first)     -> handleHttps(client, cin, first)
                isConnect(first) -> handleConnect(client, cin, first)
                isHttp(first)    -> handleHttp(client, cin, first)
                else -> {
                    Log.d(TAG, "Unknown protocol (not HTTP/TLS/CONNECT), dropping")
                    client.close()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client error: ${e.message}")
            client.runCatching { close() }
        }
    }

    // ── HTTPS ─────────────────────────────────────────────────────────────────

    private suspend fun handleHttps(client: Socket, remaining: InputStream, firstBytes: ByteArray) {
        val sni = extractSni(firstBytes)
        if (sni == null) {
            Log.w(TAG, "No SNI — cannot determine HTTPS destination, dropping")
            client.close(); return
        }

        val proxy = connectUpstream() ?: run {
            ConnectionLogStore.add(ConnectionLog(host = sni, protocol = "HTTPS", status = ConnectionStatus.FAILED, durationMs = 0, via = "Transparent"))
            client.close(); return
        }
        val pout = proxy.getOutputStream()
        val pin  = proxy.getInputStream()

        pout.write("CONNECT $sni:443 HTTP/1.1\r\nHost: $sni:443\r\nProxy-Connection: keep-alive\r\n\r\n".toByteArray())
        pout.flush()

        val response = readUntilBlankLine(pin)
        if (!response.contains("200")) {
            Log.w(TAG, "CONNECT rejected for $sni: ${response.lines().firstOrNull()}")
            ConnectionLogStore.add(ConnectionLog(host = sni, protocol = "HTTPS", status = ConnectionStatus.FAILED, durationMs = 0, via = "Transparent"))
            proxy.close(); client.close(); return
        }

        val info = parseClientHello(sni, firstBytes)
        pout.write(firstBytes); pout.flush()
        relay(client, remaining, proxy, sni, "HTTPS", info.hasEch, info.hasGrease, info.tlsVersion, "Transparent")
    }

    // ── Explicit CONNECT (system proxy mode) ──────────────────────────────────
    // Apps using the system HTTP proxy setting send explicit CONNECT requests.
    // We intercept them here so we can log HTTPS connections from system proxy mode.

    private suspend fun handleConnect(client: Socket, remaining: InputStream, firstBytes: ByteArray) {
        val requestLine = String(firstBytes, Charsets.ISO_8859_1)
            .lineSequence().firstOrNull()?.trim() ?: run { client.close(); return }
        // CONNECT api.example.com:443 HTTP/1.1
        val target = requestLine.split(" ").getOrNull(1) ?: run { client.close(); return }
        val host   = target.substringBefore(":")

        val proxy = connectUpstream() ?: run {
            ConnectionLogStore.add(ConnectionLog(host = host, protocol = "HTTPS", status = ConnectionStatus.FAILED, durationMs = 0, via = "System Proxy"))
            runCatching {
                client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
                client.getOutputStream().flush()
            }
            client.close(); return
        }

        runCatching {
            proxy.getOutputStream().write("CONNECT $target HTTP/1.1\r\nHost: $target\r\nProxy-Connection: keep-alive\r\n\r\n".toByteArray())
            proxy.getOutputStream().flush()
        }.onFailure { proxy.close(); client.close(); return }

        val response = readUntilBlankLine(proxy.getInputStream())
        if (!response.contains("200")) {
            Log.w(TAG, "CONNECT rejected for $host: ${response.lines().firstOrNull()}")
            ConnectionLogStore.add(ConnectionLog(host = host, protocol = "HTTPS", status = ConnectionStatus.FAILED, durationMs = 0, via = "System Proxy"))
            runCatching {
                client.getOutputStream().write(response.toByteArray(Charsets.ISO_8859_1))
                client.getOutputStream().flush()
            }
            proxy.close(); client.close(); return
        }

        runCatching {
            client.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
            client.getOutputStream().flush()
        }.onFailure { proxy.close(); client.close(); return }

        Log.d(TAG, "→ CONNECT tunnel: $host")
        relay(client, remaining, proxy, host, "HTTPS", false, false, null, "System Proxy")
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private suspend fun handleHttp(client: Socket, remaining: InputStream, firstBytes: ByteArray) {
        val request = String(firstBytes, Charsets.ISO_8859_1)
        val host    = extractHttpHost(request)
        if (host == null) {
            Log.w(TAG, "No Host header in HTTP request, dropping")
            client.close(); return
        }
        val modified = toAbsoluteUri(request, host) ?: run { client.close(); return }
        val proxy = connectUpstream() ?: run {
            ConnectionLogStore.add(ConnectionLog(host = host, protocol = "HTTP", status = ConnectionStatus.FAILED, durationMs = 0, via = "System Proxy"))
            client.close(); return
        }
        proxy.getOutputStream().apply { write(modified.toByteArray(Charsets.ISO_8859_1)); flush() }
        relay(client, remaining, proxy, host, "HTTP", false, false, null, "System Proxy")
    }

    // ── Bidirectional relay ───────────────────────────────────────────────────

    private suspend fun relay(
        client     : Socket,
        clientIn   : InputStream,
        proxy      : Socket,
        host       : String,
        protocol   : String  = "HTTPS",
        hasEch     : Boolean = false,
        hasGrease  : Boolean = false,
        tlsVersion : String? = null,
        via        : String  = ""
    ) {
        val startMs = System.currentTimeMillis()

        // Use async so we can retrieve the alert description from app→proxy
        // and still drive both pipes concurrently.
        val alertDesc: Int? = coroutineScope {
            val appToProxy = async {
                pipeMonitored(clientIn, proxy.getOutputStream(), host, "app→proxy")
            }
            launch {
                pipeMonitored(proxy.getInputStream(), client.getOutputStream(), host, "proxy→app")
                // If Burp closes first, tear down the client side so appToProxy terminates.
                client.runCatching { close() }
            }

            val result  = appToProxy.await()
            val elapsed = System.currentTimeMillis() - startMs

            if (result == null && elapsed < 3_000) {
                // App closed the socket without a proper TLS Alert.
                // Root cause: checkServerTrusted() threw CertificateException but the SSL
                // library closed the underlying TCP socket instead of writing a TLS Alert record.
                // Without this injection Burp logs "Unsupported or unrecognized SSL message".
                // With it, Burp logs the expected "certificate_unknown" event.
                Log.w(TAG, "⚡ [$host] App closed without TLS Alert (${elapsed}ms) — injecting synthetic certificate_unknown for Burp")
                runCatching {
                    proxy.getOutputStream().write(SYNTHETIC_CERT_UNKNOWN)
                    proxy.getOutputStream().flush()
                }
            }
            // Closing proxy unblocks the proxy→app pipe above.
            proxy.runCatching { close() }
            result
        }

        val elapsed = System.currentTimeMillis() - startMs
        val syntheticPinning = alertDesc == null && elapsed < 3_000
        val status = when {
            hasEch                               -> ConnectionStatus.ECH
            syntheticPinning                     -> ConnectionStatus.SSL_PINNED
            alertDesc == 42 || alertDesc == 46   -> ConnectionStatus.SSL_PINNED
            alertDesc == 48                      -> ConnectionStatus.UNKNOWN_CA
            else                                 -> ConnectionStatus.CONNECTED
        }
        val resolvedAlertCode = if (syntheticPinning) 46 else alertDesc ?: -1
        ConnectionLogStore.add(ConnectionLog(
            host       = host,
            protocol   = protocol,
            status     = status,
            durationMs = elapsed,
            tlsVersion = tlsVersion,
            alertCode  = resolvedAlertCode,
            hasEch     = hasEch,
            hasGrease  = hasGrease,
            via        = via
        ))

        client.runCatching { close() }
        proxy.runCatching  { close() }
    }

    // ── Monitored pipe ────────────────────────────────────────────────────────

    /**
     * Pipes src → dst, inspecting the first chunk for TLS Alerts and anomalies.
     * Returns the TLS Alert description byte (0–255) if one was seen, null otherwise.
     * 0 = close_notify (clean close), 46 = certificate_unknown, 48 = unknown_ca, etc.
     */
    private fun pipeMonitored(
        src: InputStream, dst: OutputStream,
        host: String, direction: String
    ): Int? {
        val buf = ByteArray(8192)
        var firstChunk = true
        var alertDesc: Int? = null

        try {
            while (true) {
                val n = src.read(buf)
                if (n == -1) break

                if (firstChunk && n >= 1) {
                    alertDesc  = analyzeFirstChunk(buf, n, host, direction)
                    firstChunk = false
                }

                dst.write(buf, 0, n)
                dst.flush()
            }
        } catch (_: Exception) {
            // IOException = abrupt close (TCP RST, timeout, etc.)
        }

        return alertDesc
    }

    /**
     * Analyses the first chunk of a pipe.
     * Returns the TLS Alert description byte if the chunk is a TLS Alert, null otherwise.
     */
    private fun analyzeFirstChunk(buf: ByteArray, n: Int, host: String, direction: String): Int? {
        val hexDump = buf.take(minOf(n, 16)).joinToString(" ") { "%02X".format(it) }

        return when {
            // ── TLS Alert ────────────────────────────────────────────────────
            buf[0] == TLS_ALERT -> {
                if (n >= 7) {
                    val level = buf[5].toInt() and 0xFF
                    val desc  = buf[6].toInt() and 0xFF
                    logTlsAlert(host, direction, level, desc)
                    desc
                } else {
                    Log.w(TAG, "⚠ TLS Alert [$direction] $host — too short (n=$n) hex=[$hexDump]")
                    -1
                }
            }

            // ── Plain HTTP from Burp inside a TLS tunnel ─────────────────────
            // Burp sends HTTP (e.g. 502) when it can't reach the upstream server.
            // The app's SSL engine sees plain text → "Unsupported SSL message".
            direction == "proxy→app" && looksLikeHttp(buf, n) -> {
                val firstLine = String(buf, 0, minOf(n, 256), Charsets.ISO_8859_1).lines().firstOrNull() ?: ""
                Log.e(TAG, "🔴 BURP SENT HTTP INSIDE TLS TUNNEL [$direction] $host")
                Log.e(TAG, "   → $firstLine")
                Log.e(TAG, "   This is the cause of 'Unsupported SSL message' in Burp event log")
                Log.e(TAG, "   Hex: [$hexDump]")
                null
            }

            // ── Non-TLS from app ──────────────────────────────────────────────
            direction == "app→proxy" && !looksLikeTls(buf, n) -> {
                Log.w(TAG, "🟠 NON-TLS DATA FROM APP [$direction] $host")
                Log.w(TAG, "   First byte: 0x%02X  hex: [$hexDump]".format(buf[0]))
                null
            }

            // ── Normal TLS record — debug only ───────────────────────────────
            else -> {
                val type = when (buf[0]) {
                    0x14.toByte() -> "ChangeCipherSpec"
                    0x16.toByte() -> "Handshake"
                    0x17.toByte() -> "AppData"
                    else          -> "0x%02X".format(buf[0])
                }
                Log.d(TAG, "TLS [$direction] $host — $type n=$n")
                null
            }
        }
    }

    // ── ClientHello diagnostics ───────────────────────────────────────────────

    private data class ClientHelloInfo(
        val tlsVersion : String?,
        val hasEch     : Boolean,
        val hasGrease  : Boolean
    )

    private fun parseClientHello(sni: String, data: ByteArray): ClientHelloInfo {
        if (data.size < 10) {
            Log.w(TAG, "⚠ ClientHello too short for $sni")
            return ClientHelloInfo(null, false, false)
        }
        val handshakeT = data[5].toInt() and 0xFF
        if (handshakeT != 0x01) {
            Log.e(TAG, "🔴 [$sni] Expected ClientHello(01) but got type 0x%02X".format(handshakeT))
            return ClientHelloInfo(null, false, false)
        }
        val hasEch     = containsExtension(data, 0xFE0D)
        val hasGrease  = containsGreaseCipher(data)
        val tlsVersion = parseTlsVersion(data)
        val recordLen  = read16(data, 3)

        val flags = buildString {
            if (tlsVersion != null) append(" $tlsVersion")
            if (hasEch)             append(" ECH⚠")
            if (hasGrease)          append(" GREASE")
        }
        Log.i(TAG, "→ ClientHello $sni | record=%02X%02X len=$recordLen$flags".format(data[1], data[2]))
        if (hasEch) Log.w(TAG, "⚠ [$sni] ECH detected — Burp may not support ECH → 'Unsupported SSL message'")

        return ClientHelloInfo(tlsVersion, hasEch, hasGrease)
    }

    private fun parseTlsVersion(data: ByteArray): String? {
        if (data.size < 11) return null
        // TLS 1.3 always advertises supported_versions extension (type 0x002B)
        if (containsExtension(data, 0x002B)) return "TLS 1.3"
        return when (read16(data, 9)) {
            0x0303 -> "TLS 1.2"
            0x0302 -> "TLS 1.1"
            0x0301 -> "TLS 1.0"
            else   -> null
        }
    }

    private fun containsExtension(data: ByteArray, extType: Int): Boolean {
        if (data.size < 44) return false
        var pos = 43
        pos += 1 + (data[pos].toInt() and 0xFF)
        if (pos + 2 > data.size) return false
        val csLen = read16(data, pos); pos += 2 + csLen
        if (pos + 1 > data.size) return false
        val compLen = data[pos].toInt() and 0xFF; pos += 1 + compLen
        if (pos + 2 > data.size) return false
        val extEnd = minOf(pos + 2 + read16(data, pos), data.size); pos += 2
        while (pos + 4 <= extEnd) {
            val t = read16(data, pos); val l = read16(data, pos + 2)
            if (t == extType) return true
            pos += 4 + l
        }
        return false
    }

    private fun containsGreaseCipher(data: ByteArray): Boolean {
        if (data.size < 46) return false
        var pos = 43
        pos += 1 + (data[pos].toInt() and 0xFF)
        if (pos + 2 > data.size) return false
        val csEnd = minOf(pos + 2 + read16(data, pos), data.size); pos += 2
        while (pos + 2 <= csEnd) {
            if (read16(data, pos) and 0x0F0F == 0x0A0A) return true
            pos += 2
        }
        return false
    }

    // ── Alert logging ─────────────────────────────────────────────────────────

    private fun logTlsAlert(host: String, direction: String, level: Int, desc: Int) {
        val levelStr = if (level == 2) "fatal" else "warning"
        val descStr  = tlsAlertDescription(desc)
        when (desc) {
            42, 46 -> Log.e(TAG, "🔴 SSL PINNING [$direction] $host — $levelStr: $descStr($desc)")
            48     -> Log.e(TAG, "🔴 UNKNOWN CA  [$direction] $host — $levelStr: $descStr($desc)  ← install Burp CA as system cert")
            45     -> Log.w(TAG, "🟡 CERT EXPIRED[$direction] $host — $levelStr: $descStr($desc)")
            0      -> return // close_notify — normal
            else   -> Log.w(TAG, "🟠 TLS Alert   [$direction] $host — $levelStr: $descStr($desc)")
        }
    }

    private fun tlsAlertDescription(code: Int) = when (code) {
        0   -> "close_notify";          10  -> "unexpected_message"
        20  -> "bad_record_mac";        40  -> "handshake_failure"
        42  -> "bad_certificate";       43  -> "unsupported_certificate"
        44  -> "certificate_revoked";   45  -> "certificate_expired"
        46  -> "certificate_unknown";   47  -> "illegal_parameter"
        48  -> "unknown_ca";            49  -> "access_denied"
        50  -> "decode_error";          51  -> "decrypt_error"
        70  -> "protocol_version";      71  -> "insufficient_security"
        80  -> "internal_error";        86  -> "inappropriate_fallback"
        90  -> "user_canceled";         110 -> "unsupported_extension"
        112 -> "unrecognized_name";     116 -> "unknown_psk_identity"
        120 -> "no_application_protocol"
        else -> "unknown($code)"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun looksLikeTls(buf: ByteArray, n: Int) =
        n >= 3 && buf[0] in listOf(0x14, 0x15, 0x16, 0x17).map { it.toByte() } && buf[1] == 0x03.toByte()

    private fun looksLikeHttp(buf: ByteArray, n: Int) =
        n >= 5 && String(buf, 0, minOf(n, 8), Charsets.US_ASCII).let {
            it.startsWith("HTTP/") || it.startsWith("GET ") || it.startsWith("POST ")
        }

    private fun connectUpstream(): Socket? = runCatching {
        Socket(upstreamHost, upstreamPort).also { it.soTimeout = 30_000 }
    }.onFailure { Log.e(TAG, "Cannot reach upstream $upstreamHost:$upstreamPort — ${it.message}") }
     .getOrNull()

    private fun isTls(b: ByteArray) =
        b.size >= 3 && b[0] == 0x16.toByte() && b[1] == 0x03.toByte()

    private fun isConnect(b: ByteArray): Boolean {
        if (b.size < 7) return false
        return b.copyOf(minOf(b.size, 8)).toString(Charsets.US_ASCII).startsWith("CONNECT ")
    }

    private fun isHttp(b: ByteArray): Boolean {
        val s = b.copyOf(minOf(b.size, 8)).toString(Charsets.US_ASCII)
        return listOf("GET ", "POST ", "PUT ", "DELETE ", "HEAD ", "PATCH ", "OPTIONS ").any { s.startsWith(it) }
    }

    private fun extractSni(data: ByteArray): String? {
        if (data.size < 44) return null
        var pos = 43
        pos += 1 + (data[pos].toInt() and 0xFF)
        if (pos + 2 > data.size) return null
        val csLen = read16(data, pos); pos += 2 + csLen
        if (pos + 1 > data.size) return null
        val compLen = data[pos].toInt() and 0xFF; pos += 1 + compLen
        if (pos + 2 > data.size) return null
        val extLen = read16(data, pos); pos += 2
        val extEnd = minOf(pos + extLen, data.size)
        while (pos + 4 <= extEnd) {
            val extType = read16(data, pos); val extDataLen = read16(data, pos + 2); pos += 4
            if (extType == 0x0000 && pos + 5 <= data.size) {
                val nameLen = read16(data, pos + 3); val nameStart = pos + 5
                if (nameStart + nameLen <= data.size)
                    return String(data, nameStart, nameLen, Charsets.US_ASCII)
            }
            pos += extDataLen
        }
        return null
    }

    private fun extractHttpHost(request: String): String? =
        request.lines().firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.trimEnd('\r')

    private fun toAbsoluteUri(request: String, host: String): String? {
        val lines = request.lines(); val firstLine = lines.firstOrNull() ?: return null
        val parts = firstLine.trimEnd().split(" "); if (parts.size < 3) return null
        val uri = parts[1]
        if (uri.startsWith("http://") || uri.startsWith("https://")) return request
        return request.replaceFirst(firstLine, "${parts[0]} http://$host$uri ${parts[2]}")
    }

    private fun readUntilBlankLine(input: InputStream): String {
        val sb = StringBuilder(); var prev = 0
        while (true) {
            val b = input.read()
            if (b == -1 || sb.length > 8192) break
            sb.append(b.toChar())
            if (prev == '\r'.code && b == '\n'.code && sb.endsWith("\r\n\r\n")) break
            prev = b
        }
        return sb.toString()
    }

    private fun read16(b: ByteArray, pos: Int) =
        ((b[pos].toInt() and 0xFF) shl 8) or (b[pos + 1].toInt() and 0xFF)
}
