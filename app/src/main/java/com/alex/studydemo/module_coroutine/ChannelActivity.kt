package com.alex.studydemo.module_coroutine

import android.content.Context
import android.content.Intent
import com.alex.studydemo.base.BaseActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityChannelBinding
import com.alex.studydemo.databinding.ItemMainEntryBinding
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class ChannelActivity : BaseActivity<ActivityChannelBinding>() {

    private val viewBinding: ActivityChannelBinding get() = binding

    private val queue: Channel<suspend (String) -> Unit> = Channel(Channel.UNLIMITED)

    private val scope = CoroutineScope(CoroutineName("ChannelActivity"))

    private val TAG = "ChannelActivity"

    val newSingleThreadExecutor = Executors.newFixedThreadPool(1)
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val singleScope =
        CoroutineScope(CoroutineName(TAG) + CoroutineExceptionHandler { _, throwable ->

        } + singleDispatcher)


    private val canWork = AtomicBoolean(true)

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, ChannelActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityChannelBinding =
        ActivityChannelBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = initDB()
                withContext(dbCtx(db)) {
                    while (isActive) {
                        val data = queue.receive()
                        try {
                            data(db)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val recycler = binding.recyclerChannel
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = EntryAdapter(buildEntries())
    }

    private data class EntryItem(
        val title: String,
        val onClick: (View) -> Unit
    )

    private fun buildEntries(): List<EntryItem> = listOf(
        EntryItem("Add Channel Task") { addChannelTask() },
        EntryItem("Add single Task") { testSingleThread() },
        EntryItem("Add lock Task") {
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d(TAG, "btnLockTask called start")
                testLockThread()
            }
        },
        EntryItem("Add withlock Task") { withLockTest() },
        EntryItem("Add RunBlock Task") { testRunBlock() },
        EntryItem("Semaphore 并发测试") { testSemaphoreSerial() }
    )

    private fun addChannelTask() {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "btnAddTask start channel task")
            val ch = Channel<String>(1)
            queue.send {
                Thread.sleep(2000)
                ch.send("$it 111111111111")
            }
            testSuspend()
            val result = ch.receive()
            Log.d(TAG, "btnAddTask called result 111111")
            withContext(Dispatchers.Main) {
                Log.d(TAG, "btnAddTask called result = $result")
                ToastUtils.showLong(result)
            }
        }
    }

    private fun withLockTest() {
        val mutex = Mutex()
        Log.d(TAG, "btnWithLockTest called 111111 点击事件生效了")
        singleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "btnWithLockTest called 22222 scope 进入成功")
            mutex.withLock {
                val uploader = CompletableDeferred<String>()
                Log.d(TAG, "btnWithLockTest called 3333 mutex 放行了")
                Thread.sleep(3000)
                async {
                    Thread.sleep(30000)
                    Log.d(TAG, "btnWithLockTest called async 异步任务")
                }
                withContext(Dispatchers.Main) {
                    newSingleThreadExecutor.submit {
                        Log.d(TAG, "btnWithLockTest called 4444 线程池开始运行了")
                        Thread.sleep(1000)
                        ThreadUtils.runOnUiThreadDelayed({
                            Log.d(TAG, "btnWithLockTest withContext 当次任务结束")
                            uploader.complete("1")
                        },1000)
                    }
                }
                uploader.await()
            }
        }
    }

    private inner class EntryAdapter(
        private val items: List<EntryItem>
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {

        inner class VH(val binding: ItemMainEntryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemMainEntryBinding.inflate(layoutInflater, parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.btnEntry.text = item.title
            holder.binding.btnEntry.setOnClickListener { v -> item.onClick(v) }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun testSemaphoreSerial() {
        Log.d(TAG, "testSemaphoreSerial start")
        val semaphore = Semaphore(1)
        val inCritical = java.util.concurrent.atomic.AtomicInteger(0)
        lifecycleScope.launch(Dispatchers.Default) {
            repeat(100) { i ->
                launch {
//                    delay(Random.nextLong(0, 50))
                    semaphore.withPermit {
                        val running = inCritical.incrementAndGet()
                        Log.d(TAG, "semaphore enter index=$i inCritical=$running")
                        Thread.sleep(10)
                        val now = inCritical.get()
                        Log.d(TAG, "semaphore exit index=$i inCritical=$now")
                        inCritical.decrementAndGet()
                    }
                }
            }
        }
    }

    private fun testRunBlock() {
        Log.d(TAG, "testRunBlock() 开始了！！！")

        runBlocking {
            launch {
                Log.d(TAG, "testRunBlock() 子任务A开始了！！！")
                Thread.sleep(1000)
                Log.d(TAG, "testRunBlock() 子任务A结束了！！！")
            }

            launch(Dispatchers.IO) {
                Log.d(TAG, "testRunBlock() 子任务B开始了！！！")

                Thread.sleep(1000)
                Log.d(TAG, "testRunBlock() 子任务B结束了！！！")
            }

            Log.d(TAG, "supervisorScope() 上面的 任务开始了！！！")
            supervisorScope {
                Log.d(TAG, "supervisorScope() 任务开始了！！！")
                launch {
                    Log.d(TAG, "supervisorScope() 子任务C开始了！！！")
                    Thread.sleep(1000)
                    Log.d(TAG, "supervisorScope() 子任务C结束了！！！")
                }
                Log.d(TAG, "supervisorScope() 任务结束了！！！")
            }

            Log.d(TAG, "supervisorScope() 下面的 任务开始了！！！")


            Log.d(TAG, "coroutineScope() 上面的 任务开始了！！！")
            coroutineScope {
                Log.d(TAG, "coroutineScope() 任务开始了！！！")
                launch {
                    Log.d(TAG, "coroutineScope() 子任务D开始了！！！")
                    Thread.sleep(1000)
                    Log.d(TAG, "coroutineScope() 子任务D结束了！！！")
                }
                Log.d(TAG, "coroutineScope() 任务结束了！！！")
            }

            Log.d(TAG, "coroutineScope() 下面的 任务开始了！！！")
        }
        Log.d(TAG, "testRunBlock() 结束了！！！")
    }

    private fun testSingleThread() {
        for (i in 0..100) {
            singleScope.launch {
                testChildThread(i)
            }
        }
    }

    private suspend fun testLockThread() {
        withContext(coroutineContext) {
            if (!canWork.get()) {
                return@withContext
            }
            canWork.set(false)
            Thread.sleep(3000)
            Log.d(TAG, "testLock() called finish work")
            canWork.set(true)

        }
    }

    fun initDB(): String {
        val x = 1 / 0
        return "12323"
    }

    class TestLJX {
        val name: String

        init {
            try {
                name = "121212"
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun testSuspend() {
        Log.d(TAG, "testSuspend() called")
    }

    fun testTryCatch(): String {

        return try {
            ""
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun testChildThread(i: Int) {
        val testLJX = TestLJX()
        testLJX.name

        withContext(coroutineContext) {
            Log.d(TAG, "testChildThread() withContext(coroutineContext) called i = $i")
        }
//        GlobalScope.launch(coroutineContext) {
//            Thread.sleep(1000)
//            Log.d(TAG, "testChildThread() called")
//        }

//        CoroutineScope(coroutineContext).launch(coroutineContext) {
//            Thread.sleep(1000)
//            Log.d(TAG, "testChildThread() called")
//        }

//        withContext(Dispatchers.IO) {
//            Log.d(TAG, "testChildThread() withContext(Dispatchers.IO) called")
//        }

        Log.d(TAG, "testChildThread() called")
//        GlobalScope.launch {
//           Thread.sleep(20000)
//            Log.d(TAG, "testChildThread() called")
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private class dbCtx<T>(
        val db: T
    ) : AbstractCoroutineContextElement(dbCtx) {
        companion object Key : CoroutineContext.Key<dbCtx<*>>
    }
}