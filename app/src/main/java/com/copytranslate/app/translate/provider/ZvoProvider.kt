package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * zvo.cn 免费翻译源：POST 空 body，参数放 query，from/to 必须用语言全名。
 */
class ZvoProvider : TranslateProvider {

    override val name = "zvo.cn"

    private fun codeToName(code: String?): String = when (code) {
        "zh" -> "chinese_simplified"
        "ja" -> "japanese"
        "ko" -> "korean"
        else -> "english"
    }

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val from = codeToName(sourceLang)
        val to = codeToName(targetLang)
        val url = "https://api.translate.zvo.cn/translate.json" +
                "?text=" + URLEncoder.encode(text, "UTF-8") +
                "&from=$from&to=$to"
        val resp = Http.post(url, "")
        val obj = JSONObject(resp)
        if (obj.optInt("result", 0) != 1) {
            throw IOException("zvo: " + obj.optString("info", "unknown error"))
        }
        val arr = obj.optJSONArray("text") ?: throw IOException("zvo: empty response")
        val translated = arr.optString(0)
        if (translated.isEmpty()) throw IOException("zvo: empty result")
        return TranslateResult(text, translated, sourceLang ?: "auto", targetLang)
    }
}
