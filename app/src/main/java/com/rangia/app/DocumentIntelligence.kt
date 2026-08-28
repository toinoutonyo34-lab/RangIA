package com.rangia.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DocumentIntelligence {
    private data class Rule(val path: String, val keywords: Map<String, Int>)

    private val rules = listOf(
        Rule("Entreprise/Factures", weights("facture" to 8, "invoice" to 8, "total ttc" to 6, "tva" to 3, "echeance" to 3, "a payer" to 4)),
        Rule("Entreprise/Devis", weights("devis" to 10, "quotation" to 8, "proposition commerciale" to 7, "validite du devis" to 5)),
        Rule("Entreprise/URSSAF", weights("urssaf" to 12, "cotisations" to 6, "auto-entrepreneur" to 5, "micro-entrepreneur" to 5)),
        Rule("Entreprise/Clients", weights("bon de commande" to 8, "commande client" to 7, "acompte" to 4, "chantier" to 3)),
        Rule("Travail/Fiches_de_paie", weights("bulletin de paie" to 12, "salaire brut" to 7, "net a payer" to 7, "conges payes" to 3)),
        Rule("Travail/Contrats", weights("contrat de travail" to 12, "cdi" to 4, "cdd" to 4, "interimaire" to 4, "mission" to 3)),
        Rule("Travail/France_Travail", weights("france travail" to 12, "pole emploi" to 12, "allocation" to 5, "are" to 4, "arce" to 5)),
        Rule("Voiture/Assurance", weights("assurance automobile" to 12, "attestation d'assurance" to 10, "vehicule assure" to 7, "carte verte" to 6)),
        Rule("Voiture/Controle_technique", weights("controle technique" to 12, "proces-verbal" to 4, "defaillance majeure" to 6)),
        Rule("Voiture/Entretien", weights("vidange" to 6, "pneumatique" to 4, "garage" to 3, "reparation" to 4, "vehicule" to 2)),
        Rule("Banque/Releves", weights("releve de compte" to 12, "solde" to 4, "iban" to 4, "operations" to 3, "virement" to 3)),
        Rule("Impots", weights("impot" to 10, "avis d'imposition" to 12, "direction generale des finances" to 8, "revenu fiscal" to 7)),
        Rule("Logement", weights("bail" to 10, "loyer" to 7, "quittance" to 8, "locataire" to 5, "proprietaire" to 4)),
        Rule("Identite", weights("carte nationale d'identite" to 12, "passeport" to 12, "republique francaise" to 3, "date de naissance" to 4)),
        Rule("Voyages", weights("boarding pass" to 10, "carte d'embarquement" to 10, "reservation" to 4, "hotel" to 4, "vol" to 4)),
        Rule("Garanties", weights("garantie" to 9, "warranty" to 9, "sav" to 5, "numero de serie" to 5)),
        Rule("Notices", weights("mode d'emploi" to 10, "manuel utilisateur" to 10, "notice" to 7, "instructions" to 3))
    )

    fun ruleClassify(fileName: String, text: String): ClassificationResult {
        val haystack = normalize("$fileName\n$text")
        val scores = rules.map { rule ->
            val hits = rule.keywords.filterKeys { haystack.contains(normalize(it)) }
            Triple(rule, hits.values.sum(), hits.keys.toList())
        }.sortedByDescending { it.second }
        val best = scores.firstOrNull()
        if (best == null || best.second == 0) return ClassificationResult("Autres", 0.25f, emptyList())
        val runnerUp = scores.getOrNull(1)?.second ?: 0
        val confidence = (0.52f + best.second * 0.025f + (best.second - runnerUp) * 0.02f).coerceIn(0.52f, 0.98f)
        return ClassificationResult(best.first.path, confidence, best.third)
    }

    fun extractEntities(text: String): ExtractedEntities {
        val clean = text.replace('\u00A0', ' ')
        val amountRegex = Regex("(?i)(?:total\\s*(?:ttc)?|net\\s+a\\s+payer|montant|a\\s+payer)\\D{0,20}(\\d{1,6}(?:[ .,]\\d{3})*(?:[,.]\\d{2})?)\\s*€?")
        val looseAmountRegex = Regex("(\\d{1,6}(?:[ .,]\\d{3})*(?:[,.]\\d{2}))\\s*€")
        val amountString = amountRegex.find(clean)?.groupValues?.getOrNull(1)
            ?: looseAmountRegex.findAll(clean).map { it.groupValues[1] }.lastOrNull()
        val amount = amountString?.replace(" ", "")?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()

        val dateRegexes = listOf(Regex("\\b(\\d{2}/\\d{2}/\\d{4})\\b"), Regex("\\b(\\d{2}-\\d{2}-\\d{4})\\b"), Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b"))
        val date = dateRegexes.firstNotNullOfOrNull { it.find(clean)?.groupValues?.getOrNull(1) }?.let(::normalizeDate)

        val organization = clean.lineSequence().map { it.trim() }.filter { it.length in 3..60 }
            .firstOrNull { line ->
                val letters = line.count { it.isLetter() }
                val upper = line.count { it.isUpperCase() }
                letters >= 3 && upper.toDouble() / letters.coerceAtLeast(1) > 0.72 && !line.contains("FACTURE", true) && !line.contains("DEVIS", true)
            }
            ?.lowercase(Locale.FRENCH)?.split(" ")?.joinToString(" ") { word -> word.replaceFirstChar { c -> c.uppercaseChar() } }

        return ExtractedEntities(amount, date, organization)
    }

    fun suggestFileName(originalName: String, categoryPath: String, entities: ExtractedEntities): String {
        val ext = originalName.substringAfterLast('.', "pdf").lowercase().take(5)
        val date = entities.date ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val type = categoryPath.substringAfterLast('/').replace('_', '-')
        val org = entities.organization?.let(::safePart)?.take(35)
        val base = listOfNotNull(date, type, org).joinToString("_")
        return "${base.take(90)}.$ext"
    }

    private fun normalizeDate(raw: String): String {
        val formats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
        for (pattern in formats) {
            try { return LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern)).format(DateTimeFormatter.ISO_DATE) }
            catch (_: DateTimeParseException) { }
        }
        return raw
    }

    private fun safePart(value: String): String = normalize(value).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    private fun weights(vararg pairs: Pair<String, Int>) = mapOf(*pairs)
}
