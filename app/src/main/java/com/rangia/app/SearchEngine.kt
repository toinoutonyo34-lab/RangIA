package com.rangia.app

import java.text.Normalizer
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

object SearchEngine {
    fun search(query: String, documents: List<IndexedDocument>): List<IndexedDocument> {
        if (query.isBlank()) return documents
        val q = normalize(query)
        val tokens = q.split(Regex("\\s+")).filter { it.length > 1 && it !in stopWords }
        val minAmount = Regex("(?:plus de|superieur a|>)\\s*(\\d+(?:[.,]\\d+)?)").find(q)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val maxAmount = Regex("(?:moins de|inferieur a|<)\\s*(\\d+(?:[.,]\\d+)?)").find(q)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val requestedMonth = monthNumber(q)
        val requestedYear = Regex("\\b(20\\d{2})\\b").find(q)?.groupValues?.get(1)

        return documents.mapNotNull { doc ->
            if (minAmount != null && (doc.amount ?: -1.0) <= minAmount) return@mapNotNull null
            if (maxAmount != null && (doc.amount ?: Double.MAX_VALUE) >= maxAmount) return@mapNotNull null
            if (requestedMonth != null && doc.detectedDate?.substring(5, 7)?.toIntOrNull() != requestedMonth) return@mapNotNull null
            if (requestedYear != null && doc.detectedDate?.startsWith(requestedYear) != true && !doc.originalName.contains(requestedYear)) return@mapNotNull null

            val haystack = normalize(listOf(doc.displayName, doc.originalName, doc.categoryPath, doc.organization.orEmpty(), doc.extractedText).joinToString("\n"))
            var score = 0
            tokens.forEach { token ->
                if (normalize(doc.categoryPath).contains(token)) score += 6
                if (normalize(doc.displayName).contains(token)) score += 5
                if (normalize(doc.organization.orEmpty()).contains(token)) score += 4
                if (haystack.contains(token)) score += 1
            }
            if (tokens.isEmpty()) score = 1
            if (score > 0) doc to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    private val stopWords = setOf("de", "du", "des", "la", "le", "les", "un", "une", "mes", "mon", "ma", "trouve", "montre", "fichier", "fichiers", "document", "documents")

    private fun monthNumber(q: String): Int? {
        val locale = Locale.FRENCH
        return Month.values().firstOrNull { month ->
            val full = normalize(month.getDisplayName(TextStyle.FULL, locale))
            val short = normalize(month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.'))
            q.contains(full) || q.contains(short)
        }?.value
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}
