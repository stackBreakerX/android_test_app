package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import com.alex.studydemo.R

/** 用嵌套滚动实现 140→240dp 头图展开和最多 25% 的拉伸，列表包含原有四十项。 */
class TwoStageHeaderActivity : ComponentActivity() {
    companion object {
        /** 保留现有页面启动 API。 */
        fun newInstance(context: Context) { context.startActivity(Intent(context, TwoStageHeaderActivity::class.java)) }
    }
    /** 滚动连接与进度属于本次 Composition，退出页面后不保留监听器。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ExperimentPage("Two-Stage Header", { finish() }) { TwoStageHeader() } }
    }
}

/** 向上先消耗头图的展开与拉伸，向下仅在列表到顶后的剩余距离中展开头图。 */
@Composable
private fun ColumnScope.TwoStageHeader() {
    val density = LocalDensity.current
    val limit = with(density) { 120.dp.toPx() }
    var stretch by remember { mutableFloatStateOf(0f) }
    val connection = remember(limit) {
        object : NestedScrollConnection {
            /** 上划优先折叠头图，余量交给列表，避免列表与头图同时消费距离。 */
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0 || stretch <= 0) return Offset.Zero
                val used = minOf(stretch, -available.y)
                stretch -= used
                return Offset(0f, -used)
            }
            /** 列表到顶后的下拉才扩张；限制位移防止无限增高。 */
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0) return Offset.Zero
                val used = minOf(limit - stretch, available.y)
                stretch += used
                return Offset(0f, used)
            }
        }
    }
    val progress = (stretch / limit).coerceIn(0f, 1f)
    Column(Modifier.weight(1f).fillMaxWidth().nestedScroll(connection)) {
        Box(Modifier.fillMaxWidth().height((140 + 100 * progress).dp).background(MaterialTheme.colorScheme.primaryContainer)) {
            Image(painterResource(R.mipmap.ic_launcher), contentDescription = "可下拉放大的头图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = 1 + progress * .25f
                    scaleY = 1 + progress * .25f
                })
            Text("Two-Stage Header", fontSize = (18 + 2 * progress).sp,
                modifier = Modifier.align(Alignment.BottomStart).padding((12 + 4 * progress).dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(List(40) { "Item $it" }) { item ->
                Text(item, Modifier.fillMaxWidth().padding(20.dp))
                HorizontalDivider()
            }
        }
    }
}
