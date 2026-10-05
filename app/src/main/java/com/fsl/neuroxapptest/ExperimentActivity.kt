package com.fsl.neuroxapptest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ExperimentActivity : AppCompatActivity() {

    // 范式数据类
    data class Paradigm(val id: String, val name: String, val duration: String)

    // 所有范式（按顺序）
    private val allParadigms = listOf(
        Paradigm("1", "问卷", "5min"),
        Paradigm("2", "IAT", "10min"),
        Paradigm("3", "快乐情绪诱发", "5min"),
        Paradigm("4", "悲伤情绪诱发", "5min"),
        Paradigm("5", "愤怒情绪诱发", "5min"),
        Paradigm("6", "恐惧情绪诱发", "5min"),
        Paradigm("7", "惊讶情绪诱发", "5min"),
        Paradigm("8", "厌恶情绪诱发", "5min"),
        Paradigm("9", "焦虑情绪诱发", "5min"),
        Paradigm("10", "嫉妒情绪诱发", "5min"),
        Paradigm("11", "羞愧情绪诱发", "5min")
    )

    // 情绪标签下的范式（从"快乐情绪诱发"开始）
    private val emotionParadigms = allParadigms.drop(2)   // 从索引 2 开始

    // 当前选中的范式名字
    private var selectedParadigmId: String = ""

    // 当前标签（"all" / "resting" / "emotion" / "cognition"）
    private var currentTag: String = "all"

    // 当前卡片列表（用于点击时刷新背景）
    private val currentCardViews = mutableListOf<View>()

    // 扫码结果回调
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val etSubjectId = findViewById<android.widget.EditText>(R.id.etSubjectId)
            etSubjectId.setText(result.contents)
            Toast.makeText(this, "扫码成功：${result.contents}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "已取消扫码", Toast.LENGTH_SHORT).show()
        }
    }

    private var statusListener: ((ConnState) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experiment)

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

        navSetup.setOnClickListener { selectNav(dotSetup, tvNavSetup) }
        navInspection.setOnClickListener { selectNav(dotInspection, tvNavInspection) }
        navMonitor.setOnClickListener { selectNav(dotMonitor, tvNavMonitor) }

        // ===== 被试信息：修改 / 确认 =====
        findViewById<TextView>(R.id.btnEdit).setOnClickListener {
            Toast.makeText(this, "修改（待实现）", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            if (saveSubjectInfo()) {
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
        }

        // ===== 扫码按钮 =====
        findViewById<TextView>(R.id.btnScan).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                startScan()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA
                )
            }
        }

        // ===== 顶部标签切换 =====
        val tagAll = findViewById<TextView>(R.id.tagAll)
        val tagResting = findViewById<TextView>(R.id.tagResting)
        val tagEmotion = findViewById<TextView>(R.id.tagEmotion)
        val tagCognition = findViewById<TextView>(R.id.tagCognition)
        val tags = listOf(tagAll, tagResting, tagEmotion, tagCognition)

        fun selectTag(selected: TextView, tagKey: String) {
            for (t in tags) {
                if (t === selected) {
                    t.setBackgroundResource(R.drawable.bg_tag_selected)
                    t.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    t.setBackgroundResource(R.drawable.bg_tag_normal)
                    t.setTextColor(0xFF666666.toInt())
                }
            }
            currentTag = tagKey
            refreshParadigmArea()
        }

        tagAll.setOnClickListener { selectTag(tagAll, "all") }
        tagResting.setOnClickListener { selectTag(tagResting, "resting") }
        tagEmotion.setOnClickListener { selectTag(tagEmotion, "emotion") }
        tagCognition.setOnClickListener { selectTag(tagCognition, "cognition") }

        // ===== 初始化范式区（默认"全部"）=====
        refreshParadigmArea()

        // ===== 下一步按钮 =====
        val btnNext = findViewById<TextView>(R.id.btnNext)
        btnNext.setOnClickListener {
            if (NetworkManager.getState() != ConnState.CONNECTED) {
                Toast.makeText(this, "未连接电脑，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!saveSubjectInfo()) {
                return@setOnClickListener
            }
            startActivity(Intent(this@ExperimentActivity, InspectionActivity::class.java))
        }

        // ===== 绑定连接状态 UI =====
        statusListener = NetworkManager.bindStatusUI(
            this,
            findViewById(R.id.tvServerAddr),
            findViewById(R.id.tvConnStatus),
            btnNext
        )
    }

    // ============================================================
    // 刷新范式区（根据当前标签）
    // ============================================================
    private fun refreshParadigmArea() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPagerParadigm)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyHint)
        val llDots = findViewById<LinearLayout>(R.id.llDots)
        val dotPage1 = findViewById<View>(R.id.dotPage1)
        val dotPage2 = findViewById<View>(R.id.dotPage2)

        // 重置卡片列表
        currentCardViews.clear()

        when (currentTag) {
            "all" -> {
                // 显示 ViewPager2，隐藏"暂无"
                viewPager.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE
                llDots.visibility = View.VISIBLE

                // 分 2 页：第 1 页 9 个，第 2 页 2 个
                val pages = listOf(
                    allParadigms.take(9),
                    allParadigms.drop(9)
                )
                viewPager.adapter = ParadigmPagerAdapter(pages)
                viewPager.setCurrentItem(0, false)

                // 监听滑动，更新圆点
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        if (position == 0) {
                            dotPage1.setBackgroundResource(R.drawable.bg_dot_selected)
                            dotPage2.setBackgroundResource(R.drawable.bg_dot_normal)
                            // 让第 1 个点变长
                            dotPage1.layoutParams = dotPage1.layoutParams.apply { width = dpToPx(20) }
                            dotPage2.layoutParams = dotPage2.layoutParams.apply { width = dpToPx(8) }
                        } else {
                            dotPage1.setBackgroundResource(R.drawable.bg_dot_normal)
                            dotPage2.setBackgroundResource(R.drawable.bg_dot_selected)
                            dotPage1.layoutParams = dotPage1.layoutParams.apply { width = dpToPx(8) }
                            dotPage2.layoutParams = dotPage2.layoutParams.apply { width = dpToPx(20) }
                        }
                        dotPage1.requestLayout()
                        dotPage2.requestLayout()
                    }
                })

                // 手动设一次初始
                dotPage1.setBackgroundResource(R.drawable.bg_dot_selected)
                dotPage2.setBackgroundResource(R.drawable.bg_dot_normal)
            }

            "emotion" -> {
                viewPager.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE
                llDots.visibility = View.VISIBLE

                // 只有 1 页
                val pages = listOf(emotionParadigms)
                viewPager.adapter = ParadigmPagerAdapter(pages)
                viewPager.setCurrentItem(0, false)

                // 圆点：只亮第 1 个，第 2 个隐藏
                dotPage1.setBackgroundResource(R.drawable.bg_dot_selected)
                dotPage2.visibility = View.GONE
                dotPage1.layoutParams = dotPage1.layoutParams.apply { width = dpToPx(20) }
                dotPage1.requestLayout()
            }

            "resting", "cognition" -> {
                // 隐藏 ViewPager2，显示"暂无"
                viewPager.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                llDots.visibility = View.GONE
            }
        }
    }

    // ============================================================
    // ViewPager2 适配器
    // ============================================================
    inner class ParadigmPagerAdapter(
        private val pages: List<List<Paradigm>>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ParadigmPagerAdapter.PageHolder>() {

        inner class PageHolder(val grid: androidx.gridlayout.widget.GridLayout) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(grid)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PageHolder {
            val grid = androidx.gridlayout.widget.GridLayout(parent.context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                columnCount = 3
                rowCount = 3
            }
            return PageHolder(grid)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val grid = holder.grid
            grid.removeAllViews()

            val pageData = pages[position]
            val inflater = LayoutInflater.from(this@ExperimentActivity)

            // 1. 加卡片
            for ((index, paradigm) in pageData.withIndex()) {
                val card = inflater.inflate(R.layout.item_paradigm, grid, false)

                val col = index % 3
                val row = index / 3

                val params = androidx.gridlayout.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = androidx.gridlayout.widget.GridLayout.spec(
                        col, 1,
                        androidx.gridlayout.widget.GridLayout.FILL, 1f
                    )
                    rowSpec = androidx.gridlayout.widget.GridLayout.spec(
                        row, 1,
                        androidx.gridlayout.widget.GridLayout.FILL, 1f
                    )
                    setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                }
                card.layoutParams = params

                card.findViewById<TextView>(R.id.tvParadigmName).text = paradigm.name
                card.findViewById<TextView>(R.id.tvParadigmDesc).text = "时长: ${paradigm.duration}"

                card.setOnClickListener {
                    selectCard(card, paradigm.id)
                }

                currentCardViews.add(card)
                grid.addView(card)
            }

            // 2. 加空占位，填满 3×3（让第 1 行高度 = 1/3 View）
            val totalCells = 9
            for (i in pageData.size until totalCells) {
                val col = i % 3
                val row = i / 3
                val placeholder = android.view.View(this@ExperimentActivity)
                val params = androidx.gridlayout.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = androidx.gridlayout.widget.GridLayout.spec(
                        col, 1,
                        androidx.gridlayout.widget.GridLayout.FILL, 1f
                    )
                    rowSpec = androidx.gridlayout.widget.GridLayout.spec(
                        row, 1,
                        androidx.gridlayout.widget.GridLayout.FILL, 1f
                    )
                    setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                }
                placeholder.layoutParams = params
                grid.addView(placeholder)
            }
        }

        override fun getItemCount() = pages.size
    }

    // ============================================================
    // 卡片选中
    // ============================================================
    private fun selectCard(selected: View, paradigmId: String) {
        for (v in currentCardViews) {
            v.setBackgroundResource(R.drawable.bg_card_paradigm)
        }
        selected.setBackgroundResource(R.drawable.bg_card_paradigm_selected)
        selectedParadigmId = paradigmId
    }

    // ============================================================
    // 保存被试信息
    // ============================================================
    private fun saveSubjectInfo(): Boolean {
        val name = findViewById<android.widget.EditText>(R.id.etName).text.toString().trim()
        val id = findViewById<android.widget.EditText>(R.id.etId).text.toString().trim()
        val age = findViewById<android.widget.EditText>(R.id.etAge).text.toString().trim()
        val subjectId = findViewById<android.widget.EditText>(R.id.etSubjectId).text.toString().trim()

        val gender = when (findViewById<android.widget.RadioGroup>(R.id.rgGender).checkedRadioButtonId) {
            R.id.rbMale -> "男"
            R.id.rbFemale -> "女"
            else -> ""
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "请填写姓名", Toast.LENGTH_SHORT).show()
            return false
        }
        if (id.isEmpty()) {
            Toast.makeText(this, "请填写学号", Toast.LENGTH_SHORT).show()
            return false
        }
        if (age.isEmpty()) {
            Toast.makeText(this, "请填写年龄", Toast.LENGTH_SHORT).show()
            return false
        }
        if (gender.isEmpty()) {
            Toast.makeText(this, "请选择性别", Toast.LENGTH_SHORT).show()
            return false
        }
        if (subjectId.isEmpty()) {
            Toast.makeText(this, "请填写被试编号", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedParadigmId.isEmpty()) {
            Toast.makeText(this, "请选择范式", Toast.LENGTH_SHORT).show()
            return false
        }

        DataStore.saveCurrentSubject(
            this,
            DataStore.SubjectInfo(name, id, age, gender, subjectId, selectedParadigmId)
        )
        return true
    }

    // ============================================================
    // 工具
    // ============================================================
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.let { NetworkManager.removeStateListener(it) }
    }

    private fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("请对准二维码")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan()
            } else {
                Toast.makeText(this, "没有摄像头权限，无法扫码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA = 1001
    }
}