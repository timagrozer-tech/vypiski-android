package ru.scanner291.app

import android.graphics.Bitmap
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import ru.scanner291.core.storage.PageDecoder
import ru.scanner291.core.storage.di.DecoderModule
import javax.imageio.ImageIO

/**
 * Robolectric не декодирует JPEG (BitmapFactory возвращает 0×0, ImageDecoder — «только на Android»; проверено
 * диагностикой снимков 2026-10-23). В тестах приложения файл читает javax.imageio, пиксели переносятся в Bitmap —
 * так на снимках экранов видны настоящие страницы. В приложении используется системный BitmapFactory.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DecoderModule::class])
object TestDecoderModule {
    @Provides
    fun pageDecoder(): PageDecoder = PageDecoder { f, sample ->
        val img = ImageIO.read(f) ?: return@PageDecoder null
        val w = img.width / sample; val h = img.height / sample
        val px = IntArray(w * h) { i -> img.getRGB((i % w) * sample, (i / w) * sample) }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
