package com.alex.studydemo.module_compose.modal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.studydemo.module_compose.components.iosmodal.IosModal
import com.alex.studydemo.module_compose.components.iosmodal.IosModalProperties
import com.alex.studydemo.module_compose.components.iosmodal.IosModalPresentationStyle

/** 模态组件使用示例；页面只提供显示状态和业务内容，手势与动画由 IosModal 处理。 */
class IosModalDemoActivity : ComponentActivity() {
    companion object {
        /** 目录通过枚举名称选择呈现样式；缺失或未知值均使用原有背景缩放样式。 */
        const val EXTRA_PRESENTATION_STYLE = "ios_modal_presentation_style"
    }

    /** 开启边到边绘制，页面和模态分别消费自己的安全区。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requestedStyle = intent.getStringExtra(EXTRA_PRESENTATION_STYLE)
        val presentationStyle = IosModalPresentationStyle.entries.firstOrNull { it.name == requestedStyle }
            ?: IosModalPresentationStyle.Stacked
        setContent { IosModalDemo(onBack = ::finish, presentationStyle = presentationStyle) }
    }
}

private val IosBlue = Color(0xFF007AFF)

/** 两种呈现方式共用短卡片和长列表；配置变化后恢复当前弹窗及用户选择。 */
@Composable
fun IosModalDemo(
    onBack: () -> Unit = {},
    presentationStyle: IosModalPresentationStyle = IosModalPresentationStyle.Stacked,
) {
    val overlay = presentationStyle == IosModalPresentationStyle.Overlay
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        background = Color(0xFF000000),
        surface = Color(0xFF1C1C1E),
        surfaceContainer = Color(0xFF2C2C2E),
    ) else lightColorScheme(
        primary = IosBlue,
        onPrimary = Color.White,
        background = Color(0xFFF2F2F7),
        surface = Color.White,
        surfaceContainer = Color(0xFFF2F2F7),
    )
    var visible by rememberSaveable { mutableStateOf(false) }
    // 类型单独保留，关闭时不清空，确保退场过程仍绘制原来的卡片内容。
    var sheetKind by rememberSaveable { mutableStateOf("compact") }
    var audience by rememberSaveable { mutableStateOf("仅自己可见") }
    var destination by rememberSaveable { mutableStateOf("杭州") }
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background) {
            IosModal(
                visible = visible,
                onDismissRequest = { visible = false },
                title = if (sheetKind == "compact") "分享设置" else "选择目的地",
                properties = IosModalProperties(
                    heightFraction = if (sheetKind == "compact") 0.68f else 0.92f,
                    presentationStyle = presentationStyle,
                ),
                sheetContent = { dismiss ->
                    if (sheetKind == "compact") {
                        ShareSettings(audience, onSelect = { audience = it }, onDone = dismiss)
                    } else {
                        DestinationList(destination) {
                            // 先保存选择，再请求关闭；背景页面在回到前台时显示最终结果。
                            destination = it
                            dismiss()
                        }
                    }
                },
            ) {
                Column(
                    Modifier.fillMaxSize().background(colors.background)
                        .safeDrawingPadding().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    TextButton(onClick = onBack) { Text("‹ Compose 示例", fontSize = 17.sp) }
                    Spacer(Modifier.height(20.dp))
                    Text(if (overlay) "浮层模态" else "模态弹窗", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (overlay) "COMPOSE · OVERLAY" else "COMPOSE · STACKED",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    Surface(shape = RoundedCornerShape(24.dp), color = colors.surface) {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            Box(
                                Modifier.size(56.dp).background(colors.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("↑", fontSize = 30.sp, color = colors.primary)
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                if (overlay) "浮在当前页面上方" else "从底部，轻轻展开",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (overlay) {
                                    "背景保持原尺寸和位置，卡片独立滑入。试试向下拖动，松手后关闭或回弹。"
                                } else {
                                    "打开一张卡片，试试向下拖动。背后的页面会随手势缩放，松手后关闭或回弹。"
                                },
                                color = colors.onSurfaceVariant,
                                lineHeight = 23.sp,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { sheetKind = "compact"; visible = true },
                                modifier = Modifier.fillMaxWidth().testTag("open_compact"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            ) { Text(if (overlay) "打开浮层模态" else "打开模态弹窗", fontSize = 17.sp) }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("试试滚动内容", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                        Column {
                            DemoRow(
                                "选择目的地", destination, Modifier.testTag("open_list"),
                            ) { sheetKind = "list"; visible = true }
                            HorizontalDivider(Modifier.padding(start = 20.dp), color = colors.outlineVariant)
                            DemoRow("分享范围", audience) { sheetKind = "compact"; visible = true }
                        }
                    }
                    Text(
                        "列表滚到顶部后继续下拉，即可拖动整张卡片。也可以点击遮罩、完成按钮或系统返回键关闭。",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

/** 分组设置行保留整行点击区域，右侧显示当前保存的选择。 */
@Composable
private fun DemoRow(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 17.sp)
        Text("$value  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    }
}

/** 短卡片同样允许滚动，保证小屏、横屏或大字体下仍能到达底部操作。 */
@Composable
private fun ShareSettings(audience: String, onSelect: (String) -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("↗", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp) }
        Spacer(Modifier.height(16.dp))
        Text("分享你的下一站", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("选择谁可以看到这段旅程", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            listOf("仅自己可见", "与朋友分享", "所有人可见").forEachIndexed { index, option ->
                Row(
                    Modifier.fillMaxWidth().semantics { selected = audience == option }
                        .clickable(role = Role.RadioButton) { onSelect(option) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option, modifier = Modifier.weight(1f))
                    Text(if (audience == option) "✓" else "", color = MaterialTheme.colorScheme.primary)
                }
                if (index < 2) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(),
        ) { Text("保存设置", Modifier.padding(vertical = 8.dp), fontSize = 17.sp) }
        Spacer(Modifier.height(8.dp))
    }
}

/** 使用真实 LazyColumn 验证滚动衔接；只有列表到顶后的剩余下拉才会传给模态。 */
@Composable
private fun DestinationList(destination: String, onSelect: (String) -> Unit) {
    val cities = listOf(
        "杭州", "上海", "北京", "成都", "大理", "厦门", "苏州", "南京", "重庆", "青岛",
        "西安", "长沙", "广州", "深圳", "珠海", "昆明", "丽江", "桂林", "阳朔", "三亚",
        "海口", "福州", "泉州", "武汉", "洛阳", "开封", "济南", "威海", "大连", "哈尔滨",
    )
    LazyColumn(Modifier.fillMaxSize().testTag("destination_list")) {
        item {
            Text(
                "下一段旅程，想去哪里？",
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(cities, key = { _, city -> city }) { index, city ->
            Row(
                Modifier.fillMaxWidth().semantics { selected = destination == city }
                    .clickable(role = Role.RadioButton) { onSelect(city) }
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("%02d".format(index + 1), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(city, Modifier.weight(1f), fontSize = 18.sp)
                if (destination == city) Text("✓", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 预览背景页面，弹窗交互需在模拟器或交互预览中操作。 */
@Preview(showBackground = true)
@Composable
private fun IosModalDemoPreview() { IosModalDemo() }
