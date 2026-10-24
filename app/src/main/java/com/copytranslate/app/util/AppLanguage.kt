package com.copytranslate.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用内界面语言切换（仅影响界面文字，翻译功能不变）：
 * - 默认跟随系统语言（未手动选择时 applicationLocales 为空）
 * - 手动选择后通过 AndroidX per-app locale 持久生效，应用自动重建
 * - 68 种语言：67 个可选语言 + 跟随系统
 */
object AppLanguage {

    /** code to (国旗 emoji, 语言原生名称) */
    val LANGS: List<Triple<String, String, String>> = listOf(
        Triple("zh", "🇨🇳", "中文"),
        Triple("en", "🇬🇧", "English"),
        Triple("ja", "🇯🇵", "日本語"),
        Triple("ko", "🇰🇷", "한국어"),
        Triple("ru", "🇷🇺", "Русский"),
        Triple("fr", "🇫🇷", "Français"),
        Triple("de", "🇩🇪", "Deutsch"),
        Triple("es", "🇪🇸", "Español"),
        Triple("ar", "🇸🇦", "العربية"),
        Triple("hi", "🇮🇳", "हिन्दी"),
        Triple("th", "🇹🇭", "ไทย"),
        Triple("vi", "🇻🇳", "Tiếng Việt"),
        Triple("id", "🇮🇩", "Bahasa Indonesia"),
        Triple("tr", "🇹🇷", "Türkçe"),
        Triple("uk", "🇺🇦", "Українська"),
        Triple("pl", "🇵🇱", "Polski"),
        Triple("it", "🇮🇹", "Italiano"),
        Triple("pt", "🇵🇹", "Português"),
        Triple("nl", "🇳🇱", "Nederlands"),
        Triple("sv", "🇸🇪", "Svenska"),
        Triple("da", "🇩🇰", "Dansk"),
        Triple("no", "🇳🇴", "Norsk"),
        Triple("fi", "🇫🇮", "Suomi"),
        Triple("cs", "🇨🇿", "Čeština"),
        Triple("sk", "🇸🇰", "Slovenčina"),
        Triple("hu", "🇭🇺", "Magyar"),
        Triple("ro", "🇷🇴", "Română"),
        Triple("bg", "🇧🇬", "Български"),
        Triple("el", "🇬🇷", "Ελληνικά"),
        Triple("he", "🇮🇱", "עברית"),
        Triple("fa", "🇮🇷", "فارسی"),
        Triple("ur", "🇵🇰", "اردو"),
        Triple("bn", "🇧🇩", "বাংলা"),
        Triple("ta", "🇱🇰", "தமிழ்"),
        Triple("te", "🇮🇳", "తెలుగు"),
        Triple("mr", "🇮🇳", "मराठी"),
        Triple("gu", "🇮🇳", "ગુજરાતી"),
        Triple("kn", "🇮🇳", "ಕನ್ನಡ"),
        Triple("ml", "🇮🇳", "മലയാളം"),
        Triple("pa", "🇮🇳", "ਪੰਜਾਬੀ"),
        Triple("ms", "🇲🇾", "Bahasa Melayu"),
        Triple("tl", "🇵🇭", "Filipino"),
        Triple("af", "🇿🇦", "Afrikaans"),
        Triple("sq", "🇦🇱", "Shqip"),
        Triple("be", "🇧🇾", "Беларуская"),
        Triple("bs", "🇧🇦", "Bosanski"),
        Triple("hr", "🇭🇷", "Hrvatski"),
        Triple("ca", "🇦🇩", "Català"),
        Triple("eo", "🌐", "Esperanto"),
        Triple("et", "🇪🇪", "Eesti"),
        Triple("ka", "🇬🇪", "ქართული"),
        Triple("ht", "🇭🇹", "Kreyòl"),
        Triple("ha", "🇳🇬", "Hausa"),
        Triple("haw", "🌺", "ʻŌlelo Hawaiʻi"),
        Triple("is", "🇮🇸", "Íslenska"),
        Triple("ga", "🇮🇪", "Gaeilge"),
        Triple("kk", "🇰🇿", "Қазақша"),
        Triple("ky", "🇰🇬", "Кыргызча"),
        Triple("la", "🌐", "Latina"),
        Triple("lt", "🇱🇹", "Lietuvių"),
        Triple("lv", "🇱🇻", "Latviešu"),
        Triple("mk", "🇲🇰", "Македонски"),
        Triple("mt", "🇲🇹", "Malti"),
        Triple("mn", "🇲🇳", "Монгол"),
        Triple("ne", "🇳🇵", "नेपाली"),
        Triple("so", "🇸🇴", "Soomaali"),
        Triple("sw", "🇰🇪", "Kiswahili"),
        Triple("cy", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Cymraeg"),
        Triple("zu", "🇿🇦", "isiZulu")
    )

    /** 当前应用内语言标签；null = 跟随系统 */
    fun currentTag(): String? =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() }

    /** 系统语言的主代码（如 zh / en / ru）；用系统级 Resources，避免被应用内语言切换污染 */
    fun systemTag(): String =
        android.content.res.Resources.getSystem().configuration.locales[0]?.language ?: "en"

    /** 根据语言标签取展示用（国旗, 原生名称）；zh 与未知语言有兜底 */
    fun displayFor(tag: String?): Pair<String, String> {
        val code = tag ?: systemTag()
        val hit = LANGS.firstOrNull { it.first == code }
        if (hit != null) return hit.second to hit.third
        return if (code == "zh") "🇨🇳" to "中文" else "🌐" to code
    }

    /** 应用界面语言：null = 恢复跟随系统 */
    fun apply(tag: String?) {
        val locales = if (tag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * 给服务层 Context 套上应用内语言（悬浮弹窗、通知等不经过 Activity，
     * AppCompatDelegate 对其无效，需要手动应用配置）。
     * 跟随系统时返回原 Context。
     */
    fun wrap(context: Context): Context {
        val tag = currentTag() ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }
}
