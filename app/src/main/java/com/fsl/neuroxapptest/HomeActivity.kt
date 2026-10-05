package com.fsl.neuroxapptest

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 4 个卡片的点击事件（暂时弹 Toast，等有内容页再接）
        findViewById<LinearLayout>(R.id.cardExperiment).setOnClickListener {
            startActivity(android.content.Intent(this, ExperimentActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.cardData).setOnClickListener {
            startActivity(android.content.Intent(this, DataActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.cardMusic).setOnClickListener {
            startActivity(android.content.Intent(this, MusicActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.cardSetting).setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.cardData).setOnLongClickListener {
            startActivity(android.content.Intent(this, TestActivity::class.java))
            true
        }
    }
}