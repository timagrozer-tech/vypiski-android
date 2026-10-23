package ru.scanner291.core.storage.di

import android.graphics.BitmapFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.scanner291.core.storage.PageDecoder

/** Декодер изображений страниц: системный BitmapFactory. В тестах Robolectric (где JPEG не декодируется) заменяется. */
@Module
@InstallIn(SingletonComponent::class)
object DecoderModule {
    @Provides
    fun pageDecoder(): PageDecoder = PageDecoder { f, sample ->
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
