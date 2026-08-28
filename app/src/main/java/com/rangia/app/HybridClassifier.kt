package com.rangia.app

import android.content.Context

class HybridClassifier(context: Context) {
    private val training = AiTrainingStore(context)

    fun classify(fileName: String, text: String): ClassificationResult {
        val ai = LocalAiEngine(training.load()).predict(fileName, text)
        val rules = DocumentIntelligence.ruleClassify(fileName, text)
        if (rules.confidence >= 0.86f) {
            val conf = maxOf(rules.confidence, ai.confidence * if (ai.category == rules.categoryPath) 1.05f else 0.92f)
            return ClassificationResult(rules.categoryPath, conf.coerceAtMost(0.98f), (rules.matchedKeywords + ai.evidence).distinct().take(8))
        }
        val agreed = ai.category == rules.categoryPath
        val confidence = (ai.confidence + if (agreed) 0.08f else 0f).coerceAtMost(0.97f)
        return ClassificationResult(ai.category, confidence, ai.evidence)
    }

    fun learn(doc: IndexedDocument, correctedCategory: String) {
        training.add(correctedCategory, doc.originalName, doc.extractedText)
    }

    fun learnedExamplesCount(): Int = training.count()
    fun resetLearning() = training.reset()
}
