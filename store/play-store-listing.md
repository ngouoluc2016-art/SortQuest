# SortQuest - Fiche Google Play (français)

Textes prêts à coller dans la Play Console. Les longueurs ont été vérifiées par script
(voir la fin du document).

---

## Titre de l'application (30 caractères max)

<!-- TITLE -->
SortQuest : tri de fichiers
<!-- /TITLE -->

## Description courte (80 caractères max)

<!-- SHORT -->
Triez vos fichiers d'un geste, chassez les doublons et libérez de l'espace.
<!-- /SHORT -->

## Description longue (4 000 caractères max)

<!-- LONG -->
Votre dossier Téléchargements déborde ? Des factures, captures d'écran, APK et archives s'accumulent sans ordre ? SortQuest transforme le rangement de votre téléphone en un jeu rapide et satisfaisant.

UN GESTE PAR FICHIER
Chaque fichier apparaît sous forme de carte avec son aperçu : photo, vidéo, première page d'un PDF ou pochette d'un morceau.
• Glissez à droite : le fichier part dans le dossier suggéré (Factures, Contrats, Médias...).
• Glissez à gauche : il va dans la corbeille, récupérable pendant 7 jours.
• Glissez vers le haut : il rejoint votre coffre-fort.
• Glissez vers le bas : vous le reverrez plus tard.
Un doute ? Le bouton Annuler remet le fichier exactement à sa place, même après avoir fermé l'application.

UN TRI INTELLIGENT
SortQuest analyse les noms et les types de fichiers pour proposer le bon dossier : « facture », « EDF », « contrat », « bulletin de paie », « attestation »... Les documents administratifs sont reconnus et classés par thème, les photos et vidéos par année.

CHASSE AUX DOUBLONS ET AUX FICHIERS LOURDS
• Détection des copies identiques au contenu près, même si elles portent des noms différents.
• Vous choisissez l'exemplaire à garder, SortQuest propose de supprimer les autres.
• Le Top 20 des fichiers les plus volumineux (plus de 50 Mo) pour gagner de la place en quelques secondes.

AUTO-PILOT : LE RANGEMENT AUTOMATIQUE
Créez vos propres règles en langage simple : « tout PDF contenant EDF va dans Documents/Factures ». Combinez mot-clé, extension et taille minimale. L'Auto-Pilot range votre dossier Téléchargements à la demande ou toutes les 12 heures. Chaque passage peut être annulé en un geste.

PROGRESSEZ EN RANGEANT
• Gagnez de l'XP à chaque tri et montez de niveau, de « Novice du Rangement » à « Maître du Stockage ».
• Suivez la santé de votre stockage sur une jauge en temps réel.
• Débloquez des badges : Premier Tri, Chasseur de Doublons, 1 Go Libéré, Maître de l'Auto-Pilot, Archiviste Régulier.
• Relevez la Session Express : 20 fichiers en 2 minutes, avec des combos et des confettis à la clé.
• Entretenez votre série de jours consécutifs.

SÛR PAR CONCEPTION
• Aucun fichier n'est jamais écrasé : en cas de doublon de nom, SortQuest crée « fichier (1).pdf ».
• Corbeille interne de 7 jours avant toute suppression définitive.
• Historique complet de toutes les actions, annulables une par une.
• Les dossiers système et les données des autres applications ne sont jamais modifiés.

100 % HORS LIGNE, 100 % PRIVÉ
SortQuest ne demande pas l'accès à Internet. Vos fichiers, leurs noms et vos statistiques restent sur votre téléphone : rien n'est collecté, rien n'est envoyé, aucune publicité, aucun compte à créer.

UNE EXPÉRIENCE SOIGNÉE
Mode sombre aux accents néon, animations fluides, retours haptiques et sons discrets (désactivables séparément, coupés automatiquement en mode silencieux).

POURQUOI L'ACCÈS À TOUS LES FICHIERS ?
Ranger un téléphone, c'est déplacer des documents, des archives et des APK, pas seulement des photos. Android réserve cela à l'autorisation « Accès à tous les fichiers ». SortQuest l'utilise uniquement pour analyser, déplacer et supprimer les fichiers que vous lui demandez de traiter. Vous pouvez la retirer à tout moment dans les paramètres d'Android.

Compatible Android 8.0 et versions ultérieures.
<!-- /LONG -->

---

## Déclaration d'autorisation « Accès à tous les fichiers » (Play Console)

À remplir dans : *Contenu de l'application > Autorisations sensibles > Accès à tous les fichiers*.

**Catégorie d'utilisation déclarée :** Gestionnaire de fichiers (gestion et maintenance des fichiers et dossiers du stockage partagé).

**Fonctionnalité principale qui nécessite l'autorisation (texte proposé) :**

