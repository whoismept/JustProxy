package com.whoismept.justproxy.service.vpn

data class TcpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val seqNum: Long,
    val ackNum: Long,
    val dataOffset: Int,
    val flags: Int,
    val windowSize: Int
) {
    val headerLength get() = dataOffset * 4

    val isSyn get() = (flags and FLAG_SYN) != 0
    val isAck get() = (flags and FLAG_ACK) != 0
    val isFin get() = (flags and FLAG_FIN) != 0
    val isRst get() = (flags and FLAG_RST) != 0
    val isPsh get() = (flags and FLAG_PSH) != 0

    companion object {
        const val FLAG_FIN = 0x01
        const val FLAG_SYN = 0x02
        const val FLAG_RST = 0x04
        const val FLAG_PSH = 0x08
        const val FLAG_ACK = 0x10

        fun parse(buffer: ByteArray, offset: Int): TcpHeader? {
            if (buffer.size < offset + 20) return null
            val srcPort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val dstPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            val seqNum = ((buffer[offset + 4].toLong() and 0xFF) shl 24) or
                         ((buffer[offset + 5].toLong() and 0xFF) shl 16) or
                         ((buffer[offset + 6].toLong() and 0xFF) shl 8) or
                          (buffer[offset + 7].toLong() and 0xFF)
            val ackNum = ((buffer[offset + 8].toLong() and 0xFF) shl 24) or
                         ((buffer[offset + 9].toLong() and 0xFF) shl 16) or
                         ((buffer[offset + 10].toLong() and 0xFF) shl 8) or
                          (buffer[offset + 11].toLong() and 0xFF)
            val dataOffset = (buffer[offset + 12].toInt() and 0xFF) shr 4
            val flags = buffer[offset + 13].toInt() and 0xFF
            val windowSize = ((buffer[offset + 14].toInt() and 0xFF) shl 8) or (buffer[offset + 15].toInt() and 0xFF)
            return TcpHeader(srcPort, dstPort, seqNum, ackNum, dataOffset, flags, windowSize)
        }
    }
}
