package com.whoismept.justproxy.service.vpn

object PacketBuilder {

    fun buildTcpPacket(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        seqNum: Long, ackNum: Long,
        flags: Int,
        windowSize: Int = 65535,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {
        val ipLen = 20
        val tcpLen = 20
        val total = ipLen + tcpLen + payload.size
        val pkt = ByteArray(total)

        // IPv4 header
        pkt[0] = 0x45.toByte()
        pkt[1] = 0x00
        pkt[2] = (total shr 8).toByte()
        pkt[3] = (total and 0xFF).toByte()
        pkt[4] = 0; pkt[5] = 0       // identification
        pkt[6] = 0x40; pkt[7] = 0x00 // don't fragment
        pkt[8] = 64                   // TTL
        pkt[9] = 6                    // TCP
        pkt[10] = 0; pkt[11] = 0     // checksum placeholder
        srcIp.copyInto(pkt, 12)
        dstIp.copyInto(pkt, 16)
        val ipCsum = checksum(pkt, 0, ipLen)
        pkt[10] = (ipCsum shr 8).toByte()
        pkt[11] = (ipCsum and 0xFF).toByte()

        // TCP header
        val t = ipLen
        pkt[t]     = (srcPort shr 8).toByte()
        pkt[t + 1] = (srcPort and 0xFF).toByte()
        pkt[t + 2] = (dstPort shr 8).toByte()
        pkt[t + 3] = (dstPort and 0xFF).toByte()
        pkt[t + 4] = ((seqNum shr 24) and 0xFF).toByte()
        pkt[t + 5] = ((seqNum shr 16) and 0xFF).toByte()
        pkt[t + 6] = ((seqNum shr 8) and 0xFF).toByte()
        pkt[t + 7] = (seqNum and 0xFF).toByte()
        pkt[t + 8]  = ((ackNum shr 24) and 0xFF).toByte()
        pkt[t + 9]  = ((ackNum shr 16) and 0xFF).toByte()
        pkt[t + 10] = ((ackNum shr 8) and 0xFF).toByte()
        pkt[t + 11] = (ackNum and 0xFF).toByte()
        pkt[t + 12] = (5 shl 4).toByte()  // data offset = 5 words
        pkt[t + 13] = flags.toByte()
        pkt[t + 14] = (windowSize shr 8).toByte()
        pkt[t + 15] = (windowSize and 0xFF).toByte()
        pkt[t + 16] = 0; pkt[t + 17] = 0  // checksum placeholder
        pkt[t + 18] = 0; pkt[t + 19] = 0  // urgent pointer

        if (payload.isNotEmpty()) payload.copyInto(pkt, t + tcpLen)

        val tcpCsum = tcpChecksum(srcIp, dstIp, pkt, t, tcpLen + payload.size)
        pkt[t + 16] = (tcpCsum shr 8).toByte()
        pkt[t + 17] = (tcpCsum and 0xFF).toByte()

        return pkt
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun tcpChecksum(srcIp: ByteArray, dstIp: ByteArray, pkt: ByteArray, tcpOffset: Int, tcpLength: Int): Int {
        // RFC 793 pseudo-header: srcIP + dstIP + zero + proto(6) + tcp segment length
        val pseudo = ByteArray(12 + tcpLength)
        srcIp.copyInto(pseudo, 0)
        dstIp.copyInto(pseudo, 4)
        pseudo[8] = 0
        pseudo[9] = 6
        pseudo[10] = (tcpLength shr 8).toByte()
        pseudo[11] = (tcpLength and 0xFF).toByte()
        pkt.copyInto(pseudo, 12, tcpOffset, tcpOffset + tcpLength)
        // zero out checksum field in pseudo copy
        pseudo[12 + 16] = 0
        pseudo[12 + 17] = 0
        return checksum(pseudo, 0, pseudo.size)
    }
}
