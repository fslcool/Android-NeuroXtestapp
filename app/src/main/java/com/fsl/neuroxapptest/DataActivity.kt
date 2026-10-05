package com.fsl.neuroxapptest

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DataActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        // 返回
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ===== 读取记录，动态渲染 =====
        renderRecords()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到这个页面都刷新（比如上传成功回来）
        renderRecords()
    }

    /**
     * 读 DataStore 里的所有记录，动态生成每一行。
     */
    private fun renderRecords() {
        val container = findViewById<LinearLayout>(R.id.llRecordList)
        container.removeAllViews()

        val records = DataStore.getAllRecords(this)

        if (records.isEmpty()) {
            // 没有记录时，显示一条提示
            val tv = TextView(this).apply {
                text = "暂无数据"
                textSize = 14f
                setTextColor(0xFF999999.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 80, 0, 0)
            }
            container.addView(tv)
            return
        }

        // 遍历所有记录，生成每一行
        for ((index, record) in records.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_record, container, false)

            // 填充文字
            row.findViewById<TextView>(R.id.tvName).text = record.name
            row.findViewById<TextView>(R.id.tvId).text = record.id
            row.findViewById<TextView>(R.id.tvDate).text = record.dateTime
            row.findViewById<TextView>(R.id.tvParadigm).text =
                ParadigmMapping.getName(record.paradigm)

            // 结果：正常/需关注
            val tvResult = row.findViewById<TextView>(R.id.tvResult)
            val llResultBg = row.findViewById<LinearLayout>(R.id.llResultBg)
            tvResult.text = record.result
            if (record.result == "正常") {
                llResultBg.setBackgroundResource(R.drawable.bg_result_green)
                tvResult.setTextColor(0xFF00796B.toInt())
            } else {
                llResultBg.setBackgroundResource(R.drawable.bg_result_red)
                tvResult.setTextColor(0xFFC62828.toInt())
            }

            // 点击"查看报告"
            row.findViewById<TextView>(R.id.btnReport).setOnClickListener {
                val intent = Intent(this, ReportActivity::class.java)
                intent.putExtra("recordIndex", index)
                startActivity(intent)
            }

            container.addView(row)

            // 分隔线（除了最后一行）
            if (index < records.size - 1) {
                val divider = android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(0xFFF0F0F0.toInt())
                }
                container.addView(divider)
            }
        }
    }
}