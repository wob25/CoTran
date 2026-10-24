package com.copytranslate.app.db

import com.copytranslate.app.util.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * 翻译历史实体（无 Room，仅作数据载体，字段与 Room 方案一致）。
 */
data class TranslationHistory(
    val id: Long,
    val sourceText: String,
    val translatedText: String,
    val fromLang: String,
    val toLang: String,
    val createTime: Long
)

/**
 * 历史记录仓库：用 SharedPreferences 存 JSON 数组（避免 Room 的 kapt/ksp 构建依赖）。
 * 新记录插到头部，最多保留 100 条。
 */
object HistoryRepository {

    private const val MAX_SIZE = 100

    fun getAll(): List<TranslationHistory> {
        return try {
            val arr = JSONArray(Prefs.historyJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                TranslationHistory(
                    id = o.optLong("id", 0L),
                    sourceText = o.optString("sourceText"),
                    translatedText = o.optString("translatedText"),
                    fromLang = o.optString("fromLang"),
                    toLang = o.optString("toLang"),
                    createTime = o.optLong("createTime", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(item: TranslationHistory) {
        val list = getAll().toMutableList()
        list.add(0, item)
        while (list.size > MAX_SIZE) list.removeAt(list.size - 1)
        save(list)
    }

    fun delete(id: Long) {
        save(getAll().filter { it.id != id })
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(list: List<TranslationHistory>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("sourceText", h.sourceText)
                    .put("translatedText", h.translatedText)
                    .put("fromLang", h.fromLang)
                    .put("toLang", h.toLang)
                    .put("createTime", h.createTime)
            )
        }
        Prefs.historyJson = arr.toString()
    }
}
