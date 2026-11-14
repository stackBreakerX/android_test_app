package com.alex.studydemo.module_coroutine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Channel、单线程调度、原子门闩、Mutex、runBlocking 与 Semaphore 的可执行实验。 */
class ChannelActivity : ComponentActivity() {
    private var output by mutableStateOf("")
    private var running by mutableStateOf(0)
    // 队列消费者随 Activity 销毁取消；任务必须被调用才能完成对应应答。
    private val queue = Channel<suspend (Int) -> Unit>(Channel.UNLIMITED)
    private val jobs = mutableListOf<Job>()
    private val mutex = Mutex()
    private val canWork = AtomicBoolean(true)
    /** 保留原公开线程池；调用方不得在页面销毁后继续提交任务。 */
    val newSingleThreadExecutor = Executors.newFixedThreadPool(2)
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    companion object {
        private const val TAG = "ChannelActivity"
        /** 保留显式启动入口。 */
        fun newInstance(context: Context) { context.startActivity(Intent(context, ChannelActivity::class.java)) }
    }

    /** 持续消费并执行队列任务；某个任务失败不会丢弃后续任务。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            for (task in queue) {
                try { task(0) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { log("队列任务失败：${e.message}") }
            }
        }
        setContent { Page("Channel 与并发") {
            Text("活动实验：$running；所有任务随页面销毁取消")
            Button(onClick = { addChannelTask() }) { Text("Add Channel Task（100 个应答）") }
            Button(onClick = { testSingleThread() }) { Text("Add single Task") }
            Button(onClick = { testLockThread() }) { Text("Add lock Task（3 秒门闩）") }
            Button(onClick = { withLockTest() }) { Text("Add withlock Task") }
            Button(onClick = { testRunBlock() }) { Text("Add RunBlock Task") }
            Button(onClick = { testSemaphoreSerial() }) { Text("Semaphore 并发测试") }
            TextButton(onClick = { jobs.toList().forEach { it.cancel() } }) { Text("取消正在运行的实验") }
            TextButton(onClick = { output = "" }) { Text("清空日志") }
            SelectionContainer { Text(output) }
        } }
    }

    /** 统一管理实验句柄；取消传播到子协程，普通失败只影响该次实验。 */
    private fun experiment(name: String, block: suspend CoroutineScope.() -> Unit) {
        val job = lifecycleScope.launch {
            running++
            try { log("$name 开始"); block(); log("$name 完成") }
            catch (e: CancellationException) { log("$name 已取消"); throw e }
            catch (e: Exception) { log("$name 失败：${e.message}") }
            finally { running-- }
        }
        jobs += job
        job.invokeOnCompletion { jobs.remove(job) }
    }

    /** 每个请求使用独立的缓冲应答 Channel，消费者真正执行任务后才唤醒请求方。 */
    private fun addChannelTask() = experiment("Channel") {
        coroutineScope {
            repeat(100) { i -> launch {
                val reply = Channel<Int>(1)
                try {
                    queue.send { reply.trySend(i) }
                    log("Channel 应答=${reply.receive()}")
                } finally { reply.close() }
            } }
        }
    }

    /** 单线程调度器限制同时执行的线程数，挂起点允许其他子任务交错进入。 */
    private fun testSingleThread() = experiment("单线程") {
        withContext(singleDispatcher) {
            coroutineScope { repeat(101) { i -> launch { testChildThread(i) } } }
        }
    }

    /** 原子比较并交换防止重复进入；取消或异常都必须释放门闩。 */
    private fun testLockThread() = experiment("原子门闩") {
        if (!canWork.compareAndSet(true, false)) { log("已有任务工作中，跳过"); return@experiment }
        try { delay(3000); log("门闩工作完成") }
        finally { canWork.set(true) }
    }

    /** Mutex 跨点击复用；回调经线程池完成 Deferred，等待期间释放主线程。 */
    private fun withLockTest() = experiment("Mutex") {
        mutex.withLock {
            log("进入临界区")
            // 模拟原实验的 3 秒准备和 30 秒异步工作，挂起等待使取消按钮仍能响应。
            delay(3000)
            async { delay(30000); log("Mutex 内启动的异步任务完成") }
            val result = CompletableDeferred<String>()
            val future = newSingleThreadExecutor.submit {
                try { Thread.sleep(1000); result.complete("线程池回调完成") }
                catch (e: InterruptedException) { result.cancel(); Thread.currentThread().interrupt() }
            }
            try { val message = result.await(); delay(1000); log(message) }
            finally { future.cancel(true) }
        }
    }

    /** runBlocking 确实阻塞其调用线程；放在 IO 上，避免教学操作冻结界面。 */
    private fun testRunBlock() = experiment("runBlocking") {
        withContext(Dispatchers.IO) {
            runBlocking(currentCoroutineContext()) {
                launch { delay(1000); log("子任务 A 完成") }
                launch(Dispatchers.IO) { delay(1000); log("子任务 B 完成") }
                supervisorScope { launch { delay(1000); log("supervisorScope C 完成") } }
                log("supervisorScope 已等待 C")
                coroutineScope { launch { delay(1000); log("coroutineScope D 完成") } }
                log("coroutineScope 已等待 D")
            }
        }
    }

    /** 一百个竞争协程共享一个许可；取消时 withPermit 自动归还许可。 */
    private fun testSemaphoreSerial() = experiment("Semaphore") {
        val semaphore = Semaphore(1)
        coroutineScope { repeat(100) { i -> launch {
            semaphore.withPermit { log("进入 $i"); delay(200); log("退出 $i") }
        } } }
    }

    /** 保留原公开故障演示入口；调用始终抛出 ArithmeticException，供调用方练习捕获。 */
    fun initDB(): String { throw ArithmeticException("演示数据库初始化失败") }

    /** 保留原公开样例对象与固定名称。 */
    class TestLJX { val name: String = "121212" }

    /** 保留公开挂起入口；只输出日志，不执行阻塞操作。 */
    suspend fun testSuspend() { log("testSuspend() called") }

    /** 保留公开返回值契约，正常路径返回空字符串。 */
    fun testTryCatch(): String = ""

    /** 模拟挂起工作后恢复，日志同时展示任务序号和执行线程。 */
    private suspend fun testChildThread(i: Int) {
        val value = TestLJX().name
        delay(100)
        log("任务 $i / $value / ${Thread.currentThread().name}")
    }

    /** 允许工作线程记录结果；Compose 状态只在主线程修改并限制为 12000 字符。 */
    private fun log(message: String) {
        Log.d(TAG, message)
        runOnUiThread { output = (output + message + "\n").takeLast(12000) }
    }

    /** 关闭队列并取消页面协程，释放自建线程池；不遗留 GlobalScope 任务。 */
    override fun onDestroy() {
        queue.cancel()
        jobs.toList().forEach { it.cancel() }
        newSingleThreadExecutor.shutdownNow()
        singleDispatcher.close()
        super.onDestroy()
    }

    /** 页面使用应用主题和可滚动内容；返回按钮结束当前 Activity，系统返回沿用默认行为。 */
    @Composable
    private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
        com.alex.studydemo.ui.StudyTheme {
            Scaffold(topBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                        TextButton(onClick = { finish() }) { Text("返回") }
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}