> SortQuest est un gestionnaire de fichiers dont la fonction principale est d'organiser l'ensemble du stockage partagé de l'utilisateur : il analyse les dossiers Download, Documents, DCIM, Pictures, Movies et Music, déplace chaque fichier vers un dossier thématique choisi par l'utilisateur, détecte les fichiers en double par comparaison de leur contenu, et place les fichiers supprimés dans une corbeille de 7 jours avant suppression définitive.
>
> Ces opérations portent sur tous les types de fichiers (PDF, documents bureautiques, archives ZIP/RAR, APK, audio, images, vidéos), y compris des fichiers non multimédias créés par d'autres applications. L'API MediaStore ne donne pas accès en écriture aux fichiers non multimédias appartenant à d'autres applications, et le Storage Access Framework imposerait de choisir chaque fichier ou dossier un par un, ce qui rendrait impossibles l'analyse globale, la détection des doublons et le rangement automatique (« Auto-Pilot »), qui sont le cœur de l'application.
>
> L'autorisation est demandée par un écran d'accueil qui explique son usage avant de renvoyer l'utilisateur vers le réglage système « Accès à tous les fichiers ». Elle peut être retirée à tout moment. L'application ne déclare pas la permission INTERNET : aucune donnée issue des fichiers ne quitte l'appareil. Les dossiers Android/data et Android/obb ne sont jamais modifiés.

**Vidéo de démonstration (demandée par Google) :** 30 à 60 s montrant l'écran d'accueil, l'octroi de l'autorisation, un swipe de rangement d'un PDF, l'écran des doublons et une restauration depuis la corbeille.

---

## Sécurité des données (Play Console > Sécurité des données)

| Question | Réponse |
|---|---|
| L'application collecte-t-elle des données utilisateur ? | Non |
| L'application partage-t-elle des données avec des tiers ? | Non |
| Les données sont-elles chiffrées en transit ? | Sans objet (aucune transmission) |
| L'utilisateur peut-il demander la suppression de ses données ? | Toutes les données sont locales : la désinstallation ou « Effacer les données » les supprime |

Justification : l'application ne déclare pas `android.permission.INTERNET` (retirée explicitement du manifeste fusionné). Les noms de fichiers, l'historique et la progression sont stockés uniquement dans la base locale de l'appareil.

---

## Règles de confidentialité (URL exigée par Google Play)

À héberger sur une page web publique (GitHub Pages, Google Sites...) puis à renseigner dans la Play Console.

> **Règles de confidentialité de SortQuest**
>
> Dernière mise à jour : [date de publication]
>
> SortQuest est une application de rangement de fichiers qui fonctionne entièrement hors ligne.
>
> **Données traitées.** Pour ranger vos fichiers, l'application lit le nom, le type, la taille, la date de modification et le contenu des fichiers présents dans le stockage partagé de votre appareil (le contenu sert uniquement à calculer une empreinte pour détecter les doublons). Elle enregistre localement l'historique de vos actions, vos règles de rangement, votre progression dans le jeu et vos préférences (sons, vibrations).
>
> **Aucune collecte, aucun partage.** Ces informations restent sur votre appareil. L'application ne dispose pas de l'autorisation d'accès à Internet et ne peut donc transmettre aucune donnée. Elle ne contient ni publicité, ni outil de mesure d'audience, ni compte utilisateur.
>
> **Autorisation « Accès à tous les fichiers ».** Elle est utilisée uniquement pour analyser, déplacer, mettre à la corbeille et supprimer les fichiers que vous choisissez de traiter, ou que vos règles Auto-Pilot désignent. Vous pouvez la retirer à tout moment dans les paramètres d'Android.
>
> **Conservation et suppression.** Les fichiers mis à la corbeille sont supprimés définitivement après 7 jours. La désinstallation de l'application supprime sa base de données locale.
>
> **Contact :** [adresse e-mail du développeur]

---

## Notes de version 1.0.0 (500 caractères max)

<!-- RELEASE -->
Première version de SortQuest !
• Tri des fichiers par swipe avec aperçus (photos, vidéos, PDF, audio)
• Détection des doublons et Top 20 des fichiers lourds
• Auto-Pilot : règles de rangement automatique
• Corbeille de 7 jours et annulation de chaque action
• XP, niveaux, badges et Session Express
• 100 % hors ligne
<!-- /RELEASE -->

---

## Éléments graphiques à préparer

| Élément | Format exigé | Statut |
|---|---|---|
| Icône | PNG 32 bits, 512 x 512 px | `store/icon-512-playstore.png` (fourni) |
| Image de présentation | 1024 x 500 px, JPEG ou PNG 24 bits | À créer |
| Captures téléphone | 2 à 8, ratio 16:9 ou 9:16 | À réaliser sur émulateur |

Ordre de captures conseillé : 1. carte de tri avec tampon « CLASSER », 2. tableau de bord (jauge, niveau, badges), 3. doublons, 4. règles Auto-Pilot, 5. corbeille, 6. écran de confidentialité de l'accueil.

## Mots-clés visés (ASO)

Google Play n'a pas de champ « mots-clés » : il indexe le titre et les descriptions. Termes intégrés naturellement ci-dessus, sans répétition abusive : *ranger, fichiers, téléchargements, doublons, libérer de l'espace, stockage, gestionnaire de fichiers, factures, PDF, corbeille, automatique, hors ligne*.

À éviter (règles Google Play sur les métadonnées) : superlatifs non prouvés (« meilleur », « n° 1 »), mention de prix ou de promotion dans le titre, emojis dans le titre et la description courte, témoignages non attribués.

## Vérification des longueurs (générée par script)

| Champ | Caractères | Limite | Statut |
|---|---|---|---|
| TITLE | 27 | 30 | OK |
| SHORT | 75 | 80 | OK |
| LONG | 3308 | 4000 | OK |
| RELEASE | 318 | 500 | OK |
