package com.alex.studydemo.arouter.interceptor

import android.content.Context
import android.util.Log
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.annotation.Interceptor
import com.alibaba.android.arouter.facade.callback.InterceptorCallback
import com.alibaba.android.arouter.facade.template.IInterceptor

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/1/18 5:38 下午
 * @version
 */
@Interceptor(priority = 3)
class TestInterceptor:IInterceptor {

    companion object {
        private const val TAG = "TestInterceptor"

    }

    override fun init(context: Context?) {

    }

    override fun process(postcard: Postcard?, callback: InterceptorCallback?) {
        Log.d(TAG, "process() called with: postcard = $postcard, callback = $callback")
        callback?.onContinue(postcard)
    }
}