# SortQuest - v1.0.0 (prête pour la publication, après compilation et tests)

Application Android de tri de fichiers gamifiée (Kotlin, Jetpack Compose M3, MVVM + Clean Architecture, Hilt, Room, WorkManager).

> Statut : code écrit et relu, **non compilé** (pas de SDK Android dans l'environnement de génération).
> Premier réflexe à l'ouverture : `Build > Make Project`, corriger les éventuelles erreurs d'import, puis tester sur un émulateur Android 14/15.

## 1. Arborescence

```
SortQuest/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml            # catalogue de versions
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/{strings,themes}.xml
        └── java/com/sortquest/app/
            ├── SortQuestApp.kt          # Hilt + WorkManager + Coil (vidéo)
            ├── MainActivity.kt
            ├── core/permission/
            │   └── StoragePermissionHelper.kt
            ├── domain/                  # Kotlin pur, aucune dépendance Android
            │   ├── model/               # FileItem, SortAction, SortRule, Progress, AutoPilot
            │   ├── gamification/        # GamificationManager (fonction pure) + GameEvent
            │   ├── repository/          # File, Rule, AutoPilot, Progress (interfaces)
            │   └── usecase/             # SortUseCases, RuleUseCases (dont RunAutoPilot)
            ├── data/
            │   ├── file/                # StoragePaths, FileCategorizer, SafeFileOperations, StorageLock, Sha256FileHasher
            │   ├── local/               # Room v3 : SortHistory, AutoRule, UserProgress, Files, Trash
            │   ├── mapper/
            │   └── repository/          # File, Trash, Rule, AutoPilot, Progress + UndoEngine
            ├── di/                      # modules Hilt
            ├── workers/                 # ScanWorker, AutoSortWorker, TrashCleanupWorker
            └── ui/
                ├── theme/               # palette néon, Dark Mode par défaut
                ├── navigation/
                ├── navigation/          # NavHost + barre inférieure 4 onglets + MainViewModel
                ├── onboarding/          # HorizontalPager 3 étapes + guide de permission
                ├── components/          # Shimmer, bandeau de scan, icônes de fichiers
                ├── dashboard/           # DashboardHeader (jauge, niveau, XP, série, badges)
                ├── celebration/         # CelebrationHost : modale + confettis
                ├── sort/                # onglet Trier (Swipe)
                ├── clean/               # onglet Nettoyer (doublons, top 20 lourds)
                ├── rules/               # onglet Auto-Pilot (règles + historique)
                └── trash/               # onglet Corbeille
```

Tests unitaires : `app/src/test/.../GamificationManagerTest.kt` (`./gradlew test`).

## Barème de gamification

| Événement                                   | XP                                   |
|---------------------------------------------|--------------------------------------|
| Fichier classé / coffre-fort                | +10                                  |
| Fichier à la corbeille                      | +5 (+1 / 10 Mo, max +20)             |
| Doublon à la corbeille                      | +25 (+ bonus de taille)              |
| Combo en Session Express                    | x(1 + 0,1 x combo), max x2           |
| Session Express réussie (20 fichiers/2 min) | +50 + 2 / seconde restante           |
| Auto-Pilot                                  | +2 par fichier                       |
| Série (1re action manuelle du jour)         | +5 x jours de série (max +35)        |

Badges : Premier Tri (1 fichier), Chasseur de Doublons (10 doublons), 1 Go Libéré,
Maître de l'Auto-Pilot (50 fichiers rangés par l'Auto-Pilot), Archiviste Régulier (7 jours d'affilée).

## Auto-Pilot

- Règle = mot-clé ET extension ET taille minimale (conditions vides ignorées, au moins une obligatoire).
- Portée : fichiers à la racine de `Download` ; les téléchargements en cours (`.crdownload`, `.part`...) sont exclus.
- Première règle qui correspond gagne (priorité, puis ancienneté).
- Chaque exécution a un `batchId` : bouton "Annuler" = toute l'exécution remise en place.
- Un swipe et l'Auto-Pilot partagent le même verrou disque (`StorageLock`) : jamais deux déplacements simultanés.

## Doublons (3 passes)

1. Même taille exacte (SQL, aucune lecture disque).
2. Empreinte partielle : taille + 64 premiers Ko + 64 derniers Ko (128 Ko lus max).
3. SHA-256 complet uniquement pour les survivants (inutile si fichier <= 128 Ko : déjà lu en entier).

Un fichier à la fois, tampon de 64 Ko, empreintes en cache (invalidées si le fichier change), annulable.

## Corbeille

`/.SortQuest_Trash` (caché, `.nomedia`), dans le stockage partagé : déplacement instantané et
survie à une désinstallation. Rétention 7 jours, `TrashCleanupWorker` quotidien (batterie OK, appareil inactif).
La suppression définitive refuse tout chemin situé hors de ce dossier.

## Base de données

Version 3. Les v1 et v2 n'ayant jamais été publiées, `fallbackToDestructiveMigrationFrom(1, 2)` est utilisé.
**À retirer avant la première publication** : ensuite, chaque évolution de schéma = une `Migration` explicite.

## 2. Gestes

| Geste  | Action            | Effet disque                                   |
|--------|-------------------|------------------------------------------------|
| Droite | Classer           | -> `Documents/SortQuest/<Catégorie>/...`       |
| Gauche | Corbeille         | -> `/.SortQuestTrash/` (+ `.nomedia`), 7 jours |
| Haut   | Coffre-fort       | -> `Documents/SortQuest/Coffre-fort`           |
| Bas    | Plus tard         | Aucun, réapparaît après 24 h                   |

Chaque geste a un bouton équivalent (accessibilité, usage à une main).

## 3. Garanties de sécurité des fichiers

1. **Aucun écrasement** : le nom cible est réservé par création exclusive (`Files.createFile`, O_EXCL) avant le déplacement. Conflit -> `nom (1).ext`, `nom (2).ext`...
2. **Déplacement atomique** (`rename`) sur le même volume ; sinon copie + `fsync` + contrôle de taille, puis suppression de la source.
3. **Cohérence disque/base** : si la transaction Room échoue, le fichier est remis à sa place.
4. **Zones interdites** : rien hors du stockage partagé, jamais `Android/data` ni `Android/obb`.
5. **Corbeille dans le stockage partagé** (et non dans le dossier privé de l'appli) : une désinstallation ne fait pas perdre les fichiers en attente de suppression.
6. **Purge** : quotidienne via WorkManager, uniquement à l'intérieur de `.SortQuestTrash`.

## 4. Points d'attention avant publication

- **Google Play et `MANAGE_EXTERNAL_STORAGE`** : permission restreinte. Il faut remplir la déclaration "All files access" dans la Play Console (catégorie *Gestionnaire de fichiers*), sinon l'appli est refusée. Hors Play Store (APK perso), aucune contrainte.
- **Versions** : le catalogue fige des versions stables connues (AGP 8.7, Kotlin 2.1, Compose BOM 2024.12). Mettre à jour via Android Studio, et passer `compileSdk`/`targetSdk` à la dernière version disponible.
- **Icône** : l'icône système par défaut est utilisée ; générer la vôtre via `New > Image Asset`.

## Finitions (v1.0.0)

- **Confettis** : `ui/components/ConfettiOverlay.kt`, Canvas pur, style pluie (montée de niveau) ou explosion (Session Express réussie). Aucune recomposition pendant l'animation.
- **Fly-to-Folder** : en classant (droite) ou en mettant au coffre (haut), la carte rétrécit et file vers l'icône du dossier cible avec un `spring()` légèrement rebondissant ; l'icône rebondit à l'arrivée.
- **Fluidité 60/120 Hz** : pendant un glissement, position, échelle, bordure et tampon sont lus en phase de dessin (`graphicsLayer`, `drawWithContent`) ; la carte ne se recompose que quand la direction change.
- **Sons + haptique** : `core/feedback/FeedbackManager.kt` (SoundPool, 5 sons synthétisés de 51 Ko au total dans `res/raw`, générés par `tools/gen_sounds.py`). Mode silencieux et vibreur respectés, interrupteurs indépendants dans Paramètres (DataStore).
- **Miniatures** : `core/image/LocalThumbnails.kt`. PDF (1re page via PdfRenderer) et vidéo (image à 1 s) rendus une fois, mis en cache disque en WebP (48 Mo max), puis décodés par Coil (cache mémoire 15 %). 2 rendus et 2 décodages simultanés au maximum. Les 3 cartes suivantes sont préchargées.
- **Icône adaptative** : `res/drawable/ic_launcher_*.xml` (+ monochrome pour les icônes thématisées d'Android 13+), vérifiée sur masques rond, carré arrondi et monochrome (`store/icon-preview.png`).
- **Release** : `app/proguard-rules.pro` commenté, signature via `keystore.properties` (modèle fourni, fichier réel exclu de Git).
- **Fiche Play Store** : `store/play-store-listing.md` (titre, descriptions, déclaration d'accès à tous les fichiers, sécurité des données, règles de confidentialité, notes de version) + icône 512 px.

## Construire la version Release

1. Créer la clé : *Build > Generate Signed App Bundle > Create new...* (garder le .jks et les mots de passe en lieu sûr : leur perte empêche toute mise à jour sans procédure de réinitialisation Google).
2. Copier `keystore.properties.example` en `keystore.properties` et le renseigner.
3. `./gradlew bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab` (format exigé par Google Play).
4. Pour un APK installable directement : `./gradlew assembleRelease`.
5. Conserver `app/build/outputs/mapping/release/mapping.txt` de chaque version (traces de crash lisibles).

## Avant publication (checklist)

- [ ] Compiler et lancer `./gradlew test lint` (code non compilé à la génération).
- [ ] Tester la build **release** (minifiée) : tri, Auto-Pilot planifié, puis mise à jour de l'appli par-dessus une version précédente (vérifie les règles R8 sur les noms persistés).
- [ ] Retirer `fallbackToDestructiveMigrationFrom` et figer le schéma (`app/schemas/`).
- [ ] Écran de démarrage (API SplashScreen).
- [ ] Déclaration "All files access" + vidéo de démonstration dans la Play Console (texte prêt dans `store/`).
- [ ] Héberger les règles de confidentialité et renseigner leur URL.
- [ ] Tests sur Android 8, 11, 14 et 15 (permissions différentes).
- [ ] LeakCanary en debug pour confirmer l'absence de fuite.

## 5. Prochaines étapes suggérées

1. **Mode dégradé SAF** si l'accès complet est refusé : `ACTION_OPEN_DOCUMENT_TREE` + `DocumentFile` (dépendance déjà incluse).
2. Swipe haut avec **appui long** -> sélecteur de dossier personnalisé (paramètre `customTargetDir` déjà prévu).
3. Tests : unitaires sur `SafeFileOperations.reserveTarget`, `FileCategorizer` et `DuplicateScannerUseCase` (avec faux FileHasher) ; instrumentés sur `FileRepositoryImpl` et `AutoPilotRepositoryImpl` avec un dossier temporaire.
