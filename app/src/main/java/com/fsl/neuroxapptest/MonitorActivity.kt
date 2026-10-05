package com.fsl.neuroxapptest

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class MonitorActivity : AppCompatActivity() {

    companion object {
        var instance: MonitorActivity? = null
    }

    // 类的成员变量
    private var statusListener: ((ConnState) -> Unit)? = null
    private var messageListener: ((String, String) -> Unit)? = null
    private var currentAction = "START"
    private lateinit var btnAction: TextView
    private lateinit var btnBackHome: android.widget.ImageView
    private var lastState: String = ""
    private var videoConnected = false


    // REC 计时相关
    private var recHandler: android.os.Handler? = null
    private var recSeconds: Int = 0
    private var recRunning: Boolean = false
    private lateinit var tvRecTime: android.widget.TextView
    private var recPaused = false

    // 血氧 / 心率 随机变化
    private var vitalHandler: android.os.Handler? = null
    private var spo2Value: Int = 98       // 当前血氧值
    private var hrValue: Int = 72         // 当前心率值
    private lateinit var tvSpo2: android.widget.TextView
    private lateinit var tvHr: android.widget.TextView

    private lateinit var btnPauseResume: android.widget.TextView
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)

        instance = this

        // ===== 返回 =====
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ===== 底部 5 个按钮 =====
        findViewById<TextView>(R.id.btnMarkArtifact).setOnClickListener {
            Toast.makeText(this, "标记伪迹", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnMarkEvent).setOnClickListener {
            Toast.makeText(this, "标记事件", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnMarkCustom).setOnClickListener {
            Toast.makeText(this, "自定义标记", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnRestart).setOnClickListener {
            if (NetworkManager.getState() != ConnState.CONNECTED) {
                Toast.makeText(this, "未连接电脑，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            finish()
        }

        // ===== 动态按钮：终止实验 / 上传云端 =====
        btnAction = findViewById<TextView>(R.id.btnAction)

        // ===== 暂停 / 继续 按钮（初始隐藏） =====
        btnPauseResume = findViewById(R.id.btnPauseResume)
        btnPauseResume.visibility = android.view.View.GONE
        btnPauseResume.setOnClickListener {
            if (isPaused) {
                // 点"继续"
                NetworkManager.sendControl("RESUME")
                btnPauseResume.text = "暂停"
                isPaused = false
                recPaused = false       // ⭐ REC 继续
            } else {
                // 点"暂停"
                NetworkManager.sendControl("PAUSE")
                btnPauseResume.text = "继续"
                isPaused = true
                recPaused = true        // ⭐ REC 暂停
            }
        }

        tvRecTime = findViewById(R.id.tvRecTime)
        // 初始化血氧 / 心率显示
        tvSpo2 = findViewById(R.id.tvSpo2)
        tvHr = findViewById(R.id.tvHr)
        tvSpo2.text = "$spo2Value%"
        tvHr.text = "$hrValue"

        // 启动随机变化
        startVitalTimer()
        tvRecTime.text = "REC 00:00:00"

        // ===== 返回主页按钮（初始隐藏） =====
        btnBackHome = findViewById(R.id.btnBackHome)
        btnBackHome.visibility = android.view.View.GONE
        btnBackHome.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // ============================================================
        // 根据 CS 状态更新按钮
        // ============================================================
        fun updateActionButton(state: String) {
            when (state) {
                "ENDED" -> {
                    btnAction.text = "上传云端"
                    btnAction.setBackgroundResource(R.drawable.bg_btn_filled)
                    btnAction.isEnabled = true
                    btnAction.alpha = 1.0f
                    currentAction = "UPLOAD"

                    btnBackHome.visibility = android.view.View.GONE
                    btnPauseResume.visibility = android.view.View.GONE
                }
                "TERMINATED" -> {
                    btnAction.text = "已终止"
                    btnAction.setBackgroundResource(R.drawable.bg_btn_end)
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.6f
                    currentAction = "NONE"

                    btnBackHome.visibility = android.view.View.VISIBLE
                    btnPauseResume.visibility = android.view.View.GONE
                }
                "RUNNING" -> {
                    // 已经在跑 → 显示"终止实验"
                    btnAction.text = "终止实验 →"
                    btnAction.setBackgroundResource(R.drawable.bg_btn_end)
                    btnAction.isEnabled = true
                    btnAction.alpha = 1.0f
                    currentAction = "TERMINATE"

                    btnBackHome.visibility = android.view.View.GONE
                    // ⭐ 显示"暂停"按钮，文字恢复"暂停"
                    btnPauseResume.visibility = android.view.View.VISIBLE
                    btnPauseResume.text = "暂停"
                    isPaused = false
                    recPaused = false
                }
                "FAULT" -> {
                    btnAction.text = "故障"
                    btnAction.setBackgroundResource(R.drawable.bg_btn_end)
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.6f
                    currentAction = "NONE"

                    btnBackHome.visibility = android.view.View.GONE
                    btnPauseResume.visibility = android.view.View.GONE
                }
                else -> {
                    // 初始 / 未开始 → 显示"开始实验"
                    btnAction.text = "开始实验"
                    btnAction.setBackgroundResource(R.drawable.bg_btn_filled)
                    btnAction.isEnabled = true
                    btnAction.alpha = 1.0f
                    currentAction = "START"

                    btnBackHome.visibility = android.view.View.GONE
                    btnPauseResume.visibility = android.view.View.GONE
                }
            }
        }

        // 初始状态
        updateActionButton(NetworkManager.getRemoteState())

        // ============================================================
        // 监听电脑状态变化
        // ============================================================
        messageListener = { result, state ->
            runOnUiThread {
                when (state) {

                    "RUNNING" -> {
                        val waveView = findViewById<EegWaveView>(R.id.eegWaveView)
                        // 如果是"实验刚开始"（之前没 START 过），先清空旧标记
                        // 简单做法：每次收到 RUNNING 都清空（因为一轮实验只有一个 RUNNING）
                        // 但这样会清掉"之前 END 的标记"—— 通常这是对的
                        // waveView?.clearMarkers()  // ← 视需求决定
                        if (lastState == "PAUSED") {
                            waveView?.addMarker("RESUME")     // ⭐ 从暂停恢复
                            recPaused = false
                        } else {
                            waveView?.addMarker("START")      // ⭐ 首次开始
                            startRecTimer()
                        }
                        updateActionButton("RUNNING")
                        lastState = state
                        return@runOnUiThread
                    }

                    "PAUSED" -> {
                        // CS 确认已暂停 → 按钮显示"继续"
                        btnPauseResume.visibility = android.view.View.VISIBLE
                        btnPauseResume.text = "继续"
                        isPaused = true
                        recPaused = true
                        findViewById<EegWaveView>(R.id.eegWaveView)?.addMarker("PAUSE")   // ⭐ 加"实验暂停"标记
                        lastState = state
                        return@runOnUiThread
                    }

                    // -------- 上传相关 --------
                    "UPLOADING" -> {
                        btnAction.text = "上传中..."
                        btnAction.isEnabled = false
                        btnAction.alpha = 0.6f
                        lastState = state
                        return@runOnUiThread
                    }

                    "UPLOADED" -> {
                        btnAction.text = "上传成功 ✅"
                        btnAction.isEnabled = false
                        btnAction.alpha = 1.0f

                        Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show()

                        // ⭐ 保存实验记录
                        saveExperimentRecord()

                        // 延迟 1.5 秒回主页
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val intent = Intent(this, HomeActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }, 1500)
                        lastState = state
                        return@runOnUiThread
                    }

                    "UPLOAD_FAILED", "ERROR" -> {
                        btnAction.text = "上传云端"
                        btnAction.isEnabled = true
                        btnAction.alpha = 1.0f

                        Toast.makeText(this, "上传失败，请重试", Toast.LENGTH_SHORT).show()
                        lastState = state
                        return@runOnUiThread
                    }

                    // -------- 实验结束（正常）--------
                    "ENDED" -> {
                        stopRecTimer()
                        updateActionButton(state)
                        findViewById<EegWaveView>(R.id.eegWaveView)?.addMarker("END")
                        Toast.makeText(this, "实验已完成，可以上传云端", Toast.LENGTH_LONG).show()
                        lastState = state
                        return@runOnUiThread
                    }

                    // -------- 实验终止（异常）--------
                    "TERMINATED" -> {
                        stopRecTimer()
                        updateActionButton(state)
                        // ⭐ 加事件标记
                        findViewById<EegWaveView>(R.id.eegWaveView)?.addMarker("TERMINATE")
                        Toast.makeText(this, "实验已终止", Toast.LENGTH_SHORT).show()
                        lastState = state
                        return@runOnUiThread
                    }
                    "FAULT" -> {
                        stopRecTimer()
                        updateActionButton("FAULT")
                        Toast.makeText(this, "采集故障，请退出重试或联系管理员", Toast.LENGTH_LONG).show()
                        lastState = state
                        return@runOnUiThread
                    }

                }

                // 其他状态走默认逻辑（RUNNING / PAUSED 等）
                updateActionButton(state)
                lastState = state
            }
        }
        NetworkManager.addMessageListener(messageListener!!)

        // ============================================================
        // 点击按钮
        // ============================================================
        btnAction.setOnClickListener {
            if (NetworkManager.getState() != ConnState.CONNECTED) {
                Toast.makeText(this, "未连接电脑，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (currentAction) {

                "START" -> {
                    // 发 START
                    NetworkManager.sendControl("START")

                    // 立即变"启动中..."（灰色，禁用）
                    btnAction.text = "启动中..."
                    btnAction.setBackgroundResource(R.drawable.bg_btn_filled)
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.6f
                    currentAction = "STARTING"

                    // 10 秒超时：如果还没收到 RUNNING，恢复"开始实验"
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (currentAction == "STARTING") {
                            updateActionButton("READY")   // 恢复到"开始实验"
                            Toast.makeText(this, "启动超时，请重试", Toast.LENGTH_SHORT).show()
                        }
                    }, 10000)
                }

                "STARTING" -> {
                    // 已点过，等待 CS 回 RUNNING，无操作
                }

                "TERMINATE" -> {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("终止实验")
                        .setMessage("确定要终止本次实验吗？\n未保存的数据将会丢失。")
                        .setPositiveButton("确定") { _, _ ->
                            NetworkManager.sendControl("TERMINATE")
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                "UPLOAD" -> {
                    NetworkManager.sendControl("UPLOAD_CLOUD")

                    btnAction.text = "上传中..."
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.6f
                }
                "NONE" -> {
                    // 按钮已禁用，理论上到不了这里
                }
            }
        }

        // ============================================================
        // 绑定左下角状态栏
        // ============================================================
        statusListener = NetworkManager.bindStatusUI(
            this,
            findViewById(R.id.tvServerAddr),
            findViewById(R.id.tvConnStatus),
            null    // 不绑按钮
        )

        // ===== 连接视频流 =====
        startVideoStream()

        // 启动脑电波模拟
        val waveView = findViewById<EegWaveView>(R.id.eegWaveView)
        waveView.clearMarkers()   // ⭐ 清空旧标记
        waveView.start()
    }

    /**
     * 开始 REC 计时。
     */
    private fun startRecTimer() {
        if (recRunning) return

        // 重置
        recSeconds = 0
        updateRecDisplay()

        recRunning = true
        recHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val tick = object : Runnable {
            override fun run() {
                if (!recRunning) return
                if (!recPaused) {       // ⭐ 暂停时不 +1
                    recSeconds++
                    updateRecDisplay()
                }
                recHandler?.postDelayed(this, 1000)
            }
        }
        recHandler?.post(tick)
    }

    /**
     * 停止 REC 计时（保持最后时间）。
     */
    private fun stopRecTimer() {
        recRunning = false
        recHandler?.removeCallbacksAndMessages(null)
        recHandler = null
    }

    /**
     * 更新显示 "REC HH:MM:SS"。
     */
    private fun updateRecDisplay() {
        val h = recSeconds / 3600
        val m = (recSeconds % 3600) / 60
        val s = recSeconds % 60
        tvRecTime.text = "REC %02d:%02d:%02d".format(h, m, s)
    }

    /**
     * 启动血氧 / 心率的随机变化。
     * 每隔 1~3 秒（随机）变一次。
     */
    private fun startVitalTimer() {
        vitalHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val tick = object : Runnable {
            override fun run() {
                // 血氧：在 95~100 之间随机游走
                val spo2Delta = (-1..1).random()
                spo2Value = (spo2Value + spo2Delta).coerceIn(95, 100)
                tvSpo2.text = "$spo2Value%"

                // 心率：在 60~100 之间随机游走
                val hrDelta = (-2..2).random()
                hrValue = (hrValue + hrDelta).coerceIn(60, 100)
                tvHr.text = "$hrValue"

                // 随机 1~3 秒后再次变化
                val delay = (1000L..3000L).random()
                vitalHandler?.postDelayed(this, delay)
            }
        }

        // 首次 1 秒后开始
        vitalHandler?.postDelayed(tick, 1000)
    }

    /**
     * 停止血氧 / 心率变化。
     */
    private fun stopVitalTimer() {
        vitalHandler?.removeCallbacksAndMessages(null)
        vitalHandler = null
    }

    /**
     * 保存一条实验记录。
     * 日期时间 = 当前时刻，结果 = 随机（正常 80% + 需关注 20%）。
     */
    private fun saveExperimentRecord() {
        // 1. 生成日期时间
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val dateTime = now.format(java.util.Date())

        // 2. 生成结果（正常 80% + 需关注 20%）
        val result = if ((1..100).random() <= 80) "正常" else "需关注"

        // 3. 保存
        DataStore.addRecord(this, dateTime, result)
    }

    /**
     * 连接 CS 的视频流（8082），显示到 ivPic（屏幕）和 ivEx（摄像头）。
     */
    private fun startVideoStream() {
        val ivPic = findViewById<android.widget.ImageView>(R.id.ivPic)   // 刺激屏幕画面
        val ivEx = findViewById<android.widget.ImageView>(R.id.ivEx)     // 被试摄像头

        // 用 CS 的 IP + 视频端口
        val host = NetworkManager.getServerHost()   // 从 NetworkManager 拿 IP
        val port = 8082

        VideoClient.onConnected = {
            android.util.Log.d("CT-Video", "视频已连接")
        }

        VideoClient.onDisconnected = {
            android.util.Log.d("CT-Video", "视频断开")
        }

        VideoClient.onFrame = { type, bitmap ->
            when (type) {
                VideoFrameProtocol.STREAM_CAMERA -> ivEx.setImageBitmap(bitmap)
                VideoFrameProtocol.STREAM_SCREEN -> ivPic.setImageBitmap(bitmap)
            }
        }

        VideoClient.connect(host, port)
    }

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.let { NetworkManager.removeStateListener(it) }
        messageListener?.let { NetworkManager.removeMessageListener(it) }
        findViewById<EegWaveView>(R.id.eegWaveView)?.stop()
        stopRecTimer()
        stopVitalTimer()
        VideoClient.close()
        VideoClient.clearCallbacks()
        instance = null
    }
}