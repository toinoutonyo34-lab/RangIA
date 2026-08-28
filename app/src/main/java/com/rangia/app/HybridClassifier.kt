package com.rangia.app

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Professional hybrid classifier.
 *
 * Priority order:
 * 1) high precision document signatures;
 * 2) close matches to documents manually corrected by the user;
 * 3) agreement between deterministic rules and the local statistical model;
 * 4) A_verifier/Documents instead of inventing a category.
 */
class HybridClassifier(context: Context) {
    private val training = AiTrainingStore(context)

    companion object {
        const val MODEL_VERSION = 3
    }

    fun classify(fileName: String, text: String): ClassificationResult {
        SmartCategoryRefiner.refine(fileName, text)?.let { return it }

        learnedMatch(fileName, text)?.let { return it }

        val rules = DocumentIntelligence.ruleClassify(fileName, text)
        val ai = LocalAiEngine(training.load()).predict(fileName, text)

        val ruleIsStrong = rules.categoryPath != "Autres" &&
            rules.confidence >= 0.90f && rules.matchedKeywords.size >= 2
        val modelAgrees = ai.category == rules.categoryPath

        if (ruleIsStrong && (modelAgrees || rules.confidence >= 0.96f)) {
            val confidence = if (modelAgrees) maxOf(rules.confidence, 0.94f) else rules.confidence
            return ClassificationResult(
                rules.categoryPath,
                confidence.coerceAtMost(0.98f),
                (rules.matchedKeywords + ai.evidence).distinct().take(8)
            )
        }

        // A weak statistical guess is never enough to move a real document into a domain such as
        // Voiture, Banque or Santé. The user can review it instead.
        if (modelAgrees && rules.categoryPath != "Autres" && rules.confidence >= 0.78f && ai.confidence >= 0.86f) {
            return ClassificationResult(
                rules.categoryPath,
                ((rules.confidence + ai.confidence) / 2f).coerceIn(0.78f, 0.93f),
                (rules.matchedKeywords + ai.evidence).distinct().take(8)
            )
        }

        return ClassificationResult(
            "A_verifier/Documents",
            0.38f,
            listOf("classification incertaine")
        )
    }

    private fun learnedMatch(fileName: String, text: String): ClassificationResult? {
        val input = tokens("$fileName\n${text.take(14_000)}")
        if (input.size < 4) return null

        data class Candidate(val category: String, val score: Float, val common: Int)

        val best = training.load().mapNotNull { example ->
            val sample = tokens(example.text)
            if (sample.size < 4) return@mapNotNull null
            val common = input.intersect(sample).size
            if (common < 4) return@mapNotNull null
            val overlap = common.toFloat() / minOf(input.size, sample.size).coerceAtLeast(1)
            Candidate(example.category, overlap, common)
        }.maxByOrNull { it.score }

        return when {
            best == null -> null
            best.score >= 0.58f && best.common >= 6 -> ClassificationResult(best.category, 0.98f, listOf("correction personnelle similaire"))
            best.score >= 0.42f && best.common >= 8 -> ClassificationResult(best.category, 0.93f, listOf("apprentissage personnel"))
            else -> null
        }
    }

    private fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .asSequence()
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .take(1800)
        .toSet()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun learn(doc: IndexedDocument, correctedCategory: String) {
        training.add(correctedCategory, doc.originalName, doc.extractedText)
    }

    fun learnedExamplesCount(): Int = training.count()
    fun resetLearning() = training.reset()

    private val STOP_WORDS = setOf(
        "les", "des", "une", "pour", "dans", "avec", "sur", "par", "aux", "est", "sont",
        "vous", "votre", "vos", "nous", "notre", "the", "and", "for", "from", "this"
    )
}
