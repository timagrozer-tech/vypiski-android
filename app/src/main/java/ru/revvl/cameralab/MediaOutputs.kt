package ru.revvl.cameralab

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import java.io.IOException
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions

/**
 * MediaStore destinations. CameraX manages IS_PENDING for these outputs itself;
 * BurstCaptureController writes its own file and handles IS_PENDING explicitly.
 */
object MediaOutputs {
    const val FOLDER = "REVVL Camera Lab"
    const val JPEG_MIME = "image/jpeg"
    const val DNG_MIME = "image/x-adobe-dng"
    const val MP4_MIME = "video/mp4"
    val PHOTO_RELATIVE_PATH: String get() = "${Environment.DIRECTORY_DCIM}/$FOLDER"
    val VIDEO_RELATIVE_PATH: String get() = "${Environment.DIRECTORY_MOVIES}/$FOLDER"

    val DOWNLOAD_RELATIVE_PATH: String get() = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"

    /** Saves a UTF-8 text file to Download/REVVL Camera Lab (no storage permission on API 29+). */
    fun saveText(resolver: ContentResolver, name: String, text: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Хранилище не принимает файл")
        try {
            (resolver.openOutputStream(uri) ?: throw IOException("Нет доступа к файлу")).use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    fun photo(resolver: ContentResolver, name: String, mime: String): ImageCapture.OutputFileOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, PHOTO_RELATIVE_PATH)
        }
        return ImageCapture.OutputFileOptions.Builder(
            resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
    }

    fun video(resolver: ContentResolver, name: String): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MP4_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, VIDEO_RELATIVE_PATH)
        }
        return MediaStoreOutputOptions.Builder(
            resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
    }
}
