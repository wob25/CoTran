package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.translate.UnsupportedTextException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * MyMemory 免费翻译源：GET 接口，q 限 500 字节，超长抛 UnsupportedTextException 由上层 fallback。
 */
class MyMemoryProvider : TranslateProvider {

    override val name = "MyMemory"

    private fun mapCode(code: String): String = when (code) {
        "zh" -> "zh-CN"
        "ja" -> "ja"
        "ko" -> "ko"
        else -> "en"
    }

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        if (text.toByteArray(Charsets.UTF_8).size > 500) {
            throw UnsupportedTextException("MyMemory: text too long (max 500 bytes)")
        }
        val from = mapCode(sourceLang ?: "en")
        val to = mapCode(targetLang)
        val url = "https://api.mymemory.translated.net/get" +
                "?q=" + URLEncoder.encode(text, "UTF-8") +
                "&langpair=" + URLEncoder.encode("$from|$to", "UTF-8")
        val obj = JSONObject(Http.get(url))
        if (obj.optInt("responseStatus", 200) !in 200..299) {
            throw IOException("MyMemory: " + obj.optString("responseDetails", "request failed"))
        }
        val translated = obj.optJSONObject("responseData")
            ?.optString("translatedText")
            .orEmpty()
        if (translated.isEmpty()) throw IOException("MyMemory: empty result")
        return TranslateResult(text, translated, sourceLang ?: "auto", targetLang)
    }
}
