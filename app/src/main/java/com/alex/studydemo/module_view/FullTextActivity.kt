package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

/** 前四行保持不透明，只对新增文本淡入淡出；高度随同一动画进度变化。 */
class FullTextActivity : ComponentActivity() {
    companion object {
        /** 保留调用方现有的 Context 启动入口。 */
        fun newInstance(context: Context) { context.startActivity(Intent(context, FullTextActivity::class.java)) }
    }

    /** Compose 的文字测量跟随可用宽度及字体变化，避免首次布局前截断位置为零。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExperimentPage("全文展开与收起", { finish() }) { ExpandableText() }
        }
    }
}

/** 以实际排版的第四行末尾为透明度边界；快速反向点击从当前进度继续。 */
@Composable
private fun ExpandableText() {
    val content = "在产品详情页中，默认只展示部分文案，点击\"全文\"后应以柔和的过渡呈现剩余内容。展开时，仅底部新增的文本以淡入方式出现，避免整段文本闪烁。再次收起时，底部文本淡出并隐藏，保留顶部固定的内容，保证页面整洁。该段文案用于示例，长度适中以便观察到展开与收起的过渡效果。\n\n具体实现包含：1）计算前四行的精确截断位置作为固定文本；2）使用 Compose Text，通过行数与透明度的动画仅对底部新增文本进行从上到下的淡入；3）点击切换按钮控制展开与收起状态。"
    var expanded by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(if (expanded) 300 else 200), label = "全文进度")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val color = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        val width = with(density) { maxWidth.roundToPx() }
        val layout = measurer.measure(content, style = style, constraints = Constraints(maxWidth = width))
        val line = minOf(3, layout.lineCount - 1)
        val boundary = layout.getLineEnd(line)
        val collapsedHeight = layout.getLineBottom(line)
        // 高度与透明度使用同一进度；顶部文本不参与透明度动画。
        val height = with(density) { (collapsedHeight + (layout.size.height - collapsedHeight) * progress).toDp() }
        val text = buildAnnotatedString {
            append(content)
            if (boundary < content.length) addStyle(SpanStyle(color = color.copy(alpha = progress)), boundary, content.length)
        }
        Column {
            Text(text, style = style, color = color,
                maxLines = if (progress == 0f) 4 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(height).clipToBounds())
            if (layout.lineCount > 4) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) "收起" else "全文")
                }
            }
        }
    }
}
