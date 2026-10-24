package com.copytranslate.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.copytranslate.app.R
import com.copytranslate.app.util.ClipboardGate

/**
 * 剪贴板监听前台服务：
 * 注册 ClipboardManager 变化监听，按过滤规则筛出新文本后交给 FloatingButtonService 显示悬浮按钮。
 */
class ClipboardMonitorService : Service() {

    /** 通知文案跟随应用内语言 */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.copytranslate.app.util.AppLanguage.wrap(newBase))
    }

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "copy_translate_service"

        /** 供 MainActivity 判断服务运行状态 */
        @Volatile
        var running = false
            private set
    }

    private lateinit var clipboard: ClipboardManager

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { handleClipChanged() }

    override fun onCreate() {
        super.onCreate()
        running = true
        startForegroundWithNotification()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        if (this::clipboard.isInitialized) {
            clipboard.removePrimaryClipChangedListener(listener)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 过滤规则全部通过后才交悬浮按钮服务显示 */
    private fun handleClipChanged() {
        val item = clipboard.primaryClip?.getItemAt(0) ?: return
        val text = item.coerceToText(this)?.toString() ?: return
        // 剪贴板变化监听 = 用户刚执行了复制动作，属主动信号
        ClipboardGate.processActive(this, text)
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        val notification = buildNotification(getString(R.string.notif_clipboard_text))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(getString(R.string.notif_clipboard_title))
            .setContentText(text)
            .setOngoing(true)
            .build()
}
