package ru.revvl.cameralab

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageFormat
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * One photo session, owned by the activity. Caller must cancel it onStop/onDestroy.
 *
 * Threading: start()/cancel() and every UI callback run on the main thread; capture
 * callbacks, decoding, merging and saving run on [worker]. The worker is never
 * interrupted and is shut down only when no CameraX callback is pending on it, so an
 * in-flight ImageProxy is always delivered and closed (v0.3 used shutdownNow(), which
 * could reject CameraX's callback and leak the image).
 */
class BurstCaptureController(
    private val context: Context,
    private val capture: ImageCapture,
    private val onProgress: (String) -> Unit,
    /** (frames captured so far, merging in progress) for the shutter progress ring. */
    private val onStage: (Int, Boolean) -> Unit = { _, _ -> },
    /** (merged result, unprocessed reference for A/B or null, merge result). */
    private val onSuccess: (Uri, Uri?, BurstDenoiser.Result) -> Unit,
    private val onFailure: (String) -> Unit,
    /**
     * A/B: also save the reference frame without merging, at the same size and JPEG quality,
     * so the comparison isolates the merge (not resolution or compression).
     */
    private val saveReference: Boolean = false
) {
    private val main = ContextCompat.getMainExecutor(context)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "revvl-burst").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val cancelled = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val captureInFlight = AtomicBoolean(false)
    /** Accessed only on [worker]. */
    private val frames = mutableListOf<BurstDenoiser.Frame>()

    fun start() = onMain { takeNext() }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        // A pending callback will see the flag, close its image and shut the worker down.
        if (!captureInFlight.get()) worker.shutdown()
    }

    private fun takeNext() {
        if (cancelled.get() || finished.get()) return
        onProgress("СТЕК ×3 · КАДР ${frames.size + 1}/${BurstDenoiser.FRAME_COUNT}")
        onStage(frames.size, false)
        captureInFlight.set(true)
        try {
            capture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    captureInFlight.set(false)
                    if (cancelled.get()) {
                        image.close()
                        release()
                        return
                    }
                    try {
                        if (image.format != ImageFormat.JPEG) {
                            throw IOException("Для серии требуется JPEG, получен формат ${image.format}")
                        }
                        val frame = decode(image)
                        val first = frames.firstOrNull()
                        if (first != null && (first.width != frame.width || first.height != frame.height)) {
                            throw IOException("Ориентация или размер кадра изменились во время серии")
                        }
                        frames.add(frame)
                    } catch (e: Exception) {
                        fail("Ошибка кадра: ${e.localizedMessage}")
                        return
                    } catch (oom: OutOfMemoryError) {
                        fail("Недостаточно памяти для серии")
                        return
                    } finally {
                        image.close()
                    }
                    if (frames.size < BurstDenoiser.FRAME_COUNT) {
                        // If cancel() runs first, takeNext() returns and cancel() already
                        // shut the idle worker down.
                        onMain { takeNext() }
                    } else {
                        process()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInFlight.set(false)
                    if (cancelled.get()) release()
                    else fail("Сбой серии: ${exception.localizedMessage}")
                }
            })
        } catch (e: Exception) {
            captureInFlight.set(false)
            fail("Съёмка не началась: ${e.localizedMessage}")
        }
    }

    /** Runs on [worker]. */
    private fun process() {
        try {
            onMain {
                onProgress("СТЕК ×3 · СОВМЕЩЕНИЕ И СОХРАНЕНИЕ")
                onStage(BurstDenoiser.FRAME_COUNT, true)
            }
            val input = frames.toList()
            frames.clear()
            // A/B needs the untouched reference, so the in-place merge is used only without A/B.
            val reference = if (saveReference) input[1] else null
            val result = BurstDenoiser.denoise(input, BurstDenoiser.Options(
                reuseReferenceBuffer = !saveReference,
                isCancelled = { cancelled.get() }
            ))
            if (cancelled.get()) return release()
            val stamp = CameraText.fileStamp(System.currentTimeMillis())
            val uri = save(result.frame, "REVVL_STACK_$stamp.jpg")
            // The A/B reference is optional: its failure never discards the merged photo.
            val refUri = reference?.let { runCatching { save(it, "REVVL_STACK_${stamp}_REF.jpg") }.getOrNull() }
            complete { onSuccess(uri, refUri, result) }
        } catch (oom: OutOfMemoryError) {
            fail("Недостаточно памяти для объединения кадров")
        } catch (e: Exception) {
            if (cancelled.get()) release()
            else fail("Не удалось объединить кадры: ${e.localizedMessage}")
        }
    }

    private fun decode(image: ImageProxy): BurstDenoiser.Frame {
        val buffer = image.planes[0].buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Некорректный JPEG")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > BurstDenoiser.MAX_LONG_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: throw IOException("JPEG не декодирован")
        val scale = (BurstDenoiser.MAX_LONG_EDGE.toFloat() / max(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val oriented = try {
            // Apply ImageProxy rotation metadata; JPEG decoding alone does not rotate the bitmap.
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(image.imageInfo.rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
        if (oriented !== bitmap) bitmap.recycle()
        try {
            val outWidth = oriented.width
            val outHeight = oriented.height
            val pixels = IntArray(outWidth * outHeight)
            oriented.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
            return BurstDenoiser.Frame(outWidth, outHeight, pixels)
        } finally {
            oriented.recycle()
        }
    }

    private fun save(frame: BurstDenoiser.Frame, name: String): Uri {
        if (cancelled.get()) throw IOException("Съёмка прервана")
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MediaOutputs.JPEG_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, MediaOutputs.PHOTO_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Галерея не принимает файл")
        try {
            if (cancelled.get()) throw IOException("Съёмка прервана")
            val bitmap = Bitmap.createBitmap(frame.argb, frame.width, frame.height, Bitmap.Config.ARGB_8888)
            try {
                val stream = resolver.openOutputStream(uri) ?: throw IOException("Нет доступа к файлу")
                stream.use {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)) {
                        throw IOException("Не удалось записать JPEG")
                    }
                }
            } finally { bitmap.recycle() }
            if (cancelled.get()) throw IOException("Съёмка прервана")
            val published = resolver.update(uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (published <= 0) throw IOException("Галерея не подтвердила файл")
            return uri
        } catch (e: Throwable) {
            // Never leave an IS_PENDING row behind.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun complete(callback: () -> Unit) {
        if (finished.compareAndSet(false, true) && !cancelled.get()) {
            onMain(callback)
        }
        release()
    }

    private fun fail(message: String) {
        if (finished.compareAndSet(false, true) && !cancelled.get()) {
            onMain { onFailure(message) }
        }
        release()
    }

    /** Called with no capture in flight; idempotent. Queued worker tasks still run. */
    private fun release() {
        frames.clear()
        worker.shutdown()
    }

    private fun onMain(action: () -> Unit) = main.execute {
        if (!cancelled.get()) action()
    }
}
