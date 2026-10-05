package com.fsl.neuroxapptest

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etAccount = findViewById<EditText>(R.id.etAccount)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val cbAgree = findViewById<CheckBox>(R.id.cbAgree)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvBack = findViewById<TextView>(R.id.tvBack)

        // 点击"立即注册"
        btnRegister.setOnClickListener {
            val account = etAccount.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            if (account.isEmpty()) {
                Toast.makeText(this, "请输入邮箱或手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "请设置密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (confirm.isEmpty()) {
                Toast.makeText(this, "请确认密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirm) {
                Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!cbAgree.isChecked) {
                Toast.makeText(this, "请先同意条款", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 暂时弹提示，等接后端再改成真注册
            Toast.makeText(this, "注册中：$account", Toast.LENGTH_SHORT).show()
        }

        // 点击"返回"
        tvBack.setOnClickListener {
            finish()   // 关闭当前页，回到登录页
        }
    }
}