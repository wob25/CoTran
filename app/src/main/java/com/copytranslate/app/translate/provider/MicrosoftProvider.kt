package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.util.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * 微软 Translator（需 Key + Region，在设置里配置）。
 * from 可省略由服务端自动检测。
 */
class MicrosoftProvider : TranslateProvider {

    override val name = "Microsoft"
    override val needsConfig = true
    override fun isConfigured(): Boolean = Prefs.microsoftKey.isNotBlank()

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val key = Prefs.microsoftKey
        val region = Prefs.microsoftRegion
        if (key.isBlank()) throw IOException("Microsoft: key not configured")

        val url = buildString {
            append("https://api.cognitive.microsofttranslator.com/translate")
            append("?api-version=3.0&to=").append(URLEncoder.encode(targetLang, "UTF-8"))
            if (!sourceLang.isNullOrEmpty() && sourceLang != "auto") {
                append("&from=").append(URLEncoder.encode(sourceLang, "UTF-8"))
            }
        }
        val body = JSONArray().put(JSONObject().put("Text", text)).toString()
        val resp = Http.post(
            url, body,
            mapOf(
                "Ocp-Apim-Subscription-Key" to key,
                "Ocp-Apim-Subscription-Region" to region
            )
        )
        val translated = JSONArray(resp)
            .getJSONObject(0)
            .getJSONArray("translations")
            .getJSONObject(0)
            .getString("text")
        return TranslateResult(text, translated, sourceLang ?: "auto", targetLang)
    }
}
