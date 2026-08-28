package com.rangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * High precision local rules used before the statistical classifier.
 * These rules only inspect the file name and OCR text already stored locally.
 */
object SmartCategoryRefiner {
    val categories = listOf(
        "Administratif/CAF",
        "Administratif/CPAM_Ameli",
        "Administratif/Retraite",
        "Administratif/Prefecture",
        "Administratif/Attestations",
        "Sante/Ordonnances",
        "Sante/Analyses",
        "Sante/Mutuelle",
        "Etudes/Diplomes",
        "Etudes/Formations",
        "Identite/Carte_identite",
        "Identite/Passeport",
        "Identite/Permis_de_conduire",
        "Voiture/Carte_grise",
        "Voiture/Assurance",
        "Voiture/Controle_technique",
        "Voiture/Entretien",
        "Logement/Bail",
        "Logement/Quittances",
        "Logement/Energie",
        "Logement/Telecom",
        "Assurances/Habitation",
        "Banque/RIB_IBAN",
        "Banque/Releves",
        "Achats/Tickets_et_recus",
        "Achats/Garanties",
        "Voyages/Avion",
        "Voyages/Train",
        "Entreprise/Factures",
        "Entreprise/Devis",
        "Entreprise/URSSAF",
        "Entreprise/Clients",
        "Travail/Fiches_de_paie",
        "Travail/Contrats",
        "Travail/France_Travail"
    )

    fun refine(fileName: String, text: String): ClassificationResult? {
        val value = normalize("$fileName\n${text.take(120_000)}")
        if (value.isBlank()) return null

        fun has(vararg terms: String): Boolean = terms.any { normalize(it) in value }
        fun hasAll(vararg terms: String): Boolean = terms.all { normalize(it) in value }
        fun result(category: String, confidence: Float, vararg evidence: String) =
            ClassificationResult(category, confidence, evidence.toList())

        return when {
            has("carte nationale d identite", "cni") -> result("Identite/Carte_identite", .98f, "carte d'identité")
            has("passeport", "passport") && has("nationalite", "date de naissance", "expiry") -> result("Identite/Passeport", .98f, "passeport")
            has("permis de conduire", "driving licence") -> result("Identite/Permis_de_conduire", .98f, "permis de conduire")
            has("certificat d immatriculation", "carte grise") -> result("Voiture/Carte_grise", .98f, "carte grise")
            has("controle technique", "contre visite", "defaillance majeure") -> result("Voiture/Controle_technique", .98f, "contrôle technique")
            has("assurance automobile", "assurance auto", "vehicule assure") -> result("Voiture/Assurance", .97f, "assurance auto")
            has("vidange", "revision automobile", "garage", "pneumatique", "courroie") && has("vehicule", "kilometrage", "main d oeuvre", "piece") -> result("Voiture/Entretien", .94f, "entretien véhicule")

            has("allocations familiales", "caf.fr", "caisse d allocations familiales", "aide au logement") -> result("Administratif/CAF", .97f, "CAF")
            has("assurance maladie", "ameli.fr", "cpam", "caisse primaire d assurance maladie") -> result("Administratif/CPAM_Ameli", .97f, "CPAM/Ameli")
            has("agirc arrco", "carsat", "assurance retraite", "retraite complementaire") -> result("Administratif/Retraite", .96f, "retraite")
            has("prefecture", "sous prefecture", "titre de sejour", "ants") && !has("carte grise") -> result("Administratif/Prefecture", .94f, "préfecture")
            has("attestation", "certifie que") && has("domicile", "hebergement", "honneur", "droits") -> result("Administratif/Attestations", .90f, "attestation")

            has("ordonnance", "prescription medicale") -> result("Sante/Ordonnances", .97f, "ordonnance")
            has("laboratoire de biologie", "resultats d analyses", "analyse biologique", "hematologie") -> result("Sante/Analyses", .97f, "analyses")
            has("mutuelle", "complementaire sante", "tiers payant") -> result("Sante/Mutuelle", .95f, "mutuelle")

            has("diplome", "certificat d aptitude professionnelle", "baccalaureat", "brevet professionnel") -> result("Etudes/Diplomes", .97f, "diplôme")
            has("caces", "habilitation electrique", "attestation de formation", "certificat de formation") -> result("Etudes/Formations", .96f, "formation")

            has("quittance de loyer") -> result("Logement/Quittances", .98f, "quittance")
            has("contrat de location", "bail d habitation", "bailleur", "locataire") -> result("Logement/Bail", .96f, "bail")
            has("edf", "engie", "electricite", "gaz naturel") && has("facture", "echeance", "consommation") -> result("Logement/Energie", .95f, "énergie")
            has("orange", "sfr", "bouygues telecom", "free mobile", "freebox") && has("facture", "abonnement", "forfait") -> result("Logement/Telecom", .94f, "télécom")
            has("assurance habitation", "multirisque habitation") -> result("Assurances/Habitation", .96f, "assurance habitation")

            has("releve d identite bancaire", "rib") || hasAll("iban", "bic") -> result("Banque/RIB_IBAN", .97f, "RIB/IBAN")
            has("releve de compte", "solde precedent", "operations debit credit") -> result("Banque/Releves", .96f, "relevé bancaire")

            has("ticket de caisse", "recu de paiement", "recu carte bancaire") -> result("Achats/Tickets_et_recus", .94f, "ticket/reçu")
            has("garantie", "warranty", "service apres vente") && has("numero de serie", "date d achat", "produit") -> result("Achats/Garanties", .94f, "garantie")

            has("carte d embarquement", "boarding pass", "numero de vol", "flight") -> result("Voyages/Avion", .96f, "avion")
            has("sncf", "billet de train", "tgv", "ter") && has("depart", "arrivee", "voyageur") -> result("Voyages/Train", .95f, "train")

            has("bulletin de paie", "fiche de paie", "net a payer", "net social") -> result("Travail/Fiches_de_paie", .98f, "paie")
            has("contrat de travail", "contrat de mission", "cdi", "cdd") && has("employeur", "salarie", "remuneration") -> result("Travail/Contrats", .96f, "contrat de travail")
            has("france travail", "pole emploi", "allocation retour emploi", "arce", "are") -> result("Travail/France_Travail", .97f, "France Travail")

            has("urssaf", "cotisations sociales", "micro entrepreneur") -> result("Entreprise/URSSAF", .98f, "URSSAF")
            has("devis", "bon pour accord", "validite du devis") -> result("Entreprise/Devis", .97f, "devis")
            has("facture", "invoice") && has("total ttc", "montant a payer", "net a payer", "tva") -> result("Entreprise/Factures", .96f, "facture")
            has("bon de commande", "fiche intervention", "chantier") && has("client", "prestation", "commande") -> result("Entreprise/Clients", .92f, "client/chantier")

            else -> null
        }
    }

    private fun normalize(input: String): String = Normalizer.normalize(input.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
