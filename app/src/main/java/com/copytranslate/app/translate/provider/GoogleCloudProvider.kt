package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.util.Prefs
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * 谷歌云翻译官方接口（Cloud Translation API v2，需自备 API Key）。
 * 每月 $10 免费额度 ≈ 50 万字符，超出 $20/百万字符。
 * v3 需 OAuth Bearer Token（service account 签名，手机端不现实），
 * 故采用同样计费的 v2 REST + ?key= 简单鉴权。
 */
class GoogleCloudProvider : TranslateProvider {

    override val name = "GoogleCloud"
    override val needsConfig = true
    override fun isConfigured(): Boolean = Prefs.googleCloudKey.isNotBlank()

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val key = Prefs.googleCloudKey
        if (key.isBlank()) throw IOException("GoogleCloud: key not configured")

        // v2 目标语言码：zh 需要显式区分为 zh-CN（简体）
        val tgt = if (targetLang == "zh") "zh-CN" else targetLang
        val url = "https://translation.googleapis.com/language/translate/v2?key=" +
            URLEncoder.encode(key, "UTF-8")
        val body = JSONObject().apply {
            put("q", text)
            put("format", "text")
            put("target", tgt)
            if (!sourceLang.isNullOrEmpty() && sourceLang != "auto") put("source", sourceLang)
        }.toString()

        val resp = Http.post(url, body, mapOf("Content-Type" to "application/json; charset=utf-8"))
        val translated = JSONObject(resp)
            .getJSONObject("data")
            .getJSONArray("translations")
            .getJSONObject(0)
            .getString("translatedText")
        // 防御性还原常见 HTML 实体（v2 偶尔会转义）
        val clean = translated
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        return TranslateResult(text, clean, sourceLang ?: "auto", targetLang)
    }
}
