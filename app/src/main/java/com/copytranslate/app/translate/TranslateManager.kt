package com.copytranslate.app.translate

import com.copytranslate.app.App
import com.copytranslate.app.R
import com.copytranslate.app.db.HistoryRepository
import com.copytranslate.app.db.TranslationHistory
import com.copytranslate.app.translate.provider.BaiduProvider
import com.copytranslate.app.translate.provider.BiliIndexProvider
import com.copytranslate.app.translate.provider.DeepLProvider
import com.copytranslate.app.translate.provider.GoogleCloudProvider
import com.copytranslate.app.translate.provider.GoogleGtxProvider
import com.copytranslate.app.translate.provider.MicrosoftProvider
import com.copytranslate.app.translate.provider.MyMemoryProvider
import com.copytranslate.app.translate.provider.OfflineMlKitProvider
import com.copytranslate.app.translate.provider.ZvoProvider
import com.copytranslate.app.util.LanguageDetector
import com.copytranslate.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 翻译统一入口（单例）：
 * 1. 根据设置选择翻译源，选定源失败后自动按免费源顺序 fallback；
 * 2. 选了需要密钥的源但未配置密钥时，直接走免费顺序并通知上层提示；
 * 3. 源语言==目标语言时自动互换目标语言（中→英，其他→中）；
 * 4. 长文本自动分段（按段落/句子切，每段独立翻译后拼接）；
 * 5. 成功后写入历史记录。
 */
object TranslateManager {

    private val bili = BiliIndexProvider()
    private val google = GoogleGtxProvider()
    private val zvo = ZvoProvider()
    private val myMemory = MyMemoryProvider()
    private val mlkit = OfflineMlKitProvider()
    private val microsoft = MicrosoftProvider()
    private val googleCloud = GoogleCloudProvider()
    private val baidu = BaiduProvider()
    private val deepl = DeepLProvider()

    /** 免费源默认 fallback 顺序 */
    private val freeChain: List<TranslateProvider> by lazy {
        listOf(bili, google, zvo, myMemory)
    }

    /** 长文本分段上限（字符） */
    private const val CHUNK_LIMIT = 800

    fun providerById(id: String): TranslateProvider? = when (id) {
        Prefs.PROVIDER_BILI -> bili
        Prefs.PROVIDER_GOOGLE -> google
        Prefs.PROVIDER_ZVO -> zvo
        Prefs.PROVIDER_MYMEMORY -> myMemory
        Prefs.PROVIDER_MLKIT -> mlkit
        Prefs.PROVIDER_MICROSOFT -> microsoft
        Prefs.PROVIDER_GCLOUD -> googleCloud
        Prefs.PROVIDER_BAIDU -> baidu
        Prefs.PROVIDER_DEEPL -> deepl
        else -> null
    }

    /**
     * @param targetOverride 指定目标语言（反向翻译用），null 则按设置
     * @param sourceOverride 指定源语言（手动选择语言包用，避免自动识别不准），null/auto 则自动检测
     * @param onNotice 需要提示给用户的信息（如"该源未配置密钥"），在后台线程回调
     */
    suspend fun translate(
        text: String,
        targetOverride: String? = null,
        sourceOverride: String? = null,
        onNotice: (String) -> Unit = {}
    ): Result<TranslateResult> =
        withContext(Dispatchers.IO) {
            // 组装尝试链：选定源优先，其后按免费顺序 fallback
            val selected = providerById(Prefs.provider)
            val baseChain: List<TranslateProvider> = when {
                selected == null -> freeChain
                selected.needsConfig && !selected.isConfigured() -> {
                    onNotice(App.instance.getString(R.string.toast_no_key))
                    freeChain
                }
                freeChain.contains(selected) ->
                    listOf(selected) + freeChain.filter { it !== selected }
                else -> listOf(selected) + freeChain
            }

            // 语言处理：源语言可手动指定（避免识别不准）；目标为 auto 或与源相同时自动互换
            val src = sourceOverride?.takeIf { it != Prefs.LANG_AUTO } ?: LanguageDetector.detect(text)
            var target = targetOverride ?: Prefs.targetLang
            if (target == Prefs.LANG_AUTO || target == src) {
                target = if (src == Prefs.LANG_ZH) Prefs.LANG_EN else Prefs.LANG_ZH
            }

            // 长文本分段翻译，成功的源在后续段落优先复用
            var preferred: TranslateProvider? = null
            val parts = mutableListOf<String>()
            val usedProviders = linkedSetOf<String>()
            var lastError: Throwable? = null

            for (chunk in splitChunks(text)) {
                val ordered = if (preferred != null) {
                    listOf(preferred!!) + baseChain.filter { it !== preferred }
                } else baseChain

                var ok: TranslateResult? = null
                for (p in ordered) {
                    try {
                        // B站接口自带语言识别，固定传 auto
                        val srcForProvider = if (p is BiliIndexProvider) Prefs.LANG_AUTO else src
                        ok = p.translate(chunk, srcForProvider, target)
                        preferred = p
                        usedProviders.add(p.name)
                        break
                    } catch (e: Throwable) {
                        lastError = e
                    }
                }
                if (ok == null) {
                    return@withContext Result.failure(
                        lastError ?: IOException("translate failed")
                    )
                }
                parts.add(ok.translatedText)
            }

            val result = TranslateResult(
                sourceText = text,
                translatedText = parts.joinToString("\n"),
                sourceLanguage = src,
                targetLanguage = target,
                provider = usedProviders.joinToString("+")
            )
            HistoryRepository.add(
                TranslationHistory(
                    id = System.currentTimeMillis(),
                    sourceText = text,
                    translatedText = result.translatedText,
                    fromLang = result.sourceLanguage,
                    toLang = result.targetLanguage,
                    createTime = System.currentTimeMillis()
                )
            )
            Result.success(result)
        }

    /**
     * 长文本切分：优先按段落，段内按句子（。！？.!?；;），单句超长则硬切。
     * 保证每段不超过 CHUNK_LIMIT。
     */
    internal fun splitChunks(text: String, limit: Int = CHUNK_LIMIT): List<String> {
        if (text.length <= limit) return listOf(text)

        val chunks = mutableListOf<String>()
        val sb = StringBuilder()

        fun flush() {
            val s = sb.toString().trimEnd('\n')
            if (s.isNotBlank()) chunks.add(s)
            sb.clear()
        }

        fun hardSplit(s: String) {
            var i = 0
            while (i < s.length) {
                val end = minOf(i + limit, s.length)
                val piece = s.substring(i, end)
                sb.append(piece)
                if (sb.length >= limit) flush()
                i = end
            }
        }

        for (para in text.split("\n")) {
            if (para.isBlank()) {
                sb.append("\n")
                continue
            }
            if (sb.length + para.length + 1 > limit) flush()
            if (para.length > limit) {
                // 段内按句子切分
                val sent = StringBuilder()
                for (c in para) {
                    sent.append(c)
                    if (c in "。！？.!?；;") {
                        if (sb.length + sent.length > limit) flush()
                        if (sent.length > limit) {
                            hardSplit(sent.toString())
                            sent.clear()
                        } else {
                            sb.append(sent)
                            sent.clear()
                            if (sb.length >= limit) flush()
                        }
                    }
                }
                if (sent.isNotBlank()) {
                    if (sent.length > limit) {
                        hardSplit(sent.toString())
                    } else {
                        sb.append(sent)
                    }
                }
            } else {
                sb.append(para).append("\n")
            }
        }
        flush()
        return chunks.filter { it.isNotBlank() }.ifEmpty { listOf(text) }
    }
}
