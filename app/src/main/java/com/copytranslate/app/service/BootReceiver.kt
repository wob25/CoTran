package com.copytranslate.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.copytranslate.app.util.DebugLog
import com.copytranslate.app.util.Prefs

/**
 * 开机自启：系统启动完成后自动拉起剪贴板监听与悬浮按钮两个前台服务。
 * 可在设置中关闭；无障碍服务由系统自动恢复绑定，无需处理。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.autoStartOnBoot) return
        DebugLog.d("boot completed, starting services")
        runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, ClipboardMonitorService::class.java)
            )
            ContextCompat.startForegroundService(
                context, Intent(context, FloatingButtonService::class.java)
            )
        }
    }
}
