package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.DriftEngine
import com.example.model.DriftParameters
import com.example.model.FaceLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Likeness Drift Lab", appName)
    }

    @Test
    fun `zero parameters results in zero drift`() {
        val landmarks = FaceLandmarks()
        val params = DriftParameters()
        val metrics = DriftEngine.calculateMetrics(landmarks, params, 512, 512)
        assertEquals(100f, metrics.likenessRetentionScore, 0.1f)
        assertEquals("PERFECT ALIGNMENT", metrics.statusVerdict)
    }

    @Test
    fun `compound drift reduces likeness score`() {
        val landmarks = FaceLandmarks()
        val params = DriftParameters(
            leftEyeShiftX = 0.5f,
            leftEyeShiftY = 0.4f,
            mouthShiftY = 0.5f,
            textureSoftening = 0.4f,
            cumulativeMultiplier = 1.3f
        )
        val metrics = DriftEngine.calculateMetrics(landmarks, params, 512, 512)
        assertTrue(metrics.likenessRetentionScore < 90f)
        assertTrue(metrics.meanFeatureShiftPx > 0f)
    }

    @Test
    fun `real image difference detects delta between identical vs different images`() {
        val bmp1 = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val bmp2 = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)

        // Fill with white
        val canvas1 = android.graphics.Canvas(bmp1)
        canvas1.drawColor(android.graphics.Color.WHITE)
        val canvas2 = android.graphics.Canvas(bmp2)
        canvas2.drawColor(android.graphics.Color.WHITE)

        val (_, identicalMetrics) = DriftEngine.calculateRealImageDifference(bmp1, bmp2)
        assertEquals(100f, identicalMetrics.likenessRetentionScore, 0.1f)

        // Alter bmp2 pixels directly
        val differentBmp = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                differentBmp.setPixel(x, y, android.graphics.Color.RED)
            }
        }
        val (_, changedMetrics) = DriftEngine.calculateRealImageDifference(bmp1, differentBmp)
        assertTrue("Likeness score should be less than 100", changedMetrics.likenessRetentionScore < 100f)
    }
}
