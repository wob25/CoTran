package com.copytranslate.app

import android.app.Application
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application：初始化全局 OkHttpClient 单例。
 * 读/写超时 30 秒：长文本走 B站 AI 翻译耗时较长，10 秒会超时失败。
 */
class App : Application() {

    companion object {
        lateinit var instance: App
            private set
        lateinit var okHttpClient: OkHttpClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
