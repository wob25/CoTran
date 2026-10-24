package com.copytranslate.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.copytranslate.app.util.ClipboardGate

/**
 * 无障碍服务（Android 10+ 剪贴板检测核心）：
 * Android 10 起系统禁止普通应用后台读取剪贴板（前台服务也不行），
 * 但系统对"已开启的无障碍服务"豁免该限制。
 *
 * 检测策略（三通道）：
 * 1. 事件驱动：窗口切换 / 文本选择变化时立即检查（响应快）；
 * 2. 亮屏轮询：亮屏期间每 1s 轮询一次，锁屏自动暂停以省电；
 * 3. 划词兜底：部分 ROM 会把后台应用读到的剪贴板"冻结"为旧内容（回到前台才更新），
 *    因此同时捕获无障碍事件中的"选中文本"作为候选——选中新词立即触发，
 *    完全绕过剪贴板冻结问题。
 *
 * 需要用户在 系统设置 → 无障碍 → 复制翻译助手 中手动开启。
 */
class ClipboardAccessibilityService : AccessibilityService() {

    /** 服务内提示文案跟随应用内语言 */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.copytranslate.app.util.AppLanguage.wrap(newBase))
    }

    companion object {
        /** 供主界面判断无障碍服务是否已开启 */
        @Volatile
        var running = false
            private set

        /** 亮屏轮询间隔（毫秒） */
        private const val POLL_INTERVAL_MS = 1000L

        const val TAG = "CopyTranslate"

        /** 最近一次无障碍通道捕获的候选文本（划词/输入法面板），供悬浮面板取"最新内容" */
        @Volatile
        var latestCandidate: String? = null
            private set

        /** 候选文本的捕获时间戳 */
        @Volatile
        var latestCandidateAt: Long = 0L
            private set
    }

    /** 最近一次捕获的候选文本（指向 companion 的 latestCandidate），更新时同步时间戳 */
    private var lastSelectedText: String?
        get() = latestCandidate
        set(value) {
            latestCandidate = value
            latestCandidateAt = System.currentTimeMillis()
        }

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { checkClipboardNow() }

    /** 亮屏轮询循环 */
    private val pollRunnable = object : Runnable {
        override fun run() {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive == true) {
                checkClipboardNow()
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        // 自愈：无障碍绑定成功后自动拉起两个前台服务（重装/重启后无需手动点"开启服务"）
        runCatching {
            val i1 = Intent(this, ClipboardMonitorService::class.java)
            val i2 = Intent(this, FloatingButtonService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, i1)
            androidx.core.content.ContextCompat.startForegroundService(this, i2)
        }
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        handler.removeCallbacks(pollRunnable)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            // 切换应用/窗口、选中文本（长按复制后常伴随）时立即检查
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Fcitx5 剪贴板面板打开时，自动捕获面板中最新的剪贴板条目
                if (isFcitx(event.packageName?.toString())) captureFcitxPanel()
                checkClipboard()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                captureSelectedText(event)
                checkClipboard()
            }
            // 点击事件：捕获输入法（如 Fcitx5）剪贴板面板里点击的条目文本
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                captureClickedImeItem(event)
                checkClipboard()
            }
        }
    }

    private fun isFcitx(pkg: String?): Boolean = pkg?.contains("fcitx", true) == true

    /**
     * Fcitx5 剪贴板面板适配：面板打开时遍历其窗口节点树，
     * 捕获第一个"可点击且带文本"的节点（即最新一条剪贴板条目）作为候选。
     * Fcitx5 0.1.3 无对外 API，这是唯一可行的接入方式。
     */
    private fun captureFcitxPanel() {
        val root = rootInActiveWindow ?: return
        if (!isFcitx(root.packageName?.toString())) return
        val stack = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        stack.add(root)
        var candidate: String? = null
        var visited = 0
        while (stack.isNotEmpty() && visited < 120) {
            val n = stack.removeFirst()
            visited++
            val t = n.text?.toString()
            if (!t.isNullOrBlank() && n.isClickable && t.length in 2..500) {
                candidate = t
                break
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        if (candidate != null && candidate != lastSelectedText) {
            com.copytranslate.app.util.DebugLog.d("fcitx panel candidate: ${candidate.take(30)}")
            lastSelectedText = candidate
            checkClipboard()
        }
    }

    /**
     * 输入法窗口内的点击捕获：用户在 Fcitx5 等输入法的剪贴板面板中点击条目时，
     * 直接把条目文本作为候选文本（vivo 封锁后台剪贴板读取，此通道绕过该限制）。
     * 仅处理来自输入法包名的点击，避免误捕获普通应用的按钮点击。
     */
    private fun captureClickedImeItem(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val isIme = pkg.contains("fcitx", true) ||
            pkg.contains("inputmethod", true) ||
            pkg.contains("keyboard", true) ||
            pkg.endsWith(".ime")
        if (!isIme) return
        val node = event.source
        val text = node?.text?.toString()?.takeIf { it.isNotBlank() }
            ?: event.text?.joinToString(" ") { it.toString() }?.takeIf { it.isNotBlank() }
            ?: return
        com.copytranslate.app.util.DebugLog.d("ime item clicked: ${text.take(30)}")
        lastSelectedText = text
        checkClipboard()
    }

    /** 从选中事件中捕获选中文本（划词候选），绕过剪贴板冻结；取消选中时清除 */
    private fun captureSelectedText(event: AccessibilityEvent) {
        val node = event.source ?: run { com.copytranslate.app.util.DebugLog.d("selection: source=null"); return }
        val text = node.text?.toString() ?: run { com.copytranslate.app.util.DebugLog.d("selection: node.text=null"); return }
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        com.copytranslate.app.util.DebugLog.d("selection: start=$start end=$end text=${text.take(30)}")
        if (start in 0 until end && end <= text.length) {
            lastSelectedText = text.substring(start, end)
            com.copytranslate.app.util.DebugLog.d("selection captured: ${lastSelectedText?.take(30)}")
        } else if (start >= 0 && start == end) {
            // 光标归位 = 已取消选中，清掉划词候选避免旧值反复触发
            lastSelectedText = null
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** 防抖：事件可能高频触发，合并为一次剪贴板读取 */
    private fun checkClipboard() {
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, 150)
    }

    /** 最近一次读到的非空剪贴板内容，用于检测"内容变化"这一复制信号 */
    private var lastNonNullClip: String? = null

    private fun checkClipboardNow() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        com.copytranslate.app.util.DebugLog.d("read clip: ${clip?.take(30)}")

        if (clip != null) {
            val changed = clip != lastNonNullClip
            lastNonNullClip = clip
            if (changed) {
                // 剪贴板内容出现/变化 = 刚发生了复制，走主动通道（即使该文本之前弹过）
                com.copytranslate.app.util.DebugLog.d("clip changed -> active: ${clip.take(30)}")
                if (ClipboardGate.processActive(this, clip)) return
            } else if (ClipboardGate.processClipboard(this, clip)) {
                return
            }
        }

        // 划词兜底：选中文本属于用户主动动作，同文本也允许弹出（3秒防抖）
        val selected = lastSelectedText
        if (!selected.isNullOrBlank() && selected != clip) {
            com.copytranslate.app.util.DebugLog.d("selection candidate: ${selected.take(30)}")
            ClipboardGate.processActive(this, selected)
        }
    }
}
