package com.rangia.app

import android.net.Uri

data class IndexedDocument(
    val uri: String,
    val parentTreeUri: String,
    val relativePath: String,
    val originalName: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val modifiedAt: Long,
    val extractedText: String,
    val categoryPath: String,
    val confidence: Float,
    val suggestedName: String,
    val amount: Double?,
    val detectedDate: String?,
    val organization: String?,
    val hash: String,
    val duplicate: Boolean,
    val indexedAt: Long = System.currentTimeMillis()
) {
    val contentUri: Uri get() = Uri.parse(uri)
}

data class ClassificationResult(
    val categoryPath: String,
    val confidence: Float,
    val matchedKeywords: List<String>
)

data class ExtractedEntities(
    val amount: Double? = null,
    val date: String? = null,
    val organization: String? = null
)
