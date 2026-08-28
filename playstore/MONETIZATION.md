# Monétisation RangIA

## Modèle retenu
Freemium + achat unique permanent.

### Produit Google Play
- ID produit : `rangia_pro_lifetime`
- Type : produit ponctuel non consommable
- Nom conseillé : `RangIA Pro à vie`
- Prix de lancement conseillé en France : **5,99 €**
- Pas d’abonnement.

Le prix réel est configuré dans Google Play Console et récupéré dynamiquement par l’application via Google Play Billing.

## Gratuit
- analyse d'un dossier sélectionné ;
- OCR local ;
- indexation et recherche ;
- correction/apprentissage local des catégories ;
- rangement manuel individuel.

## Pro
- scan du stockage partagé du téléphone ;
- rangement automatique des fichiers sûrs ;
- analyses périodiques ;
- futures fonctions Pro incluses dans la licence permanente.

## Implémentation
L’application utilise Google Play Billing Library 9.1.0 et le produit `rangia_pro_lifetime`.

Les builds `debug` activent Pro automatiquement pour faciliter les tests hors Play Store. Les builds `release` nécessitent un achat/restauration Google Play valide.

## Durcissement recommandé avant forte montée en charge
La v1 conserve un cache local de l’état Pro pour permettre l’usage hors ligne et interroge Google Play au lancement. Pour une résistance maximale au piratage et une gestion serveur des remboursements/révocations, ajouter ultérieurement une vérification backend via Google Play Developer API et Real-time Developer Notifications.
