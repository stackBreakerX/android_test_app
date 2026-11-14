package com.alex.studydemo.module_view

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** 仅供交互实验页使用的独立页面壳；返回交给宿主 finish，内容负责自身状态及原生实验控件。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExperimentPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    StudyTheme {
        StudyPage(title, onBack, scrollable = false,
            contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}
