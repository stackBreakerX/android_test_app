package com.alex.studydemo.module_compose.state

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.studydemo.R

/** 状态示例使用真实 Compose 内容，展示状态修改触发重组以及配置变化后的恢复。 */
class ComposeStateActivity : ComponentActivity() {
    /** 计数只属于当前示例，退出后重新进入会开始新的会话，不写入数据库。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                // rememberSaveable 在旋转或系统重建时恢复计数，重组时复用同一份状态。
                var count by rememberSaveable { mutableStateOf(0) }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        TextButton(onClick = ::finish) { Text("‹ Compose 示例") }
                        Text("状态与重组", style = MaterialTheme.typography.headlineLarge)
                        Text("修改状态后，读取这份状态的界面会自动更新。旋转屏幕后计数仍然保留。")
                        Text(stringResource(R.string.compose_counter, count), fontSize = 28.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { count += 1 }) { Text(stringResource(R.string.compose_increment)) }
                            TextButton(onClick = { count = 0 }) { Text("重置") }
                        }
                    }
                }
            }
        }
    }
}
