package com.rangia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartCategoryRefinerTest {

    @Test
    fun capCertificateIsEducationNotVehicle() {
        val result = SmartCategoryRefiner.refine(
            "cap_constructeur_ouvrages_batiment.pdf",
            """
            CERTIFICAT D'APTITUDE PROFESSIONNELLE
            Académie de Montpellier
            Ministère de l'Éducation nationale
            Constructeur d'ouvrages du bâtiment
            """.trimIndent()
        )
        assertEquals("Etudes/Diplomes_certificats", result?.categoryPath)
    }

    @Test
    fun technicalControlRequiresVehicleEvidence() {
        val result = SmartCategoryRefiner.refine(
            "controle-technique.pdf",
            """
            Procès-verbal de contrôle technique
            Véhicule particulier
            Immatriculation AA-123-BB
            Kilométrage 128500 km
            Défaillance mineure
            """.trimIndent()
        )
        assertEquals("Voiture/Controle_technique", result?.categoryPath)
    }

    @Test
    fun genericCertificateDoesNotBecomeVehicle() {
        val result = SmartCategoryRefiner.refine(
            "certificat.pdf",
            "Certificat de conformité remis au titulaire."
        )
        assertNull(result)
    }

    @Test
    fun payslipIsWork() {
        val result = SmartCategoryRefiner.refine(
            "bulletin_juillet.pdf",
            """
            Bulletin de paie
            Employeur BATIR
            Salaire brut
            Cotisations
            Net à payer
            """.trimIndent()
        )
        assertEquals("Travail/Fiches_de_paie", result?.categoryPath)
    }

    @Test
    fun invoiceIsBusinessInvoice() {
        val result = SmartCategoryRefiner.refine(
            "facture_fournisseur.pdf",
            """
            FACTURE
            SIRET 12345678900012
            TVA 20%
            Total TTC 842,50 EUR
            Montant à payer
            """.trimIndent()
        )
        assertEquals("Entreprise/Factures", result?.categoryPath)
    }
}
