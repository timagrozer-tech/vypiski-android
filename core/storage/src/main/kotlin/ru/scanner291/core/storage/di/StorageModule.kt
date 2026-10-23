package ru.scanner291.core.storage.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.storage.DocumentPaths
import javax.inject.Qualifier
import javax.inject.Singleton

/** Область корутин уровня процесса — для работы, которая не должна отменяться вместе с экраном. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun documentPaths(@ApplicationContext context: Context): DocumentPaths =
        DocumentPaths(context.filesDir, context.cacheDir)

    @Provides @Singleton
    fun operationJournal(paths: DocumentPaths): OperationJournal = OperationJournal(paths.journal)

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
