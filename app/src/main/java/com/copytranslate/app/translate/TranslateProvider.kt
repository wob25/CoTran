package com.copytranslate.app.translate

import com.copytranslate.app.App
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** 翻译结果实体 */
data class TranslateResult(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    /** 实际完成翻译的平台（多段可能混用，拼接显示） */
    val provider: String = ""
)

/** 文本超出某翻译源限制时抛出，TranslateManager 捕获后自动 fallback 到下一个源 */
class UnsupportedTextException(message: String) : Exception(message)

/**
 * 翻译源接口。
 */
interface TranslateProvider {
    val name: String

    /** 是否需要用户配置密钥 */
    val needsConfig: Boolean get() = false

    /** 密钥是否已配置（无需密钥的源恒为 true） */
    fun isConfigured(): Boolean = !needsConfig

    /**
     * 翻译。调用方（TranslateManager）保证运行在 IO 线程。
     * @param sourceLang 源语言代码（zh/en/ja/ko），null 或 "auto" 表示自动检测
     * @param targetLang 目标语言代码
     */
    suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult
}

/**
 * OkHttp 同步请求辅助：TranslateManager 已在 IO 协程中调用，这里直接同步执行。
 * 统一伪装浏览器 User-Agent；超时 10 秒在 App.okHttpClient 中配置。
 */
internal object Http {
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        exec(Request.Builder().url(url).get().apply {
            header("User-Agent", UA)
            headers.forEach { (k, v) -> header(k, v) }
        }.build())

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        exec(Request.Builder().url(url).post(body.toRequestBody(JSON_TYPE)).apply {
            header("User-Agent", UA)
            headers.forEach { (k, v) -> header(k, v) }
        }.build())

    private fun exec(request: Request): String {
        App.okHttpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${text.take(120)}")
            return text
        }
    }
}
