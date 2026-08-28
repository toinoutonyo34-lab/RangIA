package com.rangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * High precision first-stage classifier.
 *
 * It deliberately prefers "A_verifier/Documents" over a wrong category. Strong document
 * signatures are checked before the statistical model and ambiguous generic words are never
 * enough on their own (for example "certificat" does not imply a vehicle document).
 */
object SmartCategoryRefiner {
    val categories = listOf(
        "A_verifier/Documents",
        "Entreprise/Factures",
        "Entreprise/Devis",
        "Entreprise/URSSAF",
        "Entreprise/Clients_commandes",
        "Entreprise/Assurance_professionnelle",
        "Travail/Fiches_de_paie",
        "Travail/Contrats",
        "Travail/France_Travail",
        "Travail/Qualifications_habilitations",
        "Administratif/CAF",
        "Administratif/CPAM_Ameli",
        "Administratif/Retraite",
        "Administratif/Prefecture_ANTS",
        "Administratif/Etat_civil",
        "Administratif/Attestations",
        "Sante/Ordonnances",
        "Sante/Analyses_resultats",
        "Sante/Mutuelle",
        "Sante/Rendez_vous",
        "Etudes/Diplomes_certificats",
        "Etudes/Formations",
        "Etudes/Releves_notes",
        "Identite/Carte_identite",
        "Identite/Passeport",
        "Identite/Permis_de_conduire",
        "Voiture/Carte_grise",
        "Voiture/Controle_technique",
        "Voiture/Assurance",
        "Voiture/Entretien_reparation",
        "Voiture/Contraventions",
        "Banque/RIB_IBAN",
        "Banque/Releves",
        "Banque/Credits",
        "Impots/Avis_imposition",
        "Impots/Declarations",
        "Logement/Bail",
        "Logement/Quittances",
        "Logement/Energie",
        "Logement/Telecom",
        "Logement/Assurance_habitation",
        "Achats/Tickets_recus",
        "Achats/Garanties_SAV",
        "Voyages/Avion",
        "Voyages/Train",
        "Voyages/Hotels_reservations",
        "Notices/Manuels"
    )

