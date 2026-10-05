package com.fsl.neuroxapptest

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * 连接状态
 */
enum class ConnState {
    CONNECTING,     // 正在连接（含重连中）
    CONNECTED,      // 已连接
    FAILED,         // 连接失败（等 5 秒重连）
    DISCONNECTED    // 主动断开
}

object NetworkManager {

    // ===== 配置 =====
    private const val PREF_NAME = "neurox_config"
    private const val KEY_SERVER = "server_address"
    private const val DEFAULT_SERVER = "10.1.125.9:8081"
    private const val RECONNECT_INTERVAL = 5_000L   // 5 秒重连

    // ===== OkHttp 客户端（带心跳）=====
    private val client = OkHttpClient.Builder()
        .pingInterval(1, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // ===== 当前状态 =====
    private var ws: WebSocket? = null
    private var currentState: ConnState = ConnState.DISCONNECTED
    private var currentRemoteState: String = ""   // 电脑传来的状态：READY / RUNNING / ...
    private var appContext: Context? = null
    private var reconnectHandler: Handler? = null
    private var reconnectRunnable: Runnable? = null

    // ===== 监听器列表（支持多个页面同时订阅）=====
    private val stateListeners = mutableListOf<(ConnState) -> Unit>()
    private val messageListeners = mutableListOf<(String, String) -> Unit>()
    private val rawMessageListeners = mutableListOf<(org.json.JSONObject) -> Unit>()
    // ===== 对外接口 =====

    /**
     * 获取当前状态
     */
    fun getState(): ConnState = currentState

    /**
     * 获取当前保存的服务器地址（不带 ws://）
     */
    fun getServerAddress(): String {
        val ctx = appContext ?: return DEFAULT_SERVER
        val sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
    }

    /**
     * 保存服务器地址，并立即用新地址重连
     */
    fun saveServerAddressAndReconnect(context: Context, address: String) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SERVER, address).apply()
        reconnectNow()
    }

    /**
     * App 启动时调一次
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        connect()
    }

    /**
     * 立即重连（地址变了 / 手动触发）
     */
    fun reconnectNow() {
        cancelReconnect()
        closeWebSocket()
        connect()
    }

    /**
     * 主动关闭（退出 App 时用）
     */
    fun close() {
        cancelReconnect()
        setState(ConnState.DISCONNECTED)
        closeWebSocket()
        appContext = null
    }

    /**
     * 发送控制命令。
     *
     * @param command 指令名（如 "PREPARE" / "START"）
     * @param extra   额外字段（可选），会合并进 JSON
     */
    fun sendControl(command: String, extra: Map<String, Any>? = null) {
        val json = JSONObject().apply {
            put("type", "control")
            put("command", command)
            put("experimentId", "exp_001")
            extra?.forEach { (k, v) ->
                put(k, v)
            }
        }
        ws?.send(json.toString())
    }

    // ===== 监听器注册 =====

    fun addStateListener(listener: (ConnState) -> Unit) {
        stateListeners.add(listener)
        // 立即回调一次当前状态
        listener(currentState)
    }

    fun removeStateListener(listener: (ConnState) -> Unit) {
        stateListeners.remove(listener)
    }

    fun addMessageListener(listener: (String, String) -> Unit) {
        messageListeners.add(listener)
    }

    fun removeMessageListener(listener: (String, String) -> Unit) {
        messageListeners.remove(listener)
    }

    fun addRawMessageListener(listener: (org.json.JSONObject) -> Unit) {
        rawMessageListeners.add(listener)
    }

    fun removeRawMessageListener(listener: (org.json.JSONObject) -> Unit) {
        rawMessageListeners.remove(listener)
    }

    /**
     * 只返回 IP（不含端口）。
     * 比如 "192.168.1.100:8081" → "192.168.1.100"。
     */
    fun getServerHost(): String {
        val addr = getServerAddress()
        return addr.substringBefore(":").trim()
    }

    // ===== 内部实现 =====

    private fun connect() {
        val ctx = appContext ?: return
        val addr = getServerAddress()
        val url = "ws://$addr/"

        setState(ConnState.CONNECTING)

        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                setState(ConnState.CONNECTED)
                cancelReconnect()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // ⭐ 全局显示（只显示非 impedance_update）
                try {
                    val obj = JSONObject(text)
                    val type = obj.optString("type", "")
                    if (type != "impedance_update") {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val ctx = appContext
                            if (ctx != null) {
                                android.widget.Toast.makeText(
                                    ctx,
                                    "收到: $text",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略
                }

                try {
                    val obj = JSONObject(text)
                    val type = obj.optString("type", "")
                    val result = obj.optString("result", "")
                    val state = obj.optString("state", "")

                    // ⭐ error 消息全局 Toast
                    if (type == "error") {
                        val code = obj.optString("code", "")
                        val message = obj.optString("message", "")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val ctx = appContext
                            if (ctx != null) {
                                android.widget.Toast.makeText(
                                    ctx,
                                    "⚠ $code: $message",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    // control_result 和 state 都更新 remoteState
                    if ((type == "control_result" || type == "state") && state.isNotBlank()) {
                        currentRemoteState = state
                        for (l in messageListeners.toList()) {
                            l(result, state)
                        }
                    }

                    // 所有消息都推给 raw 监听器
                    for (l in rawMessageListeners.toList()) {
                        l(obj)
                    }
                } catch (e: Exception) {
                    for (l in messageListeners.toList()) {
                        l("FAILED", "解析错误")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                setState(ConnState.FAILED)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (currentState != ConnState.DISCONNECTED) {
                    setState(ConnState.FAILED)
                    scheduleReconnect()
                }
            }
        })
    }

    private fun closeWebSocket() {
        ws?.close(1000, "正常关闭")
        ws = null
    }

    /**
     * 安排 5 秒后重连
     */
    private fun scheduleReconnect() {
        cancelReconnect()
        reconnectHandler = Handler(Looper.getMainLooper())
        reconnectRunnable = Runnable {
            if (currentState != ConnState.DISCONNECTED) {
                connect()
            }
        }
        reconnectHandler?.postDelayed(reconnectRunnable!!, RECONNECT_INTERVAL)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { reconnectHandler?.removeCallbacks(it) }
        reconnectRunnable = null
        reconnectHandler = null
    }

    private fun setState(newState: ConnState) {
        currentState = newState
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            for (l in stateListeners.toList()) {
                l(newState)
            }
        }
    }

    /**
     * 获取电脑端传来的最新状态
     */
    fun getRemoteState(): String = currentRemoteState

    /**
     * 绑定一个页面的"左下角状态栏 + 下一步按钮"
     * - 自动更新 tvServerAddr / tvConnStatus
     * - 自动启用/禁用 btnNext
     * 返回一个监听器对象，页面 onDestroy 时用 removeStateListener 移除
     */
    fun bindStatusUI(
        activity: android.app.Activity,
        tvServerAddr: android.widget.TextView,
        tvConnStatus: android.widget.TextView,
        btnNext: android.view.View?
    ): (ConnState) -> Unit {
        val listener: (ConnState) -> Unit = { state ->
            activity.runOnUiThread {
                // 1. 更新服务器地址（固定显示）
                tvServerAddr.text = getServerAddress()

                // 2. 更新状态文字和颜色
                when (state) {
                    ConnState.CONNECTING -> {
                        tvConnStatus.text = "连接中..."
                        tvConnStatus.setTextColor(0xFF666666.toInt())
                    }
                    ConnState.CONNECTED -> {
                        tvConnStatus.text = "成功 ✅"
                        tvConnStatus.setTextColor(0xFF1BA39A.toInt())
                    }
                    ConnState.FAILED -> {
                        tvConnStatus.text = "失败 ❌"
                        tvConnStatus.setTextColor(0xFFF44336.toInt())
                    }
                    ConnState.DISCONNECTED -> {
                        tvConnStatus.text = "已断开"
                        tvConnStatus.setTextColor(0xFF999999.toInt())
                    }
                }

                // 3. 更新"下一步"按钮状态
                btnNext?.let {
                    val enabled = (state == ConnState.CONNECTED)
                    it.isEnabled = enabled
                    it.alpha = if (enabled) 1.0f else 0.5f
                }
            }
        }
        addStateListener(listener)
        return listener
    }
}