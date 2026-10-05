package com.fsl.neuroxapptest

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InspectionActivity : AppCompatActivity() {

    // 类的成员变量
    private var statusListener: ((ConnState) -> Unit)? = null
    private var messageListener: ((String, String) -> Unit)? = null
    private var rawMessageListener: ((org.json.JSONObject) -> Unit)? = null

    // 保存最近一次收到的通道数据（用于"或"判断）
    private var lastChannels: List<ChannelInfo> = emptyList()

    // 简单数据类
    data class ChannelInfo(val name: String, val impedance: Double, val status: String)


    private fun renderChannels(channels: org.json.JSONArray) {
//        // Toast 2：显示有多少通道
//        android.widget.Toast.makeText(
//            this,
//            "② 渲染 ${channels.length()} 个通道",
//            android.widget.Toast.LENGTH_SHORT
//        ).show()

        // 更新中间脑电帽图上的 8 个动态圆点
        findViewById<EegCapView>(R.id.eegCapView).setChannels(channels)

        val container = findViewById<android.widget.LinearLayout>(R.id.llChannelList)
        container.removeAllViews()

        for (i in 0 until channels.length()) {
            val ch = channels.getJSONObject(i)
            val name = ch.optString("name", "")
            val impedance = ch.optDouble("impedance", -1.0)
            val status = ch.optString("status", "Open")

            // 动态创建一行
            val row = layoutInflater.inflate(R.layout.item_channel, container, false)

            row.findViewById<android.widget.TextView>(R.id.tvChannelName).text = name
            row.findViewById<android.widget.TextView>(R.id.tvChannelStatus).text = status
            row.findViewById<android.widget.TextView>(R.id.tvChannelImpedance).text =
                if (impedance < 0) "---" else "%.1fkΩ".format(impedance)

            // 状态点颜色
            val dot = row.findViewById<android.view.View>(R.id.vStatusDot)
            when (status) {
                "OK" -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_ok)
                    row.findViewById<android.widget.TextView>(R.id.tvChannelStatus)
                        .setTextColor(0xFF333333.toInt())
                }
                "Bad" -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_bad)
                    row.findViewById<android.widget.TextView>(R.id.tvChannelStatus)
                        .setTextColor(0xFFF44336.toInt())
                }
                else -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_open)
                    row.findViewById<android.widget.TextView>(R.id.tvChannelStatus)
                        .setTextColor(0xFFFF5722.toInt())
                }
            }

            container.addView(row)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspection)


        // ===== 返回 =====
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ===== 左侧导航 =====
        val navSetup = findViewById<LinearLayout>(R.id.navSetup)
        val navInspection = findViewById<LinearLayout>(R.id.navInspection)
        val navMonitor = findViewById<LinearLayout>(R.id.navMonitor)

        val dotSetup = findViewById<View>(R.id.dotSetup)
        val dotInspection = findViewById<View>(R.id.dotInspection)
        val dotMonitor = findViewById<View>(R.id.dotMonitor)

        val tvNavSetup = findViewById<TextView>(R.id.tvNavSetup)
        val tvNavInspection = findViewById<TextView>(R.id.tvNavInspection)
        val tvNavMonitor = findViewById<TextView>(R.id.tvNavMonitor)

        fun selectNav(selectedDot: View, selectedTv: TextView) {
            dotSetup.setBackgroundResource(R.drawable.bg_dot_normal)
            dotInspection.setBackgroundResource(R.drawable.bg_dot_normal)
            dotMonitor.setBackgroundResource(R.drawable.bg_dot_normal)

            tvNavSetup.setTextColor(0xFF999999.toInt())
            tvNavInspection.setTextColor(0xFF999999.toInt())
            tvNavMonitor.setTextColor(0xFF999999.toInt())

            selectedDot.setBackgroundResource(R.drawable.bg_dot_selected)
            selectedTv.setTextColor(0xFF1BA39A.toInt())
        }

        navSetup.setOnClickListener {
            finish()
        }
        navInspection.setOnClickListener {
            // 已在检测页，不做操作
        }
        navMonitor.setOnClickListener {
            Toast.makeText(this, "监控页（待做）", Toast.LENGTH_SHORT).show()
        }

        // ===== 上一步：返回实验页 =====
        findViewById<TextView>(R.id.btnPrev).setOnClickListener { finish() }

        // ===== 下一步按钮 =====
        val btnNext = findViewById<TextView>(R.id.btnNext)

        // 刷新按钮状态的函数
        fun refreshNextButton() {
            val connected = (NetworkManager.getState() == ConnState.CONNECTED)

            // 条件 1：CS 发了 READY（或 RUNNING）
            val remoteReady = NetworkManager.getRemoteState() in listOf("READY", "RUNNING")

            // 条件 2：所有通道阻抗都 OK
            val allChannelsOk = lastChannels.isNotEmpty() && lastChannels.all { it.status == "OK" }

            // 或关系
            val enabled = connected && (remoteReady || allChannelsOk)

            btnNext.isEnabled = enabled
            btnNext.alpha = if (enabled) 1.0f else 0.5f
        }

        // ===== 绑定左下角状态栏（按钮传 null，我们手动控制）=====
        statusListener = NetworkManager.bindStatusUI(
            this,
            findViewById(R.id.tvServerAddr),
            findViewById(R.id.tvConnStatus),
            null
        )

        // 连接状态变化时也刷新按钮
        NetworkManager.addStateListener {
            runOnUiThread { refreshNextButton() }
        }

        // 电脑消息变化时刷新按钮 + 收到 RUNNING 跳转
        messageListener = { result, state ->
            runOnUiThread {
                refreshNextButton()
                // 跳转逻辑已移到 btnNext 点击里
            }
        }
        NetworkManager.addMessageListener(messageListener!!)

        // 先刷新一次
        refreshNextButton()

        // ===== 点击"下一步"：发 START =====
        btnNext.setOnClickListener {
            if (NetworkManager.getState() != ConnState.CONNECTED) {
                Toast.makeText(this, "未连接电脑，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (NetworkManager.getRemoteState() !in listOf("READY", "RUNNING")) {
                Toast.makeText(this, "系统未就绪，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 只跳转，START 交给监控页发
            startActivity(Intent(this@InspectionActivity, MonitorActivity::class.java))
        }

        // ===== 监听阻抗更新 =====
        rawMessageListener = { json ->
            runOnUiThread {
                val type = json.optString("type", "")
                if (type == "impedance_update") {
                    val arr = json.optJSONArray("channels")
                    if (arr != null) {
                        renderChannels(arr)

                        // 更新 lastChannels（用于按钮判断）
                        val list = mutableListOf<ChannelInfo>()
                        for (i in 0 until arr.length()) {
                            val ch = arr.getJSONObject(i)
                            list.add(ChannelInfo(
                                ch.optString("name"),
                                ch.optDouble("impedance", -1.0),
                                ch.optString("status", "Open")
                            ))
                        }
                        lastChannels = list
                        refreshNextButton()   // 有新通道数据，刷新按钮
                    }
                }
            }
        }
        NetworkManager.addRawMessageListener(rawMessageListener!!)
//        //加的测试
//        android.widget.Toast.makeText(
//            this,
//            "③ 监听器已注册",
//            android.widget.Toast.LENGTH_LONG
//        ).show()

        // ===== 页面创建后立即发 PREPARE（带被试信息）=====
        val subject = DataStore.getCurrentSubject(this)
        val extra = mutableMapOf<String, Any>()
        if (subject != null) {
            extra["subjectId"] = subject.subjectId
            extra["paradigmId"] = subject.paradigm
        }
        NetworkManager.sendControl("PREPARE", extra)
    }

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.let { NetworkManager.removeStateListener(it) }
        messageListener?.let { NetworkManager.removeMessageListener(it) }
        rawMessageListener?.let { NetworkManager.removeRawMessageListener(it) }
    }
}