# Déclaration Play Console — Accès à tous les fichiers

Permission : `android.permission.MANAGE_EXTERNAL_STORAGE`

## Fonction principale concernée
RangIA est une application de gestion et d’organisation de fichiers. Sa fonction Pro principale consiste à analyser le stockage partagé accessible de l’utilisateur, indexer ses fichiers, identifier leur type/contenu et permettre leur classement dans une arborescence RangIA.

## Pourquoi l’accès étendu est nécessaire
Le mode « téléphone complet » doit parcourir plusieurs dossiers du stockage partagé afin de trouver les documents et fichiers déjà dispersés sur l’appareil. Un accès limité à un seul dossier obligerait l’utilisateur à sélectionner manuellement chaque emplacement et empêcherait la fonction centrale de rangement global.

## Utilisation de la permission
- lecture des métadonnées et du contenu des fichiers accessibles ;
- OCR local des PDF/images compatibles ;
- calcul d’empreinte pour identifier les doublons ;
- création de catégories et index local ;
- déplacement de fichiers utilisateur vers `RangIA/<catégorie>` uniquement lorsqu’ils sont considérés comme sûrs.

## Mesures de protection
- aucune suppression définitive automatique basée uniquement sur une prédiction IA ;
- déplacement sécurisé par copie + contrôle avant suppression de l’original ;
- exclusion des emplacements système et des emplacements susceptibles de casser le fonctionnement d’autres applications ;
- traitement du contenu local sur l’appareil ;
- aucun envoi du contenu des documents vers une API d’IA distante.

## Alternative limitée disponible
La version gratuite permet aussi à l’utilisateur de choisir explicitement un dossier via le Storage Access Framework, sans utiliser le mode téléphone complet.

## À vérifier dans Play Console
Google Play restreint cette permission. Lors de la soumission, sélectionner la catégorie d’usage correspondant à **File management / Gestion de fichiers** et fournir une vidéo de démonstration montrant que le scan/rangement de fichiers est une fonctionnalité centrale de l’application.