    fun refine(fileName: String, text: String): ClassificationResult? {
        val file = normalize(fileName)
        val value = normalize("$fileName\n${text.take(120_000)}")
        if (value.isBlank()) return null

        fun has(vararg terms: String): Boolean = terms.any { normalize(it) in value }
        fun fileHas(vararg terms: String): Boolean = terms.any { normalize(it) in file }
        fun hasAll(vararg terms: String): Boolean = terms.all { normalize(it) in value }
        fun result(category: String, confidence: Float, vararg evidence: String) =
            ClassificationResult(category, confidence, evidence.toList())

        // Education / qualifications are intentionally checked before vehicle documents.
        if (
            has("certificat d aptitude professionnelle", "diplome national du brevet", "baccalaureat", "brevet professionnel", "diplome") ||
            (fileHas("cap ", "cap_", "cap-") && has("education nationale", "academie", "certificat d aptitude"))
        ) return result("Etudes/Diplomes_certificats", .995f, "diplôme/certificat", "éducation")

        if (has("releve de notes", "bulletin scolaire", "resultats examen", "notes obtenues"))
            return result("Etudes/Releves_notes", .985f, "relevé de notes")

        if (has("attestation de formation", "certificat de formation", "formation professionnelle", "organisme de formation") &&
            !has("caces", "habilitation electrique", "sst", "sauveteur secouriste"))
            return result("Etudes/Formations", .965f, "formation")

        if (has("caces", "habilitation electrique", "sauveteur secouriste du travail", "sst", "autorisation de conduite"))
            return result("Travail/Qualifications_habilitations", .985f, "qualification/habilitation")

        // Identity.
        if (has("carte nationale d identite", "cni") && has("nom", "prenom", "nationalite"))
            return result("Identite/Carte_identite", .995f, "carte d'identité")
        if (has("passeport", "passport") && has("nationalite", "date de naissance", "expiry", "expiration"))
            return result("Identite/Passeport", .995f, "passeport")
        if (has("permis de conduire", "driving licence", "driving license"))
            return result("Identite/Permis_de_conduire", .995f, "permis de conduire")

        // Vehicle: each class requires an unmistakable vehicle signature.
        if (has("certificat d immatriculation", "carte grise") && has("immatriculation", "vehicule", "vin", "numero d identification"))
            return result("Voiture/Carte_grise", .995f, "certificat d'immatriculation")
        if (has("controle technique", "contre visite", "defaillance majeure", "defaillance critique") && has("vehicule", "immatriculation", "kilometrage"))
            return result("Voiture/Controle_technique", .995f, "contrôle technique", "véhicule")
        if (has("assurance automobile", "assurance auto", "vehicule assure", "attestation d assurance automobile"))
            return result("Voiture/Assurance", .985f, "assurance automobile")
        if (has("amende forfaitaire", "avis de contravention", "antai") && has("immatriculation", "vehicule", "infraction"))
            return result("Voiture/Contraventions", .985f, "contravention")
        if (has("vidange", "revision automobile", "pneumatique", "courroie de distribution", "plaquettes de frein") &&
            has("vehicule", "kilometrage", "garage", "main d oeuvre", "immatriculation"))
            return result("Voiture/Entretien_reparation", .965f, "entretien véhicule")

        // Work / employment.
        if (has("bulletin de paie", "fiche de paie", "net a payer", "net social") && has("salaire", "employeur", "cotisations"))
            return result("Travail/Fiches_de_paie", .995f, "bulletin de paie")
        if (has("contrat de travail", "contrat de mission") && has("employeur", "salarie", "remuneration", "date d embauche"))
            return result("Travail/Contrats", .985f, "contrat de travail")
        if (has("france travail", "pole emploi", "allocation retour emploi", "allocation d aide au retour", "arce") ||
            (has("are") && has("demandeur d emploi", "indemnisation")))
            return result("Travail/France_Travail", .985f, "France Travail")

        // Public administration.
        if (has("allocations familiales", "caf fr", "caisse d allocations familiales", "aide personnalisee au logement"))
            return result("Administratif/CAF", .985f, "CAF")
        if (has("assurance maladie", "ameli fr", "cpam", "caisse primaire d assurance maladie"))
            return result("Administratif/CPAM_Ameli", .985f, "CPAM/Ameli")
        if (has("agirc arrco", "carsat", "assurance retraite", "retraite complementaire"))
            return result("Administratif/Retraite", .975f, "retraite")
        if (has("prefecture", "sous prefecture", "ants", "agence nationale des titres securises") && !has("carte grise", "certificat d immatriculation"))
            return result("Administratif/Prefecture_ANTS", .955f, "préfecture/ANTS")
        if (has("acte de naissance", "extrait d acte de naissance", "acte de mariage", "livret de famille"))
            return result("Administratif/Etat_civil", .985f, "état civil")
        if (has("attestation sur l honneur", "attestation d hebergement", "certifie sur l honneur"))
            return result("Administratif/Attestations", .965f, "attestation")

        // Health.
        if (has("ordonnance", "prescription medicale") && has("medecin", "patient", "pharmacie", "posologie"))
            return result("Sante/Ordonnances", .985f, "ordonnance")
        if (has("laboratoire de biologie", "resultats d analyses", "analyse biologique", "hematologie", "biochimie"))
            return result("Sante/Analyses_resultats", .985f, "analyses médicales")
        if (has("mutuelle", "complementaire sante", "tiers payant") && !has("assurance automobile"))
            return result("Sante/Mutuelle", .965f, "mutuelle")
        if (has("rendez vous", "convocation") && has("hopital", "clinique", "medecin", "consultation"))
            return result("Sante/Rendez_vous", .935f, "rendez-vous médical")

        // Banking / taxes.
        if (has("releve d identite bancaire", "rib") || hasAll("iban", "bic"))
            return result("Banque/RIB_IBAN", .985f, "RIB/IBAN")
        if (has("releve de compte", "solde precedent", "solde crediteur", "solde debiteur") && has("debit", "credit", "operations", "virement"))
            return result("Banque/Releves", .975f, "relevé bancaire")
        if (has("offre de pret", "tableau d amortissement", "credit immobilier", "credit consommation"))
            return result("Banque/Credits", .965f, "crédit")
        if (has("avis d imposition", "avis d impot", "revenu fiscal de reference", "direction generale des finances publiques"))
            return result("Impots/Avis_imposition", .99f, "avis d'imposition")
        if (has("declaration de revenus", "declaration revenus", "formulaire 2042"))
            return result("Impots/Declarations", .975f, "déclaration fiscale")

        // Housing and recurring providers before generic invoices.
        if (has("quittance de loyer"))
            return result("Logement/Quittances", .995f, "quittance de loyer")
        if (has("contrat de location", "bail d habitation", "bailleur") && has("locataire", "loyer", "depot de garantie"))
            return result("Logement/Bail", .985f, "bail")
        if (has("assurance habitation", "multirisque habitation"))
            return result("Logement/Assurance_habitation", .975f, "assurance habitation")
        if (has("edf", "engie", "totalenergies", "electricite", "gaz naturel") && has("facture", "consommation", "kwh", "point de livraison"))
            return result("Logement/Energie", .965f, "énergie")
        if (has("orange", "sfr", "bouygues telecom", "free mobile", "freebox") && has("facture", "abonnement", "forfait"))
            return result("Logement/Telecom", .955f, "télécom")

        // Business.
        if (has("urssaf", "cotisations sociales", "micro entrepreneur", "auto entrepreneur") && has("cotisation", "declaration", "chiffre d affaires", "urssaf"))
            return result("Entreprise/URSSAF", .99f, "URSSAF")
        if (has("devis", "quotation", "bon pour accord") && has("prix", "total", "validite", "travaux", "prestation"))
            return result("Entreprise/Devis", .98f, "devis")
        if (has("assurance responsabilite civile professionnelle", "responsabilite civile professionnelle", "assurance decennale", "garantie decennale"))
            return result("Entreprise/Assurance_professionnelle", .985f, "assurance professionnelle")
        if (has("bon de commande", "fiche intervention") && has("client", "prestation", "commande", "chantier"))
            return result("Entreprise/Clients_commandes", .955f, "client/commande")
        if (has("facture", "invoice") && has("total ttc", "montant a payer", "net a payer", "tva", "siret", "siren"))
            return result("Entreprise/Factures", .965f, "facture")

        // Purchases / warranty.
        if (has("ticket de caisse", "recu de paiement", "recu carte bancaire"))
            return result("Achats/Tickets_recus", .955f, "ticket/reçu")
        if (has("garantie", "warranty", "service apres vente", "sav") && has("numero de serie", "date d achat", "produit", "reparation"))
            return result("Achats/Garanties_SAV", .955f, "garantie/SAV")

        // Travel.
        if (has("carte d embarquement", "boarding pass", "numero de vol", "flight number"))
            return result("Voyages/Avion", .975f, "avion")
        if (has("sncf", "billet de train", "tgv", "ter") && has("depart", "arrivee", "voyageur"))
            return result("Voyages/Train", .965f, "train")
        if (has("reservation hotel", "booking confirmation", "confirmation de reservation") && has("chambre", "hotel", "check in", "nuit"))
            return result("Voyages/Hotels_reservations", .945f, "hôtel/réservation")

        if (has("mode d emploi", "manuel utilisateur", "user manual", "notice d utilisation"))
            return result("Notices/Manuels", .955f, "notice/manuel")

        return null
    }

    private fun normalize(input: String): String = Normalizer.normalize(input.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
