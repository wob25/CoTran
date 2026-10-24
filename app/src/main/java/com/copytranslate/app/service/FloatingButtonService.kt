package com.copytranslate.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.copytranslate.app.R
import com.copytranslate.app.service.ClipboardAccessibilityService
import com.copytranslate.app.translate.TranslateManager
import com.copytranslate.app.ui.ResultOverlay
import com.copytranslate.app.util.ClipboardGate
import com.copytranslate.app.util.LanguageDetector
import com.copytranslate.app.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 悬浮"译"字按钮前台服务：
 * WindowManager + TYPE_APPLICATION_OVERLAY，位于右上角（距顶 200dp、距右 16dp），
 * 可拖动；显示 N 秒自动隐藏（N 从设置读取，0=永不）；点击后隐藏按钮并翻译最近剪贴板文本。
 */
class FloatingButtonService : Service() {

    /** 悬浮弹窗/通知跟随应用内语言（Activity 之外的 Context 需手动包装） */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.copytranslate.app.util.AppLanguage.wrap(newBase))
    }

    companion object {
        const val NOTIF_ID = 1002

        @Volatile
        private var instance: FloatingButtonService? = null

        /** 供设置页调用：按新尺寸重建悬浮按钮，即时生效 */
        fun refreshButton() {
            val inst = instance ?: return
            inst.handler.post {
                runCatching {
                    inst.button?.let { tv ->
                        if (tv.isAttachedToWindow) inst.windowManager.removeView(tv)
                    }
                }
                inst.setupButton()
                if (!Prefs.floatingHidden) inst.show()
            }
        }

        /** 供剪贴板服务调用：服务已运行则直接显示按钮，否则拉起服务 */
        fun showButton(context: Context) {
            if (Prefs.floatingHidden) return // 用户已选择隐藏，尊重设置
            val inst = instance
            if (inst != null) {
                inst.show()
            } else {
                // 从后台（无障碍回调）拉起可能受 Android 12+ 前台服务限制，失败则忽略，
                // 正常情况下服务在主界面开启后常驻，不会走到这里
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, FloatingButtonService::class.java)
                    )
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var button: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resultOverlay: ResultOverlay? = null
    private var toast: Toast? = null
    private var touchSlop = 0

    // 拖动状态
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var downTime = 0L

    private val hideRunnable = Runnable { hide() }

    /** 面板是否打开中：打开期间暂停按钮守护，避免按钮叠在面板上 */
    @Volatile
    private var panelOpen = false

    /** 常驻守护：防止 ROM 在应用退后台后移除悬浮窗 */
    private val reattachRunnable = object : Runnable {
        override fun run() {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm?.isInteractive == true && !Prefs.floatingHidden && !panelOpen) {
                val tv = button
                if (tv != null && !tv.isAttachedToWindow) show()
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        startForegroundWithNotification()
        setupButton()
        // 悬浮按钮常驻显示（除非用户点了"隐藏"）
        if (!Prefs.floatingHidden) show()
        // 常驻守护：亮屏期间每 2 秒检查一次，被系统移除（如 vivo 后台管控）立即重挂
        handler.postDelayed(reattachRunnable, 2000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        resultOverlay?.dismiss()
        resultOverlay = null
        hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 创建悬浮"译"字按钮与窗口参数（尺寸从设置读取，不立即添加到窗口） */
    private fun setupButton() {
        val density = resources.displayMetrics.density
        val size = (Prefs.buttonSizeDp * density).toInt()

        val tv = TextView(this)
        tv.text = getString(R.string.floating_button_text)
        tv.setTextColor(0xF2FFFFFF.toInt())
        tv.textSize = Prefs.buttonSizeDp * 0.36f
        tv.gravity = Gravity.CENTER
        // 背景渐变按透明度滑块实时重绘（XML 固定色 alpha=E0 会导致拖动变化不明显）
        val pct = Prefs.floatingOpacity.coerceIn(10, 100)
        val a = pct * 255 / 100
        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf((a shl 24) or 0x363644, (a shl 24) or 0x1C1C24)
        )
        gd.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        gd.cornerRadius = 21 * density
        gd.setStroke(
            (0.8f * density).toInt().coerceAtLeast(1),
            ((a * 0x40 / 255) shl 24) or 0xFFFFFF
        )
        tv.background = gd
        // 文字保持不透明，仅背景可调透明度
        tv.setTextColor(0xF2FFFFFF.toInt())
        tv.elevation = 3 * density

        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()    // 距右侧 16dp（gravity=END 时 x 即距右缘距离）
            y = (200 * density).toInt()   // 距顶部 200dp
        }

        // 拖动 + 点击：移动距离超过 touchSlop 视为拖动，否则视为点击
        tv.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (moved || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        val maxX = (resources.displayMetrics.widthPixels - params.width).toFloat()
                        val maxY = (resources.displayMetrics.heightPixels - params.height).toFloat()
                        // gravity=END：x 为距右缘距离，向右拖（dx>0）时 x 减小
                        params.x = clamp(startX - dx, 0f, maxX).toInt()
                        params.y = clamp(startY + dy, 0f, maxY).toInt()
                        runCatching { windowManager.updateViewLayout(tv, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onButtonClicked()
                    tv.performClick()
                    true
                }
                else -> false
            }
        }
        button = tv
    }

    private fun clamp(v: Float, min: Float, max: Float): Float =
        if (v < min) min else if (v > max) max else v

    /** 显示悬浮按钮（常驻，不自动隐藏） */
    private fun show() {
        val tv = button ?: return
        if (Prefs.floatingHidden) return
        // 用真实挂载状态判断：vivo 等系统会在应用退后台后强制移除悬浮窗，
        // 此时 view.windowToken 仍是旧值，必须用 isAttachedToWindow 并强制重挂
        if (!tv.isAttachedToWindow) {
            runCatching { if (tv.windowToken != null) windowManager.removeViewImmediate(tv) }
            try {
                windowManager.addView(tv, params)
                com.copytranslate.app.util.DebugLog.d("button shown")
            } catch (e: Exception) {
                com.copytranslate.app.util.DebugLog.e("button show failed", e)
                showToast(getString(R.string.need_overlay_permission))
                return
            }
        }
    }

    /** 隐藏悬浮按钮（服务保持运行，便于下次快速显示） */
    private fun hide() {
        handler.removeCallbacks(hideRunnable)
        val tv = button ?: return
        if (tv.windowToken != null) {
            runCatching { windowManager.removeView(tv) }
        }
    }

    /** 点击按钮：弹出翻译面板，内容按"无障碍候选(较新) → 剪贴板变化 → 上次文本"自动填入 */
    private fun onButtonClicked() {
        hide()
        val prefill = resolveLatest() ?: ""

        val overlay = ResultOverlay(this)
        resultOverlay = overlay
        panelOpen = true
        overlay.onManualTranslate = { text -> translateInPanel(overlay, text) }
        overlay.onHideFloating = {
            Prefs.floatingHidden = true
            panelOpen = false
            hide()
            showToast(getString(R.string.toast_float_hidden))
        }
        overlay.onDismiss = {
            panelOpen = false
            if (!Prefs.floatingHidden) show()
        }
        overlay.onRefresh = { resolveLatest() }
        if (!overlay.showPanel(prefill)) {
            showToast(getString(R.string.need_overlay_permission))
            return
        }
        // 打开面板即自动翻译当前内容；延迟刷新拿到更新内容后会自动重翻
        overlay.scheduleAutoRefresh()
        if (prefill.isNotBlank()) {
            translateInPanel(overlay, prefill)
        }
    }

    /** 解析"最新内容"：无障碍候选（较新优先）→ 剪贴板 → 上次文本。供面板刷新使用 */
    private fun resolveLatest(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val fresh = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        val a11y = ClipboardAccessibilityService.latestCandidate
        return when {
            !a11y.isNullOrBlank() &&
                ClipboardAccessibilityService.latestCandidateAt > ClipboardGate.lastActiveTimeMs() -> a11y
            !fresh.isNullOrBlank() && fresh != Prefs.lastCopiedText -> fresh
            !a11y.isNullOrBlank() -> a11y
            else -> Prefs.lastCopiedText.ifBlank { null }
        }
    }

    /** 面板内点击「翻译」：显示"翻译中…"→ 请求翻译 → 结果/错误显示在译文框 */
    private fun translateInPanel(overlay: ResultOverlay, text: String) {
        com.copytranslate.app.util.ClipboardGate.markShown(text)
        Prefs.lastCopiedText = text
        overlay.showTranslating()
        scope.launch {
            // 源语言：面板内手动选择的源语言优先（避免自动识别不准），auto 则自动检测
            val result = TranslateManager.translate(
                text,
                sourceOverride = Prefs.manualSourceLang
            ) { notice -> showToast(notice) }
            result.onSuccess { overlay.showResult(it) }
                .onFailure { overlay.showError(getString(R.string.translate_failed, it.message ?: "")) }
        }
    }

    private fun showToast(msg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toast?.cancel()
            toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT).also { it.show() }
        } else {
            handler.post { showToast(msg) }
        }
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ClipboardMonitorService.CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        val notification = buildNotification(getString(R.string.notif_floating_text))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, ClipboardMonitorService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(getString(R.string.notif_floating_title))
            .setContentText(text)
            .setOngoing(true)
            .build()
}
