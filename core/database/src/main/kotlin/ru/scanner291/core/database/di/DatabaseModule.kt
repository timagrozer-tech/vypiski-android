package ru.scanner291.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.scanner291.core.database.DocumentDao
import ru.scanner291.core.database.ScannerDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): ScannerDatabase =
        Room.databaseBuilder(context, ScannerDatabase::class.java, "scanner291.db")
            // Режим WAL (по умолчанию для Room) — устойчивость к обрыву записи.
            // Только явные миграции: разрушающая миграция запрещена — документы пользователя не теряются.
            .addMigrations(*ScannerDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun documentDao(db: ScannerDatabase): DocumentDao = db.documentDao()
}
