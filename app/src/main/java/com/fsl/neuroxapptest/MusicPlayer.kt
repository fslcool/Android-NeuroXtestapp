package com.fsl.neuroxapptest

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 音乐播放器封装。
 */
object MusicPlayer {

    private var player: MediaPlayer? = null
    private var context: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    var currentIndex: Int = 0
        private set

    var isPlaying: Boolean = false
        private set

    /** 播放状态变化时通知 UI */
    var onStateChanged: (() -> Unit)? = null

    /** 播放进度（每秒）通知 UI */
    var onProgress: ((current: Int, duration: Int) -> Unit)? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun playIndex(index: Int) {
        val ctx = context ?: return
        if (index !in SongLibrary.SONGS.indices) return

        stop()
        currentIndex = index
        val song = SongLibrary.SONGS[index]

        try {
            player = MediaPlayer().apply {
                ctx.assets.openFd(song.assetPath).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = true
                prepare()
                start()
            }
            isPlaying = true
            startProgressLoop()
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
        }
        onStateChanged?.invoke()
    }

    fun togglePlayPause() {
        val p = player ?: run {
            playIndex(currentIndex)
            return
        }
        if (p.isPlaying) {
            p.pause()
            isPlaying = false
            stopProgressLoop()
        } else {
            p.start()
            isPlaying = true
            startProgressLoop()
        }
        onStateChanged?.invoke()
    }

    fun next() {
        val next = (currentIndex + 1) % SongLibrary.SONGS.size
        playIndex(next)
    }

    fun prev() {
        val prev = if (currentIndex - 1 < 0) SongLibrary.SONGS.size - 1 else currentIndex - 1
        playIndex(prev)
    }

    fun stop() {
        stopProgressLoop()
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        isPlaying = false
    }

    fun release() {
        stop()
        context = null
        onStateChanged = null
        onProgress = null
    }

    /** 跳转到指定毫秒 */
    fun seekTo(ms: Int) {
        player?.seekTo(ms)
        onProgress?.invoke(player?.currentPosition ?: 0, player?.duration ?: 0)
    }

    /** 获取当前时长（毫秒） */
    fun getDuration(): Int {
        return try {
            player?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** 获取当前位置（毫秒） */
    fun getCurrentPosition(): Int {
        return try {
            player?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // ============ 进度循环 ============

    private fun startProgressLoop() {
        stopProgressLoop()
        progressRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    onProgress?.invoke(getCurrentPosition(), getDuration())
                    handler.postDelayed(this, 500)   // 每 500ms 刷一次
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressLoop() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }
}