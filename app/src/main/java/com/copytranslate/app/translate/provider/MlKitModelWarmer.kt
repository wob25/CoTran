package com.copytranslate.app.translate.provider

import android.content.Context
import com.copytranslate.app.translate.UnsupportedTextException
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 模型预热（装上即离线可用）：
 * 1. 优先播种 APK 内置模型（assets/mlkit_models/en_zh/，43MB，中英双向），
 *    同时写入 ML Kit 的模型注册标记（shared_prefs 里的 current_model_hash），
 *    用户安装后无需联网即可离线翻译；
 * 2. 播种缺失/失败时自动联网下载兜底（downloadModelIfNeeded 幂等）。
 * 其余语言方向不在内置范围，仍按需在线下载。
 */
object MlKitModelWarmer {

    /** 内置模型所在 assets 根目录 */
    private const val ASSET_DIR = "mlkit_models"

    /** ML Kit 存放远端模型的位置（no_backup 下的固定目录名） */
    private const val MODEL_ROOT = "com.google.mlkit.translate.models"

    fun warmAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { warm(context) }
        }
    }

    suspend fun warm(context: Context) {
        runCatching { seedFromAssets(context) }
        // 下载兜底：内置模型播种成功时为空操作；失败时静默重下
        downloadIfNeeded(TranslateLanguage.ENGLISH, TranslateLanguage.CHINESE)
        downloadIfNeeded(TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH)
    }

    /**
     * 播种 assets 内置模型到 no_backup 模型目录，并写入注册哈希。
     * 目录结构：assets/mlkit_models/<langPair>/... → no_backup/<MODEL_ROOT>/<langPair>/...
     */
    private fun seedFromAssets(context: Context) {
        val assets = context.assets
        val pairs = runCatching { assets.list(ASSET_DIR)?.toList() }.getOrNull()
            ?.filter { !it.endsWith(".hash") } ?: return
        if (pairs.isEmpty()) return

        val modelRoot = File(context.noBackupFilesDir, MODEL_ROOT)
        pairs.forEach { pair ->
            val names = runCatching { assets.list("$ASSET_DIR/$pair")?.toList() }.getOrNull() ?: return@forEach
            if (names.isEmpty()) return@forEach

            // 注册标记存在且模型目录已有文件 → 视为播种完成，跳过
            val destPairDir = File(modelRoot, pair)
            val registered = context.getSharedPreferences("com.google.mlkit.internal", Context.MODE_PRIVATE)
                .contains("current_model_hash_COM.GOOGLE.BASE_TRANSLATE:$pair")
            if (registered && destPairDir.isDirectory && destPairDir.walk().any { it.isFile }) return@forEach

            var copied = 0
            names.forEach { top ->
                copyAssetDir(context, "$ASSET_DIR/$pair/$top", File(destPairDir, top))
                copied++
            }
            if (copied > 0) {
                // 写入模型注册哈希（缺了它 ML Kit 会认为模型未下载）
                registerModelHash(context, pair)
            }
        }
    }

    /** 递归复制 assets 子目录到目标目录 */
    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val entries = runCatching { context.assets.list(assetPath)?.toList() }.getOrNull() ?: return
        if (entries.isEmpty()) {
            // 是文件：直接复制
            destDir.parentFile?.mkdirs()
            runCatching {
                context.assets.open(assetPath).use { input ->
                    if (!destDir.exists() || destDir.length() != input.available().toLong()) {
                        destDir.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }.onFailure { destDir.delete() }
            return
        }
        destDir.mkdirs()
        entries.forEach { child -> copyAssetDir(context, "$assetPath/$child", File(destDir, child)) }
    }

    /** 写入 ML Kit 的 current_model_hash 注册标记（哈希值随模型放在 assets 的 .hash 文件里） */
    private fun registerModelHash(context: Context, pair: String) {
        val hash = runCatching {
            context.assets.open("$ASSET_DIR/$pair.hash").bufferedReader().use { it.readLine()?.trim() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        context.getSharedPreferences("com.google.mlkit.internal", Context.MODE_PRIVATE)
            .edit()
            .putString("current_model_hash_COM.GOOGLE.BASE_TRANSLATE:$pair", hash)
            .apply()
    }

    private suspend fun downloadIfNeeded(src: String, tgt: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(tgt)
            .build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded().awaitTask("warm $src->$tgt")
        } catch (_: Exception) {
            // 无网络/下载失败不影响使用，翻译时会再次尝试
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(what: String): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            addOnCanceledListener { if (cont.isActive) cont.resumeWithException(
                UnsupportedTextException("cancelled: $what")) }
        }
}
