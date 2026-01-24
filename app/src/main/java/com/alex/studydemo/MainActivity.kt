package com.alex.studydemo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.arouter.ArouterMainActivity
import com.alex.studydemo.module_coroutine.ChannelActivity
import com.alex.studydemo.module_recyclerview.RecyclerViewActivity
import com.alex.studydemo.module_room.RoomActivity
import com.alex.studydemo.module_view.CustomerViewActivity
import com.alibaba.android.arouter.launcher.ARouter
import com.blankj.utilcode.util.TimeUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import com.alex.studydemo.databinding.ItemMainEntryBinding
import com.alex.studydemo.databinding.ActivityMainBinding
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.network.request.NetworkEntryActivity
import kotlin.jvm.java


class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val sPool: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    companion object {
        const val REQUEST_CODE = 99
    }

    /**
     * 接收、分析、执行模块
     */
    @SuppressLint("HandlerLeak")
    var handler: Handler = object : Handler(Looper.getMainLooper()) {
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            Log.d("111111", "onCreate() called 99999999999")
        }
    }

    val newTask = Task()

    inner class Task: Runnable {
        override fun run() {
            // 此处执行任务 此处即使界面返回也会一直后台运行
            Log.i("djtest", "run: 该条打印信息仅测试锁屏情况下是否会执行task内容");

            // 每5s重复一次
            handler.postDelayed(this, 5 * 1000);//延迟5秒,再次执行task本身,实现了5s一次的循环效果
        }

    }


    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        ARouter.getInstance().inject(this)

        // 仅在 MainActivity 隐藏返回按钮（覆盖 BaseActivity 的默认行为）
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)
        toolbar?.navigationIcon = null

        val recycler = binding.recyclerMain
        recycler.layoutManager = GridLayoutManager(this, 2)
        val entries = buildEntries()
        recycler.adapter = MainEntryAdapter(entries)

//        sPool.scheduleWithFixedDelay({
//            try {
//                // Run the command
//                Log.e("date_time: ", "date_time start")
//                val process = Runtime.getRuntime().exec("date +%s")
//                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
//
//                // Grab the results
//                val log = StringBuilder()
//                var line: String?
//                while ((bufferedReader.readLine().also { line = it }) != null) {
//                    log.append(line)
//                }
//                val time = Date(log.toString().toLong() * 1000)
//
//                TimeUtils.getNowDate()
//                val elapsedRealtime = SystemClock.elapsedRealtime()
//                Log.e("date_time: ", "date_time 11111 elapsedRealtime = $elapsedRealtime date = " + Date(elapsedRealtime))
//
//
//                Log.e("date_time: ", "date_time = " + time.toString())
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//        },0,1,TimeUnit.SECONDS)

        // 当我们使用requestLocationUpdates时，我们需要实现LocationListener接口。
        // 在LocationListen的回调onLocationChanged当中获取时间




//        Thread({
//            while (true) {
//               Thread.sleep(2000)
//                Log.d("111111", "onCreate() called 99999999999")
//            }
//
//        }).start()

//        handler.postDelayed(newTask,2000)
//        lifecycleScope.launch {
//            while (true) {
//                delay(20000)
//                Log.d("9999999999", "onCreate() called")
//            }
//        }

