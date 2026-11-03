package ru.revvl.cameralab

import android.Manifest
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Instrumented checks for a real device or an emulator. They run ONLY via the manual
 * "Instrumented tests" workflow or Android Studio; nothing here runs on push.
 * No photos are taken; the only file written (a diagnostics text) is deleted again.
 */
@RunWith(AndroidJUnit4::class)
class CameraCapabilitiesDeviceTest {
    @get:Rule val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capabilities(): CameraCapabilities {
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
        return CameraCapabilities(context).also { it.provider = provider }
    }

    @Test fun everyPublicLensReportsJpegAndConsistentProSupport() {
        val caps = capabilities()
        val lenses = caps.discoverLenses()
        assumeTrue("no camera on this device/emulator", lenses.isNotEmpty())
        for (lens in lenses) {
            val sel = caps.selectorFor(lens.id)
            assertTrue("JPEG for ${lens.id}", ImageCapture.OUTPUT_FORMAT_JPEG in caps.photoFormats(sel))
            val pro = caps.proSupport(lens.id)
            // Manual focus is only offered together with MANUAL_SENSOR.
            assertTrue(!pro.manualFocus || pro.manualSensor)
            Log.i(TAG, "lens ${lens.id}: ${lens.label}; video=${caps.videoQualities(sel).size}; pro=$pro")
        }
    }

    @Test fun probePlanCoversEveryLensAndStaysBounded() {
        val caps = capabilities()
        val lenses = caps.discoverLenses()
        assumeTrue(lenses.isNotEmpty())
        val plan = ProbePlan.build(lenses.map {
            val sel = caps.selectorFor(it.id)
            ProbeLens(it.id, it.label, caps.photoFormats(sel), emptyList(), emptyList())
        }, { CameraLabels.photoFormat(it) }, { "$it" }, ImageCapture.OUTPUT_FORMAT_JPEG)
        assertTrue(plan.size <= ProbePlan.MAX_CASES)
        assertTrue(plan.map { it.lensId }.toSet().containsAll(lenses.map { it.id }.take(ProbePlan.MAX_CASES)))
    }

    companion object { const val TAG = "REVVLDeviceTest" }
}

@RunWith(AndroidJUnit4::class)
class MediaOutputsDeviceTest {
    @Test fun diagnosticsTextIsWrittenCompletelyAndPublished() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val text = "REVVL test ${System.nanoTime()}\nвторая строка"
        val uri = MediaOutputs.saveText(resolver, "REVVL_instrumented_${System.nanoTime()}.txt", text)
        try {
            val read = resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(text, read)
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null)!!.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0)) // never left pending
            }
        } finally {
            resolver.delete(uri, null, null)
        }
        assertTrue(ContentUris.parseId(uri) > 0)
    }
}

@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleDeviceTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    /** Start, background, foreground and recreate the viewfinder without a crash. */
    @Test fun survivesBackgroundAndRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(1500) // let CameraX bind
            s.moveToState(Lifecycle.State.CREATED) // like pressing Home
            s.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(800)
            s.recreate()
            Thread.sleep(800)
            s.onActivity { assertTrue(!it.isFinishing) }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class BurstDenoiserDeviceSpeedTest {
    /** On-device speed of the СТЕК ×3 merge at the real working size (logcat tag REVVLDeviceTest). */
    @Test fun mergeAtWorkingSizeFinishesInReasonableTime() {
        val w = 2304; val h = 1728
        val rnd = java.util.Random(1)
        val frames = List(3) {
            BurstDenoiser.Frame(w, h, IntArray(w * h) { i ->
                val v = (((i % w) / 9 + (i / w) / 7) % 200 + 20 + rnd.nextInt(9) - 4).coerceIn(0, 255)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            })
        }
        BurstDenoiser.denoise(frames) // warm-up (JIT)
        val r = BurstDenoiser.denoise(frames, BurstDenoiser.Options(reuseReferenceBuffer = true))
        Log.i(CameraCapabilitiesDeviceTest.TAG, "burst merge ${w}x$h: ${r.stats.elapsedMs} ms, noise −${(r.stats.noiseReduction * 100).toInt()}%")
        assertEquals(3, r.usedFrames)
        assertTrue("merge took ${r.stats.elapsedMs} ms", r.stats.elapsedMs < 10_000)
    }
}
