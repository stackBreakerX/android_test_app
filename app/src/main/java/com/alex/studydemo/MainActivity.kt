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

    inner class Task : Runnable {
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
        val entries = listOf(
            MainEntry("Arouter Entry") { ArouterMainActivity.newInstance(this) },
            MainEntry("Hilt Entry") { /* Hilt demo 已下线，如需可恢复 */ },
            MainEntry("Coroutine Entry") { ChannelActivity.newInstance(this) },
            MainEntry("RecyclerView Entry") {
                ARouter.getInstance()
                    .build(RecyclerViewActivity.PATH)
                    .navigation(this)
            },
            MainEntry("Room Entry") {
                ARouter.getInstance()
                    .build(RoomActivity.PATH)
                    .navigation(this)
            },
            MainEntry("View Entry") { CustomerViewActivity.newInstance(this) },
            MainEntry("多媒体入库（合并入口）") {
                startActivity(
                    Intent(
                        this,
                        com.alex.studydemo.module_media.MediaEntryActivity::class.java
                    )
                )
            },
            MainEntry("Network Entry") { NetworkEntryActivity.newInstance(this) },
            MainEntry("Performance Entry") {
                startActivity(
                    Intent(
                        this,
                        com.alex.studydemo.module_performance.PerformanceEntryActivity::class.java
                    )
                )
            },
            MainEntry("Test try catch") { testTryCatch(it) },
            MainEntry(getString(R.string.full_text_entry)) {
                com.alex.studydemo.module_view.FullTextActivity.newInstance(
                    this
                )
            },
            MainEntry("Two-Stage Header") {
                com.alex.studydemo.module_view.TwoStageHeaderActivity.newInstance(
                    this
                )
            },
            MainEntry("TG 文本消息(对齐)") {
                startActivity(
                    Intent(
                        this,
                        com.alex.studydemo.chat_tg.TgTextChatActivity::class.java
                    )
                )
            },
            MainEntry("ItemAnimation 列表") {
                startActivity(
                    Intent(
                        this,
                        com.alex.studydemo.listdemo.AnimListActivity::class.java
                    )
                )
            },
            MainEntry("Animation Entry") {
                startActivity(
                    Intent(
                        this,
                        com.alex.studydemo.module_animation.AnimationsEntryActivity::class.java
                    )
                )
            },
        )
        recycler.adapter = MainEntryAdapter(entries)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Toast.makeText(this, "跳转后返回$requestCode", Toast.LENGTH_LONG).show()
        finish()
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
