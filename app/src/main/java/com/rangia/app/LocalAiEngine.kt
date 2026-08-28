package com.rangia.app

import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

class LocalAiEngine(private val extraExamples: List<TrainingExample> = emptyList()) {
    data class Prediction(
        val category: String,
        val confidence: Float,
        val evidence: List<String>,
        val alternatives: List<Pair<String, Float>>
    )

    data class TrainingExample(val category: String, val text: String)

    companion object {
        val categories = listOf(
            "Entreprise/Factures", "Entreprise/Devis", "Entreprise/URSSAF", "Entreprise/Clients",
            "Travail/Fiches_de_paie", "Travail/Contrats", "Travail/France_Travail",
            "Voiture/Assurance", "Voiture/Controle_technique", "Voiture/Entretien",
            "Banque/Releves", "Impots", "Logement", "Identite", "Voyages", "Garanties", "Notices", "Autres"
        )

        private val seeds = listOf(
            TrainingExample("Entreprise/Factures", "facture numero total ttc hors taxe tva montant a payer date echeance fournisseur client reglement iban"),
            TrainingExample("Entreprise/Factures", "invoice facture achat fournitures total net payable tva siret siren paiement"),
            TrainingExample("Entreprise/Devis", "devis proposition commerciale prix estimatif validite du devis bon pour accord acompte travaux fourniture pose"),
            TrainingExample("Entreprise/Devis", "quotation estimation offre commerciale description travaux quantite prix unitaire total devis"),
            TrainingExample("Entreprise/URSSAF", "urssaf cotisations sociales micro entrepreneur auto entrepreneur declaration chiffre affaires contribution formation"),
            TrainingExample("Entreprise/URSSAF", "attestation vigilance urssaf cotisation travailleur independant echeancier paiement social"),
            TrainingExample("Entreprise/Clients", "bon de commande client chantier acompte commande adresse travaux reception prestation intervention"),
            TrainingExample("Entreprise/Clients", "client fiche intervention chantier commande prestation pose fenetre volet porte baie"),
            TrainingExample("Travail/Fiches_de_paie", "bulletin de paie salaire brut net a payer cotisations heures travaillees conges payes employeur salarie"),
            TrainingExample("Travail/Fiches_de_paie", "fiche de paie salaire mensuel net social prelevement source heures supplementaires"),
            TrainingExample("Travail/Contrats", "contrat de travail cdi cdd interim mission employeur salarie periode essai horaires remuneration"),
            TrainingExample("Travail/Contrats", "contrat mission travail temporaire agence interim date debut fin qualification salaire"),
            TrainingExample("Travail/France_Travail", "france travail pole emploi allocation retour emploi are arce demandeur emploi indemnisation droits"),
            TrainingExample("Travail/France_Travail", "notification ouverture droits allocation aide reprise creation entreprise france travail"),
            TrainingExample("Voiture/Assurance", "assurance automobile attestation assurance vehicule assure immatriculation garantie responsabilite civile contrat"),
            TrainingExample("Voiture/Assurance", "avis echeance assurance auto bonus malus sinistre conducteur vehicule prime"),
            TrainingExample("Voiture/Controle_technique", "controle technique proces verbal vehicule defaillance majeure mineure critique contre visite kilometrage"),
            TrainingExample("Voiture/Controle_technique", "centre controle technique numero immatriculation identification vehicule resultat favorable"),
            TrainingExample("Voiture/Entretien", "garage entretien vehicule vidange filtre huile pneus pneumatique frein amortisseur courroie reparation facture atelier"),
            TrainingExample("Voiture/Entretien", "revision automobile pieces main oeuvre diagnostic mecanique remplacement batterie"),
            TrainingExample("Banque/Releves", "releve de compte banque solde iban operations debit credit virement prelevement carte bancaire"),
            TrainingExample("Banque/Releves", "compte courant releve bancaire date valeur montant operation solde precedent nouveau solde"),
            TrainingExample("Impots", "avis imposition impot revenu fiscal direction generale finances publiques taxe declaration revenu"),
            TrainingExample("Impots", "impots gouv avis situation declarative revenu reference prelevement source tresor public"),
            TrainingExample("Logement", "bail location loyer quittance locataire proprietaire depot garantie logement charges adresse"),
            TrainingExample("Logement", "contrat location etat lieux quittance loyer agence immobiliere logement habitation"),
            TrainingExample("Identite", "carte nationale identite republique francaise nom prenom date naissance nationalite document identite"),
            TrainingExample("Identite", "passeport nom prenom date naissance sexe nationalite date expiration autorite"),
            TrainingExample("Voyages", "reservation hotel voyage vol avion boarding pass carte embarquement aeroport passager siege bagage"),
            TrainingExample("Voyages", "billet train reservation voyage depart arrivee passager confirmation booking"),
            TrainingExample("Garanties", "garantie warranty sav service apres vente numero serie date achat produit couverture reparation"),
            TrainingExample("Garanties", "certificat garantie fabricant produit panne remplacement facture achat"),
            TrainingExample("Notices", "mode emploi manuel utilisateur notice instructions installation utilisation securite entretien appareil"),
            TrainingExample("Notices", "guide utilisateur manuel technique instructions montage fonctionnement caracteristiques"),
            TrainingExample("Autres", "document note information divers piece jointe contenu general archive"),
            TrainingExample("Autres", "texte personnel brouillon memo divers sans categorie")
        )

        private val stopWords = setOf(
            "le","la","les","un","une","des","de","du","d","et","ou","a","au","aux","en","dans","pour","par","sur","avec","sans","ce","cet","cette","ces","est","sont","etre","vous","votre","vos","nous","notre","nos","il","elle","ils","elles","the","and","of","to","for","in","on"
        )
    }

