package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 默认外观入口，调用方可以复用顶部结构，也可以通过 IosModal 的 header 完全替换。 */
object IosModalDefaults {
    /**
     * 默认顶部包含拖动指示条、居中标题和右侧操作；拖动识别由外层组件统一处理。
     * [dismissButtonText] 可传入业务文案或本地化字符串；[onDismissRequest] 不直接修改业务状态。
     */
    @Composable
    fun Header(
        title: String,
        onDismissRequest: () -> Unit,
        modifier: Modifier = Modifier,
        dismissButtonText: String = "完成",
    ) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier.size(36.dp, 5.dp).background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(3.dp),
                ),
            )
            // 左右操作区使用等宽布局，长文案也不会把标题推离窗口中心。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    title,
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismissRequest) { Text(dismissButtonText) }
                }
            }
        }
    }
}
