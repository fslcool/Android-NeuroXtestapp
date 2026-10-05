package com.fsl.neuroxapptest

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 对应 CS 的 VideoFrameProtocol.java。
 * 解析 CS 通过视频 WebSocket 发来的二进制帧。
 */
object VideoFrameProtocol {

    private const val MAGIC = 0x43425653       // "CBVS"
    const val HEADER_BYTES = 24

    const val STREAM_CAMERA = 1                 // 被试摄像头
    const val STREAM_SCREEN = 2                 // CS 电脑屏幕

    data class Frame(
        val type: Int,
        val timestampMillis: Long,
        val sequence: Long,
        val jpeg: ByteArray
    )

    /**
     * 解析一帧。
     * 失败返回 null。
     */
    fun decode(bytes: ByteArray): Frame? {
        if (bytes.size < HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.int != MAGIC) return null
        if (buffer.get() != 1.toByte()) return null
        val type = buffer.get().toInt()
        buffer.short  // reserved
        val timestamp = buffer.long
        val sequence = buffer.int.toLong() and 0xFFFFFFFFL
        val length = buffer.int
        if (length <= 0 || length != bytes.size - HEADER_BYTES) return null
        val jpeg = ByteArray(length)
        buffer.get(jpeg)
        return Frame(type, timestamp, sequence, jpeg)
    }
}