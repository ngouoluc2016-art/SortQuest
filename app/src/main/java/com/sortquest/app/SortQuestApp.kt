package com.sortquest.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.sortquest.app.core.image.LocalThumbnailFetcher
import com.sortquest.app.core.image.LocalThumbnailKeyer
import kotlinx.coroutines.Dispatchers
import com.sortquest.app.core.feedback.FeedbackManager
import com.sortquest.app.workers.TrashCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SortQuestApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var feedbackManager: FeedbackManager

    /** WorkManager créé avec la factory Hilt (initialiseur par défaut retiré dans le manifeste). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        TrashCleanupWorker.schedule(WorkManager.getInstance(this))
        feedbackManager.preload() // ~50 Ko de sons décodés en tâche de fond par SoundPool
    }

    /**
     * Coil configuré pour des milliers de fichiers locaux :
     *  - cache mémoire plafonné à 15 % de la mémoire de l'appli (pas d'OutOfMemory) ;
     *  - pas de cache disque Coil : il ne sert qu'aux images réseau, et l'appli n'a pas Internet.
     *    Les miniatures coûteuses (PDF, vidéo) ont leur propre cache disque (LocalThumbnailFetcher) ;
     *  - RGB_565 autorisé pour les images opaques : 2 fois moins de mémoire par miniature ;
     *  - au plus 2 décodages simultanés : le thread UI garde toujours la main.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache(null)
            .components {
                add(LocalThumbnailKeyer())
                add(LocalThumbnailFetcher.Factory(this@SortQuestApp))
            }
            .allowRgb565(true)
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
            .crossfade(150)
            .build()
}
