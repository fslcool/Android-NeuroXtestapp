package com.fsl.neuroxapptest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * 视频流客户端。
 *
 * 连接 CS 的视频 WebSocket（默认 8082），接收 JPEG 帧。
 */
object VideoClient {

    private var ws: WebSocket? = null
    private var currentUrl: String = ""

    /** 回调：收到一帧画面（在主线程） */
    var onFrame: ((type: Int, bitmap: Bitmap) -> Unit)? = null

    /** 连接状态变化 */
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 连接视频服务。
     *
     * @param host CS 的 IP
     * @param port 视频端口（默认 8082）
     */
    fun connect(host: String, port: Int) {
        val url = "ws://$host:$port/"
        close()
        currentUrl = url

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post { onConnected?.invoke() }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                android.util.Log.d("CT-Video", "收到二进制帧 size=${bytes.size}")

                val frame = VideoFrameProtocol.decode(bytes.toByteArray())
                if (frame == null) {
                    mainHandler.post {
                        android.widget.Toast.makeText(
                            com.fsl.neuroxapptest.MonitorActivity.instance,
                            "视频帧解码失败",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                }

                val bitmap = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)
                if (bitmap == null) {
                    mainHandler.post {
                        android.widget.Toast.makeText(
                            com.fsl.neuroxapptest.MonitorActivity.instance,
                            "JPEG 解码失败",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                }

                mainHandler.post {
                    if (onFrame == null) {
                        android.widget.Toast.makeText(
                            com.fsl.neuroxapptest.MonitorActivity.instance,
                            "onFrame 为空！",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onFrame?.invoke(frame.type, bitmap)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // CS 发来的 text 是 video-hello，忽略
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post { onDisconnected?.invoke() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post { onDisconnected?.invoke() }
            }
        })
    }

    fun close() {
        ws?.close(1000, "bye")
        ws = null
        currentUrl = ""
        // 注意：这里不清理回调，由 Activity 自己负责
    }

    /** 真正要清回调时，由 Activity 显式调用 */
    fun clearCallbacks() {
        onFrame = null
        onConnected = null
        onDisconnected = null
    }
}