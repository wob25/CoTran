package com.copytranslate.app.util

import android.content.Context
import com.copytranslate.app.service.FloatingButtonService

/**
 * 剪贴板文本统一过滤入口：
 * 空白 / 超长(>5000) / 纯数字 / 纯 URL / 与上次重复 / 近期已触发过 的文本直接忽略，
 * 通过过滤后记录并触发悬浮按钮显示。
 * 剪贴板监听服务与无障碍服务共用，保证行为一致。
 */
object ClipboardGate {

    /** 纯数字/符号，忽略 */
    private val NUMBER_ONLY = Regex("^[0-9\\s.,+-]+$")

    /** 纯 URL，忽略 */
    private val URL_ONLY = Regex("^(https?|ftp)://\\S+$")

    /** 近期已触发过的文本（防 ROM 冻结剪贴板导致的旧文本反复触发），上限 15 条 */
    private val shownTexts = LinkedHashSet<String>()

    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        Prefs.shownTextsJson.split('\u0001').forEach {
            if (it.isNotBlank()) shownTexts.add(it)
        }
    }

    private fun saveShown() {
        while (shownTexts.size > 15) {
            shownTexts.remove(shownTexts.first())
        }
        Prefs.shownTextsJson = shownTexts.joinToString("\u0001")
    }

    /** 手动路径（复制译文/手动翻译）也登记为已显示，避免检测通道重复触发 */
    fun markShown(text: String) {
        ensureLoaded()
        shownTexts.add(text)
        saveShown()
    }

    // 主动触发（选中/复制事件）的同文本防抖
    private var lastActiveText: String? = null
    private var lastActiveAt = 0L

    /** 最近一次主动触发（=lastCopiedText 更新）的时间戳，供面板取值比较新旧 */
    fun lastActiveTimeMs(): Long = lastActiveAt

    /**
     * 主动通道：用户选中文字 / 剪贴板变化监听触发。
     * "选中"本身就是明确意图，同一文本也允许再次弹出（测试场景：反复复制同一个词），
     * 仅做 3 秒同文本防抖，避免拖动选择时连续弹窗。
     */
    fun processActive(context: Context, text: String): Boolean {
        Prefs.lastDetectedAt = System.currentTimeMillis()
        Prefs.lastDetectedPreview = text.take(24)

        if (text.isBlank()) return false
        if (text.length > 5000) return false
        if (text.matches(NUMBER_ONLY)) return false
        if (text.matches(URL_ONLY)) return false
        if (text == lastActiveText && System.currentTimeMillis() - lastActiveAt < 3000) return false

        DebugLog.d("active pass: ${text.take(30)}")
        lastActiveText = text
        lastActiveAt = System.currentTimeMillis()
        Prefs.lastCopiedText = text
        FloatingButtonService.showButton(context)
        return true
    }

    /**
     * 被动通道：亮屏轮询读剪贴板。没有用户动作信号，
     * 必须严格去重（含近期已显示记录），否则 ROM 冻结剪贴板的旧内容会反复触发。
     */
    fun processClipboard(context: Context, text: String): Boolean {
        Prefs.lastDetectedAt = System.currentTimeMillis()
        Prefs.lastDetectedPreview = text.take(24)

        if (text.isBlank()) return false
        if (text.length > 5000) return false
        if (text.matches(NUMBER_ONLY)) return false
        if (text.matches(URL_ONLY)) return false
        if (text == Prefs.lastCopiedText) return false
        ensureLoaded()
        if (shownTexts.contains(text)) return false

        DebugLog.d("clipboard pass: ${text.take(30)}")
        shownTexts.add(text)
        saveShown()
        Prefs.lastCopiedText = text
        FloatingButtonService.showButton(context)
        return true
    }
}
