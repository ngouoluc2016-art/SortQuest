package com.sortquest.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sortquest.app.data.local.dao.AutoRuleDao
import com.sortquest.app.data.local.dao.FileDao
import com.sortquest.app.data.local.dao.HistoryDao
import com.sortquest.app.data.local.dao.TrashDao
import com.sortquest.app.data.local.dao.UserProgressDao
import com.sortquest.app.data.local.entity.AutoRuleEntity
import com.sortquest.app.data.local.entity.FileEntity
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.local.entity.TrashEntity
import com.sortquest.app.data.local.entity.UserProgressEntity

/**
 * v2 : historique enrichi, règles avec taille minimale, progression avec série et célébration.
 * v3 : cache d'empreintes partielles (doublons), nom d'origine dans la corbeille.
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
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun historyDao(): HistoryDao
    abstract fun trashDao(): TrashDao
    abstract fun autoRuleDao(): AutoRuleDao
    abstract fun userProgressDao(): UserProgressDao

    companion object {
        const val NAME = "sortquest.db"
    }
}
