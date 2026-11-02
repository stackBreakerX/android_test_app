package com.alex.studydemo.module_compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alex.studydemo.compose.MuteStateOfActivity
import com.alex.studydemo.module_animation.swipe.SwipeActionsActivity

/** Compose 相关 demo 的入口列表。 */
class ComposeDemoActivity : ComponentActivity() {

    private data class Entry(val title: String, val target: Class<*>)

    private val entries = listOf(
        Entry("iOS 信息侧滑按钮", SwipeActionsActivity::class.java),
        Entry("mutableStateOf", MuteStateOfActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EntryList(entries) { startActivity(Intent(this, it.target)) }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun EntryList(items: List<Entry>, onClick: (Entry) -> Unit) {
        Scaffold(topBar = { TopAppBar(title = { Text("Compose Entry") }) }) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(items) { entry ->
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick(entry) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
