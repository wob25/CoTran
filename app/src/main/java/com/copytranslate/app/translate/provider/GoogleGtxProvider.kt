package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.translate.UnsupportedTextException
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder

/**
 * 谷歌 translate_a/single（gtx 客户端，免费，支持 auto）。
 * 响应为嵌套 JSON 数组：第 0 个元素是分段结果数组，第 2 个元素是检测到的源语言。
 */
class GoogleGtxProvider : TranslateProvider {

    override val name = "Google"

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        if (text.length > 1500) {
            throw UnsupportedTextException("Google: text too long (max 1500 chars)")
        }
        val sl = if (sourceLang.isNullOrEmpty()) "auto" else sourceLang
        val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$sl&tl=$targetLang&dt=t" +
                "&q=" + URLEncoder.encode(text, "UTF-8")
        val data = JSONArray(Http.get(url))
        val segments = data.optJSONArray(0) ?: throw IOException("Google: bad response")
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            sb.append(seg.optString(0))
        }
        val translated = sb.toString().trim()
        if (translated.isEmpty()) throw IOException("Google: empty result")
        val detected = (data.opt(2) as? String) ?: sl
        return TranslateResult(text, translated, detected, targetLang)
    }
}
