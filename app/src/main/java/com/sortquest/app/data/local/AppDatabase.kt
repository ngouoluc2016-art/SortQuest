package com.sortquest.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sortquest.app.data.local.dao.AutoRuleDao
import com.sortquest.app.data.local.dao.FileDao
import com.sortquest.app.data.local.dao.HistoryDao
import com.sortquest.app.data.local.dao.ThematicFolderDao
import com.sortquest.app.data.local.dao.TrashDao
import com.sortquest.app.data.local.dao.UserProgressDao
import com.sortquest.app.data.local.entity.AutoRuleEntity
import com.sortquest.app.data.local.entity.FileEntity
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.local.entity.ThematicFolderEntity
import com.sortquest.app.data.local.entity.TrashEntity
import com.sortquest.app.data.local.entity.UserProgressEntity

/**
 * v2 : historique enrichi, règles avec taille minimale, progression avec série et célébration.
 * v3 : cache d'empreintes partielles (doublons), nom d'origine dans la corbeille.
 * v4 : gamme de dossiers thématiques + destination lisible des fichiers (1re version publiée
 *      après une installation réelle : migration EXPLICITE, aucune donnée perdue).
 * v1 et v2 jamais publiées : migration destructive depuis ces versions (voir DatabaseModule).
 * À partir de la première publication, chaque changement de schéma = une Migration explicite.
 */
@Database(
    entities = [
        FileEntity::class,
        SortHistoryEntity::class,
        TrashEntity::class,
        AutoRuleEntity::class,
        UserProgressEntity::class,
        ThematicFolderEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun historyDao(): HistoryDao
    abstract fun trashDao(): TrashDao
    abstract fun autoRuleDao(): AutoRuleDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun thematicFolderDao(): ThematicFolderDao

    companion object {
        const val NAME = "sortquest.db"

        /**
         * 3 -> 4 : nouvelle table + 2 colonnes NULLABLES (aucune valeur par défaut nécessaire).
         * Le SQL reproduit exactement ce que Room génère pour ThematicFolderEntity, sinon Room
         * refuserait d'ouvrir la base. Testé en vrai : mise à jour de la 1.0.0 vers la 1.1.0 sur émulateur.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `thematic_folders` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`relativePath` TEXT NOT NULL, `icon` TEXT NOT NULL, `keywordsCsv` TEXT NOT NULL, " +
                        "`extensionsCsv` TEXT NOT NULL, `kindsCsv` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`builtIn` INTEGER NOT NULL, `locked` INTEGER NOT NULL, `splitByYear` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("ALTER TABLE `files` ADD COLUMN `suggestedLabel` TEXT")
                db.execSQL("ALTER TABLE `files` ADD COLUMN `suggestedIcon` TEXT")
            }
        }
    }
}
