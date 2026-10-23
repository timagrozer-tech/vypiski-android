package ru.scanner291.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import ru.scanner291.core.storage.di.PreferencesModule
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/** В тестах — свой файл настроек на каждый компонент Hilt (DataStore не допускает два хранилища на один файл). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PreferencesModule::class])
object TestPreferencesModule {
    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(context.cacheDir, "prefs-${UUID.randomUUID()}.preferences_pb") }
}
