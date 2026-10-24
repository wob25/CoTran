package com.copytranslate.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.text.method.ScrollingMovementMethod
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.copytranslate.app.R
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.util.Prefs

/**
 * 悬浮翻译面板（底部弹出，窗口可聚焦以支持输入）：
 * - 右上角「隐藏」「×」：隐藏=收起悬浮按钮（回主界面重新显示），×=仅关闭面板
 * - 「内容」框：自动填入最新复制内容，可手动输入，长文本可上下滑动
 * - 「译文」框：显示翻译结果，长文本可上下滑动
 * - 右下角「复制译文」「翻译」
 * 所有方法均在主线程调用。
 */
class ResultOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: View? = null
    private var currentTranslation = ""

    /** 点「翻译」回调（由 FloatingButtonService 注入） */
    var onManualTranslate: ((String) -> Unit)? = null

    /** 点「隐藏」回调（由 FloatingButtonService 注入，收起悬浮按钮） */
    var onHideFloating: (() -> Unit)? = null

    /** 面板关闭回调（无论哪种方式），用于恢复悬浮按钮守护 */
    var onDismiss: (() -> Unit)? = null

    /** 点「刷新」回调（重新读取最新剪贴板/候选），由 FloatingButtonService 注入 */
    var onRefresh: (() -> String?)? = null

    /** 清除后自动翻译模式：第一个输入/粘贴的内容自动触发翻译 */
    private var autoTranslateArmed = false
    private val autoTranslateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoTranslateRunnable = Runnable {
        val input = root?.findViewById<EditText>(R.id.etOverlayInput) ?: return@Runnable
        val text = input.text.toString().trim()
        if (autoTranslateArmed && text.isNotEmpty()) {
            autoTranslateArmed = false
            onManualTranslate?.invoke(text)
        }
    }

    /** 延迟自动刷新：面板打开后多次尝试拉取最新内容（vivo 剪贴板释放有延迟），拿到新内容自动重翻 */
    private val refreshRunnable = Runnable {
        val input = root?.findViewById<EditText>(R.id.etOverlayInput) ?: return@Runnable
        val current = input.text.toString().trim()
        val latest = onRefresh?.invoke()
        if (!latest.isNullOrBlank() && latest != current) {
            input.setText(latest)
            input.setSelection(latest.length)
            onManualTranslate?.invoke(latest)
        }
    }

    /** 面板打开后调度 3 次延迟刷新（600ms/1800ms/3500ms） */
    fun scheduleAutoRefresh() {
        longArrayOf(600, 1800, 3500).forEach { delay ->
            autoTranslateHandler.postDelayed({
                if (root != null) refreshRunnable.run()
            }, delay)
        }
    }

    private fun cancelPendingCallbacks() {
        autoTranslateHandler.removeCallbacksAndMessages(null)
    }

    /**
     * 显示面板。prefill = 自动检测到的最新复制内容（也可手动输入）。
     * @return true 成功；无悬浮窗权限等异常返回 false
     */
    fun showPanel(prefill: String): Boolean {
        if (root != null) return true
        val themed = ContextThemeWrapper(context, R.style.Theme_CopyTranslate)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_result, null)

        val input = view.findViewById<EditText>(R.id.etOverlayInput)
        input.setText(prefill)
        // 光标移到末尾，方便继续编辑
        input.setSelection(input.text.length)

        // 清除：清空内容框与译文框，并武装"输入即自动翻译"
        view.findViewById<TextView>(R.id.tvOverlayClear).setOnClickListener {
            input.setText("")
            currentTranslation = ""
            view.findViewById<TextView>(R.id.tvOverlayResult).text = ""
            autoTranslateArmed = true
        }

        // 输入监听：武装状态下内容变化 → 防抖 0.8 秒后自动翻译（粘贴/输入都覆盖）
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!autoTranslateArmed) return
                autoTranslateHandler.removeCallbacks(autoTranslateRunnable)
                if (!s.isNullOrBlank()) {
                    autoTranslateHandler.postDelayed(autoTranslateRunnable, 800)
                }
            }
        })

        // 刷新：重新读取最新剪贴板/候选填入内容框
        view.findViewById<TextView>(R.id.tvOverlayRefresh).setOnClickListener {
            onRefresh?.invoke()?.let { latest ->
                input.setText(latest)
                input.setSelection(latest.length)
            }
        }

        view.findViewById<TextView>(R.id.tvOverlayHide).setOnClickListener {
            cancelPendingCallbacks()
            dismiss()
            onHideFloating?.invoke()
        }
        view.findViewById<TextView>(R.id.tvOverlayClose).setOnClickListener {
            cancelPendingCallbacks()
            dismiss()
        }
        view.findViewById<View>(R.id.btnOverlayTranslate).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) onManualTranslate?.invoke(text)
        }
        view.findViewById<View>(R.id.btnOverlayCopy).setOnClickListener {
            if (currentTranslation.isNotEmpty()) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("translation", currentTranslation))
                // 登记"已显示"，避免复制译文再次触发检测弹按钮
                com.copytranslate.app.util.ClipboardGate.markShown(currentTranslation)
                Prefs.lastCopiedText = currentTranslation
                Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.toast_no_result, Toast.LENGTH_SHORT).show()
            }
        }

        root = view
        return try {
            // 全屏透明窗口：顶部 15% 空白区点击关闭；底部 85% 为固定高度面板，
            // 内容框/译文框在布局中按权重固定等高，长文字在框内上下滑动
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }
            // 点击面板外的空白区 = 等同右上角 ×：取消待执行任务并关闭
            view.findViewById<View>(R.id.viewOverlayOutside).setOnClickListener {
                cancelPendingCallbacks()
                dismiss()
            }
            // 双保险：部分 ROM 的 ACTION_OUTSIDE 事件兜底
            view.setOnTouchListener { _, ev ->
                if (ev.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    cancelPendingCallbacks()
                    dismiss()
                    true
                } else false
            }
            windowManager.addView(view, lp)
            input.requestFocus()
            true
        } catch (e: Exception) {
            root = null
            false
        }
    }

    /** 译文框显示"翻译中…" */
    fun showTranslating() {
        root?.findViewById<TextView>(R.id.tvOverlayTargetLabel)?.text =
            context.getString(R.string.overlay_target_label)
        updateResultView(context.getString(R.string.overlay_translating), isError = false)
    }

    /** 显示翻译结果（译文标签带平台名，前缀用本地化字符串） */
    fun showResult(result: TranslateResult) {
        currentTranslation = result.translatedText
        val view = root
        if (view != null && result.provider.isNotBlank()) {
            view.findViewById<TextView>(R.id.tvOverlayTargetLabel).text =
                context.getString(R.string.manual_via, result.provider)
        }
        updateResultView(result.translatedText, isError = false)
    }

    /** 显示错误信息 */
    fun showError(message: String) {
        currentTranslation = ""
        updateResultView(message, isError = true)
    }

    private fun updateResultView(text: String, isError: Boolean) {
        val view = root ?: return
        val tv = view.findViewById<TextView>(R.id.tvOverlayResult)
        // 长文本可上下滑动
        tv.movementMethod = ScrollingMovementMethod()
        tv.text = text
        tv.setTextColor(
            if (isError) context.getColor(R.color.error_red)
            else context.getColor(R.color.text_primary)
        )
    }

    /** 收起面板 */
    fun dismiss() {
        cancelPendingCallbacks()
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
        onDismiss?.invoke()
    }
}