//        lifecycleScope.launch(Dispatchers.IO) {
//            delay(5000)
//            withContext(Dispatchers.Main) {
////                val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(this@MainActivity)
////                stackBuilder.addParentStack(ArouterMainActivity::class.java)
////                stackBuilder.addNextIntent(Intent(this@MainActivity, ChannelActivity::class.java))
////                stackBuilder.startActivities()
//
//                ArouterMainActivity.newInstance(this@MainActivity)
//
//
//                val intent: Intent = Intent(this@MainActivity, ChannelActivity::class.java)
//                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
//                startActivity(intent)
////                ChannelActivity.newInstance(this@MainActivity)
//
//
//            }
//        }
    }

    fun arouterDemo(view: View) {
        ArouterMainActivity.newInstance(this)
    }

    fun hiltDemo(view: View) {
//        HiltMainActivity.newInstance(this)
    }

    fun coroutineDemo(view: View) {
        ChannelActivity.newInstance(this)
//        ARouter.getInstance()
//            .build(CoroutineDemoActivity.PATH)
//            .navigation(this,REQUEST_CODE)

//        CoroutineDemoActivity.newInstance(this)

//        ARouter.getInstance()
//            .build("/main/secondActivity")
//            .withString("test","111111111")
//            .navigation()
    }

    fun recyclerViewDemo(view: View) {
        ARouter.getInstance()
            .build(RecyclerViewActivity.PATH)
            .navigation(this)
    }

    fun RoomDemo(view: View) {
        ARouter.getInstance()
            .build(RoomActivity.PATH)
            .navigation(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Toast.makeText(this, "跳转后返回$requestCode", Toast.LENGTH_LONG).show()
//        HiltMainActivity.newInstance(this)
        finish()
    }

    fun ViewDemo(view: View) {
        CustomerViewActivity.newInstance(this)
    }

    fun imageWebpDemo(view: View) {
        val intent = Intent(this, com.alex.studydemo.module_media.WebpConvertActivity::class.java)
        startActivity(intent)
    }

    fun imageWebpLibDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_media.WebpLibwebpActivity::class.java)
        startActivity(intent)
    }

    fun imagePngConvertDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_media.PngConvertActivity::class.java)
        startActivity(intent)
    }

    fun imageJpgConvertDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_media.JpgConvertActivity::class.java)
        startActivity(intent)
    }

    fun pngAlphaCheckDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_media.PngAlphaCheckActivity::class.java)
        startActivity(intent)
    }

    fun imagePickerDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_image.ImagePickerActivity::class.java)
        startActivity(intent)
    }

    fun fullTextDemo(view: View) {
        com.alex.studydemo.module_view.FullTextActivity.newInstance(this)
    }

    fun fastThumbnailDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_image.FastThumbnailActivity::class.java)
        startActivity(intent)
    }

    fun dngProcessDemo(view: View) {
        val intent = android.content.Intent(this, com.alex.studydemo.module_image.DngProcessActivity::class.java)
        startActivity(intent)
    }

    fun twoStageHeaderDemo(view: View) {
        com.alex.studydemo.module_view.TwoStageHeaderActivity.newInstance(this)
    }

    // 合并的多媒体入库入口：跳转到二级页面
    fun mediaInsertEntry(view: View) {
        startActivity(Intent(this, com.alex.studydemo.module_media.MediaEntryActivity::class.java))
    }

    fun networkEntryDemo(view: View) {
        NetworkEntryActivity.newInstance(this)
    }

    fun performanceEntry(view: View) {
        startActivity(Intent(this, com.alex.studydemo.module_performance.PerformanceEntryActivity::class.java))
    }
    fun tgTextChatDemo(view: View) {
        startActivity(Intent(this, com.alex.studydemo.chat_tg.TgTextChatActivity::class.java))
    }
    fun testTryCatch(view: View) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = mutableListOf<Deferred<Any?>>()
                val result1 = async {
                    testSubTryCatch1()
                    Any()
                }
                results.add(result1)

                val result2 = async {
                    testSubTryCatch1()
                    Any()
                }
                results.add(result2)

                val result3 = async {
                    testSubTryCatch1()
                    withContext(Dispatchers.Main) {
                        val i = 100 / 0
                    }
                    Any()
                }
                results.add(result3)

                val result4 = async {
                    testSubTryCatch1()
                    Any()
                }
                results.add(result4)

                val allResult = results.awaitAll()
                for (any in allResult) {
                    if (any == null) {
                        println("error = null")
                    }
                }

                println("allResult is success")
            } catch (t: Throwable) {
                println("error = $t")
            }
        }
    }

    suspend fun testSubTryCatch1() {
        delay(1000)

//        val i = 100 / 0
    }

    suspend fun testSubTryCatch2() {
        delay(800)
    }

    suspend fun testSubTryCatch3() {
        delay(500)
    }

    suspend fun testSubTryCatch4() {
    }

    fun testSubTryCatch() {
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val i = 100 / 0
            } catch (e: Throwable) {
                println("error = $e")
            }
        }
    }

    private data class MainEntry(
        val title: String,
        val onClick: (View) -> Unit
    )

    private fun buildEntries(): List<MainEntry> = listOf(
        MainEntry("Arouter Entry", ::arouterDemo),
        MainEntry("Hilt Entry", ::hiltDemo),
        MainEntry("Coroutine Entry", ::coroutineDemo),
        MainEntry("RecyclerView Entry", ::recyclerViewDemo),
        MainEntry("Room Entry", ::RoomDemo),
        MainEntry("View Entry", ::ViewDemo),
        MainEntry("多媒体入库（合并入口）", ::mediaInsertEntry),
        MainEntry("Network Entry", ::networkEntryDemo),
        MainEntry("Performance Entry", ::performanceEntry),
        MainEntry("Test try catch", ::testTryCatch),
        MainEntry(getString(R.string.full_text_entry), ::fullTextDemo),
        MainEntry("Two-Stage Header", ::twoStageHeaderDemo),
        MainEntry("TG 文本消息(对齐)", ::tgTextChatDemo),
    )

    private inner class MainEntryAdapter(
        private val items: List<MainEntry>
    ) : RecyclerView.Adapter<MainEntryAdapter.VH>() {

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
}
