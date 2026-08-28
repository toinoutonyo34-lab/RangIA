# Google Play — Data Safety (brouillon à reporter dans Play Console)

Ce document décrit **le code RangIA 1.0 actuel**. Vérifier à nouveau si des SDK d’analytics, publicité, crash reporting ou cloud sont ajoutés avant publication.

## Données de documents
RangIA accède aux fichiers que l’utilisateur autorise afin de fournir le service de classement. Le contenu OCR, les noms de fichiers, les catégories, les empreintes et l’index sont conservés localement sur l’appareil par le code actuel.

- Collecte vers les serveurs de l’éditeur : **Non**
- Partage avec des tiers par RangIA : **Non**
- Traitement : **sur l’appareil**

## Compte utilisateur
- Création de compte RangIA : **Non**
- Nom/e-mail demandé par l’application : **Non**

## Paiements
RangIA Pro utilise Google Play Billing. Le paiement est traité par Google Play. L’application reçoit l’état technique de l’achat et un jeton d’achat nécessaire à la gestion de la licence. RangIA ne demande ni ne stocke directement les coordonnées bancaires.

## Localisation
- Permission de localisation : **Non**

## Publicité
- SDK publicitaire : **Non**

## Analytics / suivi
- SDK analytics ajouté par RangIA : **Non**
- Profilage publicitaire : **Non**

## Sécurité
- trafic HTTP en clair désactivé (`usesCleartextTraffic=false`) ;
- aucune API d’IA distante dans la v1 ;
- sauvegarde Android de l’application désactivée (`allowBackup=false`).

## Point de vigilance
La déclaration Data Safety doit refléter tous les SDK présents au moment de l’envoi. Google Play Billing communique avec l’infrastructure Google Play pour exécuter les achats. Relire les exigences Play Console au moment de la soumission.
