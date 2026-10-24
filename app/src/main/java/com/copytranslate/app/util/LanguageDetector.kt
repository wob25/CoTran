package com.copytranslate.app.util

/**
 * 简单语言检测：按 韩文 > 日文 > 中文 > 英文 顺序启发式判断。
 */
object LanguageDetector {

    /** 韩文谚文 */
    private val HANGUL = Regex("[\uAC00-\uD7AF]")

    /** 日文假名（平/片假名） */
    private val KANA = Regex("[\u3040-\u30FF]")

    /** 中日韩统一区（含假名与谚文） */
    private val CJK = Regex("[\u4E00-\u9FFF\u3040-\u30FF\uAC00-\uD7AF]")

    /** 是否包含中/日/韩字符 */
    fun containsCJK(text: String): Boolean = CJK.containsMatchIn(text)

    /**
     * 检测语言代码：ko / ja / zh / en
     */
    fun detect(text: String): String {
        if (HANGUL.containsMatchIn(text)) return Prefs.LANG_KO
        if (KANA.containsMatchIn(text)) return Prefs.LANG_JA
        if (CJK.containsMatchIn(text)) return Prefs.LANG_ZH
        return Prefs.LANG_EN
    }
}
