package com.fsl.neuroxapptest

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        // 返回
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        // ===== 读记录 =====
        val index = intent.getIntExtra("recordIndex", -1)
        val record = if (index >= 0) DataStore.getRecordAt(this, index) else null

        if (record == null) {
            // 没数据，显示默认（或提示）
            // 简单起见，直接 finish
            finish()
            return
        }

        // ===== 填充数据 =====

        // 日期（左上角）
        findViewById<TextView>(R.id.tvDate).text = record.dateTime

        // 范式（左上角胶囊）
        findViewById<TextView>(R.id.tvParadigm).text =
            ParadigmMapping.getName(record.paradigm)

        // 左侧被试信息
        findViewById<TextView>(R.id.tvName).text = record.name
        findViewById<TextView>(R.id.tvId).text = record.id
        findViewById<TextView>(R.id.tvAge).text = record.age
        findViewById<TextView>(R.id.tvGender).text = record.gender

        // 底部"结果"标签（正常/需关注）
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val llResultBg = findViewById<LinearLayout>(R.id.llResultBg)
        tvResult.text = record.result
        if (record.result == "正常") {
            llResultBg.setBackgroundResource(R.drawable.bg_result_green)
            tvResult.setTextColor(0xFF00796B.toInt())
        } else {
            llResultBg.setBackgroundResource(R.drawable.bg_result_red)
            tvResult.setTextColor(0xFFC62828.toInt())
        }
    }
}