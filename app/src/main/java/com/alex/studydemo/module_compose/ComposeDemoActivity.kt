package com.alex.studydemo.module_compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.studydemo.module_compose.modal.IosModalDemoActivity
import com.alex.studydemo.module_compose.state.ComposeStateActivity
import com.alex.studydemo.module_compose.swipe.SwipeActionsActivity

/** 首页唯一的 Compose 入口，具体示例都在此选择，新增示例无需再向 MainActivity 添加按钮。 */
class ComposeDemoActivity : ComponentActivity() {
    /** 示例目录独立于具体示例的状态，返回目录后可继续进入其他示例。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp)) {
                        TextButton(onClick = ::finish) { Text("‹ 返回首页") }
                        Text("Compose 示例", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "组件与交互演示",
                            Modifier.padding(top = 8.dp, bottom = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn {
                            item {
                                ComposeEntry("iOS 模态弹窗", "底部卡片、跟手拖动、列表滚动衔接", "compose_entry_modal") {
                                    startActivity(Intent(this@ComposeDemoActivity, IosModalDemoActivity::class.java))
                                }
                            }
                            item {
                                ComposeEntry("iOS 信息侧滑", "左右操作按钮、完整侧滑、列表动画", "compose_entry_swipe") {
                                    startActivity(Intent(this@ComposeDemoActivity, SwipeActionsActivity::class.java))
                                }
                            }
                            item {
                                ComposeEntry("状态与重组", "mutableStateOf 与 rememberSaveable", "compose_entry_state") {
                                    startActivity(Intent(this@ComposeDemoActivity, ComposeStateActivity::class.java))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 目录项只负责导航，具体示例的业务内容和组件参数由各自页面管理。 */
@Composable
private fun ComposeEntry(title: String, description: String, tag: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}
