package com.fsl.neuroxapptest

import android.content.Context

/**
 * LRC 歌词解析。
 */
object LrcParser {

    /** 一行歌词 */
    data class LrcLine(val timeMs: Long, val text: String)

    /**
     * 从 assets 读 LRC 文件，解析成行列表。
     * 文件不存在返回空列表。
     */
    fun parse(ctx: Context, path: String?): List<LrcLine> {
        if (path == null) return emptyList()
        return try {
            val content = ctx.assets.open(path).bufferedReader().readText()
            parseContent(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseContent(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        for (rawLine in content.lines()) {
            val match = regex.find(rawLine) ?: continue
            val (mm, ss, ms, text) = match.destructured
            val minute = mm.toLong()
            val second = ss.toLong()
            val milli = if (ms.length == 2) ms.toLong() * 10 else ms.toLong()
            val timeMs = minute * 60_000 + second * 1000 + milli
            if (text.isNotBlank()) {
                lines.add(LrcLine(timeMs, text.trim()))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 根据当前播放时间，找到应该高亮的歌词索引。
     * 返回 -1 表示"还没有到第一行"。
     */
    fun findLineIndex(lines: List<LrcLine>, currentMs: Int): Int {
        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }
}