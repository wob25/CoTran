package com.copytranslate.app.translate.provider

import com.copytranslate.app.translate.TranslateProvider
import com.copytranslate.app.translate.TranslateResult
import com.copytranslate.app.translate.UnsupportedTextException
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 完全离线的端上翻译（Google ML Kit）：
 * - 文本不离开设备，隐私最佳（与 Google 翻译离线模式同款模型）
 * - 首次翻译某语言对时自动下载语言模型（约 30MB，仅此一次需要联网）
 * - 50+ 语言；质量适合日常场景
 */
class OfflineMlKitProvider : TranslateProvider {

    override val name = "MLKit"

    /** 确保语言模型已下载后执行端上翻译 */
    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslateResult {
        val src = mapLang(sourceLang ?: "en")
        val tgt = mapLang(targetLang)
        if (src == tgt) throw UnsupportedTextException("same language")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(tgt)
            .build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded()
                .await("download model $src->$tgt")
            val translated = translator.translate(text).await("translate")
            return TranslateResult(text, translated, src, tgt)
        } finally {
            translator.close()
        }
    }

    /** 语言码 → ML Kit 语言（自动覆盖全部支持语言，未知码回退英语） */
    private fun mapLang(code: String): String =
        TranslateLanguage.fromLanguageTag(code) ?: TranslateLanguage.ENGLISH

    /** Task<T> → suspend 的轻量包装（避免引入 coroutines-play-services 依赖） */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(what: String): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            addOnCanceledListener { if (cont.isActive) cont.resumeWithException(
                UnsupportedTextException("cancelled: $what")) }
        }
}
