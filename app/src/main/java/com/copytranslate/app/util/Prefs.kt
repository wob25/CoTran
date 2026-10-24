package com.copytranslate.app.util

import android.content.Context
import android.content.SharedPreferences
import com.copytranslate.app.App

/**
 * SharedPreferences 统一封装：
 * 翻译源选择、目标语言、自动隐藏秒数、各翻译源密钥、
 * 最近一次剪贴板文本、历史记录 JSON 字符串（由 HistoryRepository 读写）。
 */
object Prefs {
    private const val FILE = "copy_translate_prefs"

    // 翻译源 id
    const val PROVIDER_AUTO = "auto"
    const val PROVIDER_BILI = "bili"
    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_ZVO = "zvo"
    const val PROVIDER_MYMEMORY = "mymemory"
    const val PROVIDER_MLKIT = "mlkit"
    const val PROVIDER_MICROSOFT = "microsoft"
    const val PROVIDER_GCLOUD = "gcloud"
    const val PROVIDER_BAIDU = "baidu"
    const val PROVIDER_DEEPL = "deepl"

    // 语言代码
    const val LANG_AUTO = "auto"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"
    const val LANG_JA = "ja"
    const val LANG_KO = "ko"

    private val sp: SharedPreferences
        get() = App.instance.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 翻译源选择，默认离线翻译 */
    var provider: String
        get() = sp.getString(KEY_PROVIDER, PROVIDER_MLKIT) ?: PROVIDER_MLKIT
        set(value) = sp.edit().putString(KEY_PROVIDER, value).apply()

    /** 目标语言，默认自动（中英互译） */
    var targetLang: String
        get() = sp.getString(KEY_TARGET_LANG, LANG_AUTO) ?: LANG_AUTO
        set(value) = sp.edit().putString(KEY_TARGET_LANG, value).apply()

    /** 面板手动翻译的源语言（auto=自动检测），用于手动指定离线语言包避免识别不准 */
    var manualSourceLang: String
        get() = sp.getString(KEY_MANUAL_SRC, LANG_AUTO) ?: LANG_AUTO
        set(value) = sp.edit().putString(KEY_MANUAL_SRC, value).apply()

    /** 悬浮按钮背景透明度（10-100 百分比，默认 80） */
    var floatingOpacity: Int
        get() = sp.getInt(KEY_FLOATING_OPACITY, 80)
        set(value) = sp.edit().putInt(KEY_FLOATING_OPACITY, value).apply()

    /** 悬浮按钮自动隐藏秒数，0 表示永不隐藏，默认 5 秒 */
    var autoHideSeconds: Int
        get() = sp.getInt(KEY_AUTO_HIDE, 5)
        set(value) = sp.edit().putInt(KEY_AUTO_HIDE, value).apply()

    /** 微软翻译密钥 */
    var microsoftKey: String
        get() = sp.getString(KEY_MS_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_MS_KEY, value).apply()

    /** 微软服务区域（如 global） */
    var microsoftRegion: String
        get() = sp.getString(KEY_MS_REGION, "global") ?: "global"
        set(value) = sp.edit().putString(KEY_MS_REGION, value).apply()

    /** 谷歌云翻译 API Key（v2 接口） */
    var googleCloudKey: String
        get() = sp.getString(KEY_GCLOUD_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_GCLOUD_KEY, value).apply()

    /** 百度翻译 APP ID */
    var baiduAppId: String
        get() = sp.getString(KEY_BAIDU_APPID, "") ?: ""
        set(value) = sp.edit().putString(KEY_BAIDU_APPID, value).apply()

    /** 百度翻译密钥 */
    var baiduSecret: String
        get() = sp.getString(KEY_BAIDU_SECRET, "") ?: ""
        set(value) = sp.edit().putString(KEY_BAIDU_SECRET, value).apply()

    /** DeepL API Key（:fx 结尾为免费 key） */
    var deeplKey: String
        get() = sp.getString(KEY_DEEPL_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_DEEPL_KEY, value).apply()

    /** 最近一次通过过滤的剪贴板文本（用于去重与点击翻译） */
    var lastCopiedText: String
        get() = sp.getString(KEY_LAST_COPIED, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_COPIED, value).apply()

    /** [诊断] 最近一次成功读取到剪贴板的时间戳（0=从未读到），用于自检后台检测是否工作 */
    var lastDetectedAt: Long
        get() = sp.getLong(KEY_LAST_DETECTED_AT, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_DETECTED_AT, value).apply()

    /** [诊断] 最近一次读到的剪贴板内容预览 */
    var lastDetectedPreview: String
        get() = sp.getString(KEY_LAST_DETECTED_PREVIEW, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_DETECTED_PREVIEW, value).apply()

    /** 已触发过悬浮按钮的文本记录（\u0001 分隔），防止旧文本反复触发 */
    var shownTextsJson: String
        get() = sp.getString(KEY_SHOWN_TEXTS, "") ?: ""
        set(value) = sp.edit().putString(KEY_SHOWN_TEXTS, value).apply()

    /** 悬浮按钮尺寸（dp），滑块自定义 24-64，默认 28 */
    var buttonSizeDp: Int
        get() = sp.getInt(KEY_BUTTON_SIZE, 28)
        set(value) = sp.edit().putInt(KEY_BUTTON_SIZE, value).apply()

    /** 开机自动启动服务，默认开 */
    var autoStartOnBoot: Boolean
        get() = sp.getBoolean(KEY_AUTO_BOOT, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_BOOT, value).apply()

    /** 调试日志开关，默认关 */
    var debugLog: Boolean
        get() = sp.getBoolean(KEY_DEBUG_LOG, false)
        set(value) = sp.edit().putBoolean(KEY_DEBUG_LOG, value).apply()

    /** 悬浮按钮是否被用户隐藏（隐藏后回主界面重新显示） */
    var floatingHidden: Boolean
        get() = sp.getBoolean(KEY_FLOATING_HIDDEN, false)
        set(value) = sp.edit().putBoolean(KEY_FLOATING_HIDDEN, value).apply()

    /** 历史记录 JSON 数组字符串 */
    var historyJson: String
        get() = sp.getString(KEY_HISTORY, "[]") ?: "[]"
        set(value) = sp.edit().putString(KEY_HISTORY, value).apply()

    private const val KEY_PROVIDER = "provider"
    private const val KEY_TARGET_LANG = "target_lang"
    private const val KEY_MANUAL_SRC = "manual_source_lang"
    private const val KEY_FLOATING_OPACITY = "floating_opacity"
    private const val KEY_AUTO_HIDE = "auto_hide_seconds"
    private const val KEY_MS_KEY = "microsoft_key"
    private const val KEY_MS_REGION = "microsoft_region"
    private const val KEY_GCLOUD_KEY = "google_cloud_key"
    private const val KEY_BAIDU_APPID = "baidu_app_id"
    private const val KEY_BAIDU_SECRET = "baidu_secret"
    private const val KEY_DEEPL_KEY = "deepl_key"
    private const val KEY_LAST_COPIED = "last_copied_text"
    private const val KEY_LAST_DETECTED_AT = "last_detected_at"
    private const val KEY_LAST_DETECTED_PREVIEW = "last_detected_preview"
    private const val KEY_SHOWN_TEXTS = "shown_texts"
    private const val KEY_BUTTON_SIZE = "button_size_dp"
    private const val KEY_AUTO_BOOT = "auto_start_on_boot"
    private const val KEY_DEBUG_LOG = "debug_log"
    private const val KEY_FLOATING_HIDDEN = "floating_hidden"
    private const val KEY_HISTORY = "history_json"
}
