package com.fsl.neuroxapptest

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {

    // 状态监听器引用（用于 onDestroy 时移除）
    private val stateListener: (ConnState) -> Unit = { state ->
        runOnUiThread {
            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            tvStatus.text = when (state) {
                ConnState.CONNECTING -> "⏳ 连接中..."
                ConnState.CONNECTED -> "✅ 已连接电脑"
                ConnState.FAILED -> "❌ 连接失败，5 秒后重试..."
                ConnState.DISCONNECTED -> "⚪ 已断开"
            }
            tvStatus.setTextColor(
                when (state) {
                    ConnState.CONNECTED -> 0xFF1BA39A.toInt()
                    ConnState.FAILED -> 0xFFF44336.toInt()
                    else -> 0xFF666666.toInt()
                }
            )
        }
    }

    private val messageListener: (String, String) -> Unit = { result, state ->
        runOnUiThread {
            val tvResult = findViewById<TextView>(R.id.tvResult)
            tvResult.text = "结果：$result\n状态：$state"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        // 订阅状态和消息
        NetworkManager.addStateListener(stateListener)
        NetworkManager.addMessageListener(messageListener)

        // 4 个按钮
        findViewById<TextView>(R.id.btnPrepare).setOnClickListener {
            NetworkManager.sendControl("PREPARE")
        }
        findViewById<TextView>(R.id.btnStart).setOnClickListener {
            NetworkManager.sendControl("START")
        }
        findViewById<TextView>(R.id.btnPause).setOnClickListener {
            NetworkManager.sendControl("PAUSE")
        }
        findViewById<TextView>(R.id.btnEnd).setOnClickListener {
            NetworkManager.sendControl("END")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除监听器（避免内存泄漏）
        NetworkManager.removeStateListener(stateListener)
        NetworkManager.removeMessageListener(messageListener)
    }
}