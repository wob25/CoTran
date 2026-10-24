package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.util.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * DeepL 翻译（需 Key；key 以 :fx 结尾用 api-free 域名，否则用 api 域名）。
 */
class DeepLProvider : TranslateProvider {

    override val name = "DeepL"
    override val needsConfig = true
    override fun isConfigured(): Boolean = Prefs.deeplKey.isNotBlank()

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val key = Prefs.deeplKey
        if (key.isBlank()) throw IOException("DeepL: key not configured")
        val host = if (key.endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"

        val payload = JSONObject()
            .put("text", JSONArray().put(text))
            .put("target_lang", targetCode(targetLang))
        if (!sourceLang.isNullOrEmpty() && sourceLang != "auto") {
            sourceCode(sourceLang)?.let { payload.put("source_lang", it) }
        }

        val resp = Http.post(
            "$host/v2/translate",
            payload.toString(),
            mapOf("Authorization" to "DeepL-Auth-Key $key")
        )
        val translated = JSONObject(resp)
            .getJSONArray("translations")
            .getJSONObject(0)
            .getString("text")
        return TranslateResult(text, translated, sourceLang ?: "auto", targetLang)
    }

    private fun targetCode(code: String): String = when (code) {
        "zh" -> "ZH"
        "en" -> "EN-US"
        "ja" -> "JA"
        "ko" -> "KO"
        else -> "EN-US"
    }

    private fun sourceCode(code: String): String? = when (code) {
        "zh" -> "ZH"
        "en" -> "EN"
        "ja" -> "JA"
        "ko" -> "KO"
        else -> null
    }
}
