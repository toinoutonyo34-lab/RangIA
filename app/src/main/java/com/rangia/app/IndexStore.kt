package com.rangia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class IndexStore(private val context: Context) {
    private val lock = Any()
    private val file: File get() = File(context.filesDir, "rangia_index.json")

    fun load(): List<IndexedDocument> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) add(array.getJSONObject(i).toDoc())
            }
        }.getOrDefault(emptyList())
    }

    fun save(documents: List<IndexedDocument>) = synchronized(lock) {
        val array = JSONArray()
        documents.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    private fun IndexedDocument.toJson() = JSONObject().apply {
        put("uri", uri)
        put("parentTreeUri", parentTreeUri)
        put("relativePath", relativePath)
        put("originalName", originalName)
        put("displayName", displayName)
        put("mimeType", mimeType)
        put("size", size)
        put("modifiedAt", modifiedAt)
        put("extractedText", extractedText)
        put("categoryPath", categoryPath)
        put("confidence", confidence.toDouble())
        put("suggestedName", suggestedName)
        put("amount", amount ?: JSONObject.NULL)
        put("detectedDate", detectedDate ?: JSONObject.NULL)
        put("organization", organization ?: JSONObject.NULL)
        put("hash", hash)
        put("duplicate", duplicate)
        put("indexedAt", indexedAt)
        put("classificationVersion", classificationVersion)
        put("classificationEvidence", JSONArray().apply { classificationEvidence.forEach(::put) })
    }

    private fun JSONObject.toDoc() = IndexedDocument(
        uri = getString("uri"),
        parentTreeUri = optString("parentTreeUri"),
        relativePath = optString("relativePath"),
        originalName = getString("originalName"),
        displayName = optString("displayName", getString("originalName")),
        mimeType = optString("mimeType"),
        size = optLong("size"),
        modifiedAt = optLong("modifiedAt"),
        extractedText = optString("extractedText"),
        categoryPath = optString("categoryPath", "A_verifier/Documents"),
        confidence = optDouble("confidence", 0.0).toFloat(),
        suggestedName = optString("suggestedName", getString("originalName")),
        amount = if (isNull("amount")) null else optDouble("amount"),
        detectedDate = if (isNull("detectedDate")) null else optString("detectedDate"),
        organization = if (isNull("organization")) null else optString("organization"),
        hash = optString("hash"),
        duplicate = optBoolean("duplicate"),
        indexedAt = optLong("indexedAt"),
        classificationVersion = optInt("classificationVersion", 0),
        classificationEvidence = optJSONArray("classificationEvidence")?.let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }.orEmpty()
    )
}
