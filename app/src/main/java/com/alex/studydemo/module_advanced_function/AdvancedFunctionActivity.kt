package com.alex.studydemo.module_advanced_function

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*

/** 执行 inline 与 noinline 回调，界面日志展示立即执行和延后执行的顺序。 */
class AdvancedFunctionActivity : ComponentActivity() {
    private var output by mutableStateOf("")
    companion object { const val TAG = "AdvancedFunctionActivity" }
    /** 每次运行重新开始日志，返回的回调由按钮处理函数显式执行。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("高阶函数", onBack = { finish() }) {
                    Text("inline 回调立即执行；noinline 回调作为值返回后执行。转发示例仅调用第一个回调，第二个参数保留原有未调用行为。")
                    Button(onClick = {
                        output = ""
                        val deferred = testInlineFunc("Compose", { output += "inline 执行\n" }, { output += "noinline 返回后执行\n" })
                        output += "已获得回调\n"
                        deferred()
                    }) { Text("运行 inline / noinline") }
                    Button(onClick = { testInlineFunc1({ output += "传给普通函数的回调\n" }, { output += "第二回调\n" }) }) { Text("运行回调转发") }
                    SelectionContainer { Text(output) }
                }
            }
        }
    }
    /** name 保留签名兼容；block 当场调用，block1 不在此处执行而是原样返回。 */
    inline fun testInlineFunc(name: String, block: () -> Unit, noinline block1: () -> Unit): () -> Unit {
        block()
        return block1
    }
    /** noinline 允许将 block 传给普通函数；block1 沿用原契约不执行。 */
    inline fun testInlineFunc1(noinline block: () -> Unit, block1: () -> Unit) {
        testNoInlineFunc(block)
    }
    /** 执行转发的回调，回调异常按普通函数调用规则向调用方传播。 */
    fun testNoInlineFunc(block: () -> Unit) { block() }

}
