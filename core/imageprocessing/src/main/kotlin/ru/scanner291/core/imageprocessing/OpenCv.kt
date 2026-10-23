package ru.scanner291.core.imageprocessing

import android.util.Log
import org.opencv.android.OpenCVLoader

/** Однократная загрузка нативной библиотеки OpenCV из APK (без сети и без OpenCV Manager). */
object OpenCv {
    @Volatile private var loaded: Boolean? = null

    fun ensureLoaded(): Boolean {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val ok = runCatching { OpenCVLoader.initLocal() }.getOrElse { Log.e("Scanner291CV", "OpenCV не загрузился", it); false }
            loaded = ok
            return ok
        }
    }
}
