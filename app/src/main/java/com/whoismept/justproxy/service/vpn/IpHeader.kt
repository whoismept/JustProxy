package com.whoismept.justproxy.service.vpn

import java.net.InetAddress

data class IpHeader(
    val version: Int,
    val ihl: Int,
    val totalLength: Int,
    val protocol: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray
) {
    val headerLength get() = ihl * 4
    val srcIpStr: String get() = InetAddress.getByAddress(srcIp).hostAddress ?: ""
    val dstIpStr: String get() = InetAddress.getByAddress(dstIp).hostAddress ?: ""

    companion object {
        const val PROTO_TCP = 6
        const val PROTO_UDP = 17

        fun parse(buffer: ByteArray, length: Int): IpHeader? {
            if (length < 20) return null
            val versionIhl = buffer[0].toInt() and 0xFF
            if (versionIhl shr 4 != 4) return null  // IPv4 only
            val ihl = versionIhl and 0x0F
            if (ihl < 5 || ihl * 4 > length) return null
            val totalLength = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
            val protocol = buffer[9].toInt() and 0xFF
            val srcIp = buffer.sliceArray(12..15)
            val dstIp = buffer.sliceArray(16..19)
            return IpHeader(versionIhl shr 4, ihl, totalLength, protocol, srcIp, dstIp)
        }
    }

    override fun equals(other: Any?) = other is IpHeader &&
            srcIp.contentEquals(other.srcIp) && dstIp.contentEquals(other.dstIp)
    override fun hashCode() = srcIp.contentHashCode() * 31 + dstIp.contentHashCode()
}
