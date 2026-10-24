package com.copytranslate.app.util

import android.util.Log

/**
 * 统一日志出口：默认关闭调试日志（避免刷屏），可在设置中开启用于排查问题。
 */
object DebugLog {
    private const val TAG = "CopyTranslate"

    fun d(msg: String) {
        if (Prefs.debugLog) Log.d(TAG, msg)
    }

    fun e(msg: String, tr: Throwable? = null) {
        if (Prefs.debugLog) Log.e(TAG, msg, tr)
    }
}