    private val examples = seeds + extraExamples
    private val vocabulary: Set<String>
    private val tokenCountsByCategory: Map<String, Map<String, Int>>
    private val totalTokensByCategory: Map<String, Int>
    private val docsByCategory: Map<String, Int>

    init {
        val docs = mutableMapOf<String, Int>()
        val counts = mutableMapOf<String, MutableMap<String, Int>>()
        val vocab = mutableSetOf<String>()
        examples.forEach { ex ->
            docs[ex.category] = (docs[ex.category] ?: 0) + 1
            val map = counts.getOrPut(ex.category) { mutableMapOf() }
            tokenize(ex.text).forEach { token ->
                vocab += token
                map[token] = (map[token] ?: 0) + 1
            }
        }
        vocabulary = vocab
        tokenCountsByCategory = counts
        totalTokensByCategory = counts.mapValues { (_, m) -> m.values.sum() }
        docsByCategory = docs
    }

    fun predict(fileName: String, text: String): Prediction {
        val tokens = tokenize("$fileName\n${text.take(80_000)}")
        if (tokens.isEmpty()) return Prediction("Autres", 0.35f, emptyList(), listOf("Autres" to 0.35f))

        val totalDocs = examples.size.coerceAtLeast(1)
        val vocabSize = vocabulary.size.coerceAtLeast(1)
        val scores = categories.associateWith { cat ->
            val docCount = docsByCategory[cat] ?: 1
            var logProb = ln(docCount.toDouble() / (totalDocs + categories.size))
            val counts = tokenCountsByCategory[cat].orEmpty()
            val totalTokens = totalTokensByCategory[cat] ?: 0
            tokens.forEach { token ->
                val count = counts[token] ?: 0
                logProb += ln((count + 1.0) / (totalTokens + vocabSize.toDouble()))
            }
            logProb
        }

        val maxScore = scores.values.maxOrNull() ?: 0.0
        val exps = scores.mapValues { exp((it.value - maxScore).coerceAtLeast(-60.0)) }
        val sum = exps.values.sum().coerceAtLeast(1e-12)
        val probs = exps.mapValues { (it.value / sum).toFloat() }
        val ranked = probs.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val raw = best.value
        val second = ranked.getOrNull(1)?.value ?: 0f
        val margin = (raw - second).coerceAtLeast(0f)
        val calibrated = (0.50f + margin * 0.42f + raw * 0.18f).coerceIn(0.35f, 0.97f)

        return Prediction(
            category = best.key,
            confidence = calibrated,
            evidence = topEvidence(best.key, tokens),
            alternatives = ranked.take(3).map { it.key to it.value }
        )
    }

    private fun topEvidence(category: String, inputTokens: List<String>): List<String> {
        val categoryCounts = tokenCountsByCategory[category].orEmpty()
        return inputTokens.distinct()
            .map { token -> token to (categoryCounts[token] ?: 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(6)
            .map { it.first }
    }

    private fun tokenize(value: String): List<String> {
        val base = Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (base.isBlank()) return emptyList()
        val words = base.split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
            .take(7000)
        val bigrams = words.zipWithNext().map { (a, b) -> "${a}_${b}" }
        return words + bigrams
    }
}
