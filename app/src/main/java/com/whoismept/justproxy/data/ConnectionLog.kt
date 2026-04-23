package com.whoismept.justproxy.data

enum class ConnectionStatus(val label: String) {
    CONNECTED("OK"),
    SSL_PINNED("Pinned"),
    UNKNOWN_CA("Unknown CA"),
    ECH("ECH"),
    FAILED("Failed")
}

data class ConnectionLog(
    val id         : Long    = System.nanoTime(),
    val timestamp  : Long    = System.currentTimeMillis(),
    val host       : String,
    val protocol   : String,
    val status     : ConnectionStatus,
    val durationMs : Long,
    val tlsVersion : String? = null,  // "TLS 1.3" / "TLS 1.2" / null for HTTP
    val alertCode  : Int     = -1,    // -1 = none; 46 = cert_unknown; 48 = unknown_ca; etc.
    val hasEch     : Boolean = false,
    val hasGrease  : Boolean = false,
    val via        : String  = ""     // "Transparent" / "System Proxy"
)
