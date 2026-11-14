package com.alex.studydemo.network.request

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 复用带日志和演示拦截器的客户端；请求取消同步传递给底层 Call。 */
object NetworkClient {
    // 连接池跨页面复用，超时沿用原配置；不为每次重试新建客户端。
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .addInterceptor(DemoInterceptor())
            .build()
    }

    /** 执行 GET 并返回完整响应体；无响应体返回空串，HTTP 非成功码和 IO 错误交由调用方展示。 */
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            // Activity 销毁或调用方取消后立即关闭网络操作，避免继续占用连接直到读超时。
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                /** 取消引起的失败不再次恢复协程，普通网络错误保留原始异常。 */
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                /** 响应始终关闭；解析与错误处理发生在 OkHttp 回调线程，不阻塞主线程。 */
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!continuation.isActive) return
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            val body = it.body?.string().orEmpty()
                            if (continuation.isActive) continuation.resume(body)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}
