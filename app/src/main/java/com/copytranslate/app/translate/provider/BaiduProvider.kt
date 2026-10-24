package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.util.Prefs
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 百度翻译（需 appid + secret，MD5 签名）。
 * from/to 用百度语言代码：zh/en/jp/kor。
 */
class BaiduProvider : TranslateProvider {

    override val name = "Baidu"
    override val needsConfig = true
    override fun isConfigured(): Boolean =
        Prefs.baiduAppId.isNotBlank() && Prefs.baiduSecret.isNotBlank()

    private fun fromCode(code: String): String = when (code) {
        "zh" -> "zh"
        "en" -> "en"
        "ja" -> "jp"
        "ko" -> "kor"
        else -> "auto"
    }

    private fun toCode(code: String): String = when (code) {
        "zh" -> "zh"
        "en" -> "en"
        "ja" -> "jp"
        "ko" -> "kor"
        else -> "en"
    }

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val appId = Prefs.baiduAppId
        val secret = Prefs.baiduSecret
        if (appId.isBlank() || secret.isBlank()) throw IOException("Baidu: not configured")

        val salt = (100000..999999).random().toString()
        val sign = md5(appId + text + salt + secret)
        val from = if (sourceLang.isNullOrEmpty() || sourceLang == "auto") "auto" else fromCode(sourceLang)
        val url = "https://fanyi-api.baidu.com/api/trans/vip/translate" +
                "?q=" + URLEncoder.encode(text, "UTF-8") +
                "&from=$from&to=${toCode(targetLang)}" +
                "&appid=" + URLEncoder.encode(appId, "UTF-8") +
                "&salt=$salt" +
                "&sign=" + URLEncoder.encode(sign, "UTF-8")

        val obj = JSONObject(Http.get(url))
        val errorCode = obj.optString("error_code", "")
        if (errorCode.isNotEmpty()) {
            throw IOException("Baidu $errorCode: ${obj.optString("error_msg")}")
        }
        val arr = obj.optJSONArray("trans_result") ?: throw IOException("Baidu: bad response")
        val dst = arr.getJSONObject(0).optString("dst")
        if (dst.isEmpty()) throw IOException("Baidu: empty result")
        return TranslateResult(text, dst, sourceLang ?: "auto", targetLang)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
