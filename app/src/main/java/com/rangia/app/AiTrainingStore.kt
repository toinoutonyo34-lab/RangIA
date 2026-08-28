package com.rangia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AiTrainingStore(context: Context) {
    private val prefs = context.getSharedPreferences("rangia_ai_training", Context.MODE_PRIVATE)

    fun load(): List<LocalAiEngine.TrainingExample> {
        val raw = prefs.getString("examples", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(LocalAiEngine.TrainingExample(o.getString("category"), o.getString("text")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(category: String, fileName: String, text: String) {
        val all = load().toMutableList()
        val compact = "$fileName\n${text.take(12_000)}"
        all += LocalAiEngine.TrainingExample(category, compact)
        val kept = all.takeLast(250)
        val arr = JSONArray()
        kept.forEach { ex ->
            arr.put(JSONObject().apply {
                put("category", ex.category)
                put("text", ex.text)
            })
        }
        prefs.edit().putString("examples", arr.toString()).apply()
    }

    fun count(): Int = load().size
    fun reset() = prefs.edit().remove("examples").apply()
}
