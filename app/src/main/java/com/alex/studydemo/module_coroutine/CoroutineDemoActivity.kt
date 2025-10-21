//package com.alex.studydemo.module_coroutine
//
//import android.content.Context
//import android.content.Intent
//import androidx.appcompat.app.AppCompatActivity
//import android.os.Bundle
//import android.util.Log
//import androidx.annotation.WorkerThread
//import androidx.lifecycle.flowWithLifecycle
//import androidx.lifecycle.lifecycleScope
//import androidx.lifecycle.liveData
//import com.alex.studydemo.databinding.ActivityCoroutineDemoBinding
//import com.alex.studydemo.hilt.UserInfoDto
//import com.alex.studydemo.module_coroutine.CoroutineDemoActivity.Companion.PATH
//import com.alibaba.android.arouter.facade.annotation.Route
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.*
//import javax.inject.Inject
//
//@AndroidEntryPoint
//@Route(path = PATH)
//class CoroutineDemoActivity : AppCompatActivity() {
//
//    @Inject
//    lateinit var userInfoDto: UserInfoDto
//
//    private lateinit var viewBinding: ActivityCoroutineDemoBinding
//
//    companion object {
//        const val PATH = "/second/coroutine"
//
//        private const val TAG = "CoroutineDemoActivity"
//
//        fun newInstance(context: Context) {
//            val intent = Intent(context, CoroutineDemoActivity::class.java)
//            context.startActivity(intent)
//        }
//    }
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        viewBinding = ActivityCoroutineDemoBinding.inflate(layoutInflater)
//        setContentView(viewBinding.root)
//
//
//        //阻塞主线程
////        runBlocking {
////            delay(500)
////            Log.d(TAG, "runBlocking thread = ${Thread.currentThread().name}")
////        }
//
//        userInfoDto.age = 23
//        viewBinding.textView4.text = userInfoDto.age.toString()
//
//
////        lifecycleScope.launch {
////        }
//
//        viewBinding.button.setOnClickListener {
//            setResult(100)
//            finish()
//        }
//
//        liveData(Dispatchers.Default) {
//            for (i in 1..5) {
//                delay(100)
//                emit(i)
//                Log.d(TAG, "liveData thread = ${Thread.currentThread().name}")
//            }
//        }.observe(this) {
//            Log.d(TAG, "liveData observe thread = ${Thread.currentThread().name} value = $it")
//        }
//
//        GlobalScope.launch(Dispatchers.Default) {
//            var num = 0
//            val a1 = async {
//                delay(1000)
//                num++
//                Log.d(TAG, "111111111111 num = $num " + Thread.currentThread().name)
//                num
//            }
//            val a2 = async {
//                delay(100)
////                1/0 // 故意报错
//                num++
//                Log.d(TAG, "22222222222 num = $num " + Thread.currentThread().name)
//                num
//            }
//            val total = a1.await() + a2.await()
//            Log.d(TAG, "total = $total " + Thread.currentThread().name)
//        }
//
//        flowOf(1,2,3,4,5)
//            .onEach {
//
//            }
//
//
////        GlobalScope.launch {
////            async {
////
////            }
////
////            flowTest()
////        }
//
//    }
//
//    @WorkerThread
//    suspend fun flowTest() {
//        flow {
//
//            for (i in 1..5) {
//                delay(100)
//                emit(i)
//            }
//        }.flowWithLifecycle(lifecycle).collect {
//            Log.d(TAG, "thread name = ${Thread.currentThread().name} flowTest() called i = $it")
//        }
//    }
//}