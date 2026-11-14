package com.alex.studydemo.arouter

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.content.Context
import android.content.Intent
import android.view.View
import com.alibaba.android.arouter.launcher.ARouter

/** 通过真实 ARouter 路由传递输入参数，保留旧 XML 点击方法供外部调用。 */
class ArouterMainActivity : ComponentActivity() {
    private var parameter by mutableStateOf("Compose 参数")
    private var error by mutableStateOf("")
    companion object {
        /** 沿用原来的任务栈标志，支持 Application Context。 */
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, ArouterMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
    /** 输入值作为 test 参数发送，返回本页仍保留当前输入。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("ARouter 参数传递", onBack = { finish() }) {
                    OutlinedTextField(parameter, { parameter = it }, label = { Text("test 参数") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { navigateSecond() }) { Text("通过 ARouter 打开第二页") }
                    Text(error)
                }
            }
        }
    }
    /** 保留历史公开点击签名；View 不参与路由参数构造。 */
    fun secondActivity(view: View) { navigateSecond() }

    /** 保留实际路由链路；初始化或路由异常显示在当前页面以便重试。 */
    private fun navigateSecond() {
        try { ARouter.getInstance().build(ArouterSecondActivity.PATH).withString("test", parameter).navigation(this) }
        catch (e: Exception) { error = "路由失败：${e.message}" }
    }

}
