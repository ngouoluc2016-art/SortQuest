# =====================================================================================
# SortQuest - règles R8 / ProGuard pour la build Release (minifyEnabled + shrinkResources)
#
# Principe : Room, Hilt, WorkManager, Coil, DataStore, Compose et les coroutines fournissent
# déjà leurs règles "consumer" dans leurs AAR. On n'ajoute ici QUE ce que R8 ne peut pas deviner :
# les noms qui sont PERSISTÉS (base de données, WorkManager) et qui doivent donc rester
# identiques d'une version de l'appli à l'autre.
# =====================================================================================

# --- Traces de crash lisibles (avec le fichier mapping.txt de chaque release) -----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Annotations et génériques : utilisés par Room, Hilt et Kotlin
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- 1. Enums persistés par leur NOM -----------------------------------------------------
# Room stocke ActionType, FileStatus, FileKind, FileCategory, ActionSource sous forme de texte
# (enum.name) ; les badges sont stockés en CSV de noms ; ScanPhase transite par WorkManager.
# Si R8 renommait ces constantes, une mise à jour de l'appli ne relirait plus ses propres données.
-keepclassmembers enum com.sortquest.app.domain.model.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- 2. Workers : nom de classe persisté par WorkManager ---------------------------------
# WorkManager enregistre le NOM de classe de chaque tâche planifiée (nettoyage quotidien,
# Auto-Pilot toutes les 12 h). HiltWorkerFactory retrouve le worker par ce même nom.
# Un renommage entre deux versions = tâches planifiées introuvables après mise à jour.
-keep class com.sortquest.app.workers.** extends androidx.work.ListenableWorker {
    *;
}

# --- 3. Room ----------------------------------------------------------------------------
# Les règles consumer de Room couvrent le code généré. On garde en plus les entités et
# les POJO de requête (ex. IdPath) : leurs champs correspondent aux colonnes SQL.
-keep class com.sortquest.app.data.local.entity.** { *; }
-keep class com.sortquest.app.data.local.dao.IdPath { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- 4. Hilt / Dagger -------------------------------------------------------------------
# Couvert par les règles consumer de Hilt (composants générés, @HiltViewModel, @HiltWorker,
# @EntryPoint). Rien à ajouter ; ligne gardée pour documenter la vérification.
-dontwarn com.google.errorprone.annotations.**

# --- 5. Sérialisation -------------------------------------------------------------------
# Le projet n'utilise AUCUNE sérialisation par réflexion (pas de Gson, Moshi ni Parcelable) :
# les données passent par Room, DataStore Preferences (types primitifs) et WorkManager Data
# (types primitifs). Si kotlinx.serialization est ajouté plus tard, décommenter :
# -keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# -keepclassmembers @kotlinx.serialization.Serializable class com.sortquest.app.** {
#     *** Companion;
#     kotlinx.serialization.KSerializer serializer(...);
# }

# --- 6. Coil + Okio (fetcher de miniatures) ---------------------------------------------
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# --- 7. Coroutines ----------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- 8. Release : suppression des logs de debug -----------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
