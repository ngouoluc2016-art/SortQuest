# Obtenir l'APK de SortQuest et l'installer

Deux voies, au choix. La voie A ne demande aucune installation sur ton ordinateur.

---

## Voie A - GitHub Actions (navigateur uniquement, ~15 min la première fois)

GitHub compile l'APK sur ses serveurs grâce au fichier `.github/workflows/build-apk.yml` déjà inclus.

1. **Créer un compte** sur github.com (gratuit) si tu n'en as pas.
2. **Créer un dépôt privé** : bouton « + » > *New repository* > nom `SortQuest` > cocher **Private** > *Create repository*.
3. **Envoyer le code** :
   - le plus simple est **GitHub Desktop** (desktop.github.com) : *File > Add local repository* > dossier `SortQuest` dézippé > *Publish repository* ;
   - par le site : *Add file > Upload files*, puis glisser le **contenu** du dossier `SortQuest`. Attention, sur Mac et Windows les dossiers cachés (`.github`) ne sont pas toujours envoyés. Dans ce cas, crée le workflow à la main : *Actions > set up a workflow yourself*, puis colle le contenu de `.github/workflows/build-apk.yml` et nomme le fichier `build-apk.yml`.
4. **Lancer la compilation** : onglet **Actions** > *Build APK* > **Run workflow** (elle démarre aussi toute seule à chaque envoi).
5. **Récupérer l'APK** : clique sur l'exécution terminée (coche verte) > en bas, section **Artifacts** > **SortQuest-APK** (fichier zip). Il contient :
   - `SortQuest-1.0.0.apk` : **version à installer** (optimisée, plus rapide et plus légère) ;
   - `SortQuest-1.0.0-debug.apk` : version de diagnostic (s'installe à côté, nom « SortQuest » identique).

**Si l'exécution est rouge (échec)** : ouvre l'étape en erreur, copie les lignes contenant `e:` ou `error:` (ou télécharge l'artefact *rapports-erreur*) et transmets-les à Claude pour correction. Une première compilation d'un projet de cette taille révèle souvent quelques erreurs ponctuelles : c'est normal et rapide à corriger.

---

## Voie B - Android Studio (sur ton ordinateur)

1. Installer **Android Studio** (developer.android.com/studio), version récente.
2. *File > Open* > dossier `SortQuest` dézippé. Attendre la fin de la synchronisation Gradle (barre en bas, plusieurs minutes la première fois).
3. *Build > Build App Bundle(s) / APK(s) > Build APK(s)*.
4. Cliquer sur **locate** dans la notification : l'APK est dans `app/build/outputs/apk/debug/`.
   Pour la version optimisée : menu *Build > Select Build Variant* > `release`, puis refaire l'étape 3 (dossier `apk/release/`).

---

## Installer l'APK sur le téléphone

1. Transférer le fichier `.apk` sur le téléphone (câble USB, Google Drive, e-mail à soi-même...).
2. L'ouvrir depuis l'application **Fichiers**.
3. Android demande d'autoriser l'installation depuis cette source : *Paramètres > Autoriser cette source*, puis revenir et **Installer**.
4. Google Play Protect peut afficher « Application inconnue » : c'est normal pour une appli qui ne vient pas du Play Store. Choisir *Plus de détails > Installer quand même*.
5. Au premier lancement, suivre l'accueil et activer **Accès à tous les fichiers**.

Compatible Android 8.0 (API 26) et versions ultérieures.

---

## Signature officielle (uniquement pour publier sur Google Play)

L'APK obtenu sans configuration est signé avec une **clé de debug** : parfait pour un usage personnel, refusé par le Play Store.
Pour signer avec ta propre clé dans GitHub Actions, ajoute 4 secrets (*Settings > Secrets and variables > Actions*) :
`KEYSTORE_BASE64` (contenu du `.jks` encodé en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Pour le Play Store, construire plutôt un bundle : `./gradlew bundleRelease` (fichier `.aab`).

---

## Ce qui a déjà été vérifié

- La couche métier (domaine) a été **compilée avec le compilateur Kotlin 2.0** et ses **16 tests unitaires réussissent** : gamification (XP, niveaux, badges, séries, annulation), règles Auto-Pilot, détection des doublons en 3 passes.
- Les couches Android (interface Compose, Room, Hilt, WorkManager) n'ont **pas pu être compilées** dans l'environnement de génération, qui n'a pas accès aux dépôts Google et Maven. Leur cohérence a été contrôlée par script (imports, interfaces et implémentations, appels de méthodes, dépendances) : la première compilation réelle se fait via la voie A ou B.
