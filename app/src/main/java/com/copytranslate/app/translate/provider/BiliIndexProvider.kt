package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.Http
import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * B站 Index-Translate 大模型翻译（默认推荐源，免费、接口自带语言识别）。
 * OpenAI chat/completions 格式。
 */
class BiliIndexProvider : TranslateProvider {

    override val name = "BiliIndex"

    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val langName = when (targetLang) {
            "zh" -> "中文"
            "en" -> "英文"
            "ja" -> "日文"
            "ko" -> "韩文"
            else -> "英文"
        }
        val payload = JSONObject()
            .put(
                "model", "Index-Translate-35B-A3B"
            )
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "将以下内容翻译为$langName，只输出译文：\n$text")
                )
            )
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))

        val resp = Http.post(
            "https://index-translate.bilibili.com/v1/chat/completions",
            payload.toString()
        )
        val content = JSONObject(resp)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        // 去掉可能的思考块 <think>...</think>
        val cleaned = content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (cleaned.isEmpty()) throw IOException("BiliIndex: empty result")
        // 该接口自动识别源语言
        return TranslateResult(text, cleaned, "auto", targetLang)
    }
}
