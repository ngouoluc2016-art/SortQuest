package com.sortquest.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.sortquest.app.data.file.Sha256FileHasher
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.organization.OrganizationRepositoryImpl
import com.sortquest.app.data.local.dao.UserProgressDao
import com.sortquest.app.data.repository.AutoPilotRepositoryImpl
import com.sortquest.app.data.repository.FileRepositoryImpl
import com.sortquest.app.data.repository.ProgressRepositoryImpl
import com.sortquest.app.data.repository.RuleRepositoryImpl
import com.sortquest.app.data.repository.TrashRepositoryImpl
import com.sortquest.app.data.settings.SettingsRepositoryImpl
import com.sortquest.app.domain.repository.AutoPilotRepository
import com.sortquest.app.domain.repository.FileHasher
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.repository.OrganizationRepository
import com.sortquest.app.domain.repository.ProgressRepository
import com.sortquest.app.domain.repository.RuleRepository
import com.sortquest.app.domain.repository.SettingsRepository
import com.sortquest.app.domain.repository.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // v1/v2 jamais publiées : on repart de zéro. À partir de la v3 (APK 1.0.0 installé
            // par l'utilisateur), chaque évolution de schéma passe par une migration explicite.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideUserProgressDao(db: AppDatabase): UserProgressDao = db.userProgressDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds @Singleton
    abstract fun bindRuleRepository(impl: RuleRepositoryImpl): RuleRepository

    @Binds @Singleton
    abstract fun bindAutoPilotRepository(impl: AutoPilotRepositoryImpl): AutoPilotRepository

    @Binds @Singleton
    abstract fun bindTrashRepository(impl: TrashRepositoryImpl): TrashRepository

    @Binds @Singleton
    abstract fun bindFileHasher(impl: Sha256FileHasher): FileHasher

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindOrganizationRepository(impl: OrganizationRepositoryImpl): OrganizationRepository
}
