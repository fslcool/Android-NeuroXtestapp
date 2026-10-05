package com.fsl.neuroxapptest

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // ===== 返回按钮 =====
        findViewById<ImageView>(R.id.btnHome).setOnClickListener { finish() }

        // ===== 菜单项 =====
        val menuGeneral = findViewById<TextView>(R.id.menuGeneral)
        val menuNetwork = findViewById<TextView>(R.id.menuNetwork)
        val menuDevice = findViewById<TextView>(R.id.menuDevice)
        val menuAccount = findViewById<TextView>(R.id.menuAccount)

        // ===== 4 个内容面板 =====
        val panelGeneral = findViewById<LinearLayout>(R.id.panelGeneral)
        val panelNetwork = findViewById<LinearLayout>(R.id.panelNetwork)
        val panelDevice = findViewById<LinearLayout>(R.id.panelDevice)
        val panelAccount = findViewById<LinearLayout>(R.id.panelAccount)

        // 切换：只显示选中的面板，其它隐藏
        fun showPanel(panel: View, selectedMenu: TextView) {
            // 隐藏所有面板
            panelGeneral.visibility = View.GONE
            panelNetwork.visibility = View.GONE
            panelDevice.visibility = View.GONE
            panelAccount.visibility = View.GONE
            // 显示选中的
            panel.visibility = View.VISIBLE

            // 左侧菜单高亮切换
            val menus = listOf(menuGeneral, menuNetwork, menuDevice, menuAccount)
            for (m in menus) {
                if (m === selectedMenu) {
                    m.setBackgroundResource(R.drawable.bg_menu_selected)
                    m.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    m.background = null
                    m.setTextColor(0xFF666666.toInt())
                }
            }
        }

        menuGeneral.setOnClickListener { showPanel(panelGeneral, menuGeneral) }
        menuNetwork.setOnClickListener { showPanel(panelNetwork, menuNetwork) }
        menuDevice.setOnClickListener  { showPanel(panelDevice,  menuDevice) }
        menuAccount.setOnClickListener { showPanel(panelAccount, menuAccount) }

        // ===== 通用面板里的保存按钮 =====
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val autoSync = findViewById<Switch>(R.id.swAutoSync).isChecked
            val cloudUpload = findViewById<Switch>(R.id.swCloudUpload).isChecked
            val labName = findViewById<EditText>(R.id.etLabName).text.toString().trim()
            val deviceId = findViewById<EditText>(R.id.etDeviceId).text.toString().trim()

            val msg = "已保存\n自动同步=$autoSync\n上传云端=$cloudUpload\n实验室=$labName\n设备=$deviceId"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // ===== 设备面板里的扫描按钮 =====
        findViewById<Button>(R.id.btnScanDevice).setOnClickListener {
            Toast.makeText(this, "扫描设备（待实现）", Toast.LENGTH_SHORT).show()
        }

        // ===== 账户面板里的退出按钮 =====
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            Toast.makeText(this, "退出登录（待实现）", Toast.LENGTH_SHORT).show()
        }

        // ===== 账户面板：更换账号 =====
        findViewById<Button>(R.id.btnChangeAccount).setOnClickListener {
            Toast.makeText(this, "更换账号（待实现）", Toast.LENGTH_SHORT).show()
        }

        // ===== 账户面板：编辑头像 =====
        findViewById<ImageView>(R.id.ivEditAvatar).setOnClickListener {
            Toast.makeText(this, "编辑头像（待实现）", Toast.LENGTH_SHORT).show()
        }

        // ===== 账户面板：编辑用户名 =====
        findViewById<ImageView>(R.id.ivEditName).setOnClickListener {
            Toast.makeText(this, "编辑用户名（待实现）", Toast.LENGTH_SHORT).show()
        }

        // ===== 网络面板：加载已保存的地址 + 保存按钮 =====
        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        etServerUrl.setText(NetworkManager.getServerAddress())

        findViewById<Button>(R.id.btnSaveNetwork).setOnClickListener {
            val addr = etServerUrl.text.toString().trim()
            if (addr.isEmpty()) {
                Toast.makeText(this, "请填写服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            NetworkManager.saveServerAddressAndReconnect(this, addr)
            Toast.makeText(this, "已保存：$addr\n正在使用新地址重连...", Toast.LENGTH_LONG).show()
        }
    }
}