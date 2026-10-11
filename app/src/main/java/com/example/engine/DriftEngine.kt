package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import com.example.model.DriftMetrics
import com.example.model.DriftParameters
import com.example.model.FaceLandmarks
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DriftEngine {

    private const val MESH_WIDTH = 26
    private const val MESH_HEIGHT = 26

    /**
     * Deforms the source bitmap according to the provided parameters and face landmarks.
     */
    fun applyDrift(
        source: Bitmap,
        landmarks: FaceLandmarks,
        params: DriftParameters
    ): Bitmap {
        val width = source.width
        val height = source.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Calculate vertex mesh
        val totalVerts = (MESH_WIDTH + 1) * (MESH_HEIGHT + 1)
        val verts = FloatArray(totalVerts * 2)

        val cellW = width.toFloat() / MESH_WIDTH
        val cellH = height.toFloat() / MESH_HEIGHT

        // Scaling reference based on face width
        val interEyeDist = hypot(
            (landmarks.rightEye.x - landmarks.leftEye.x) * width,
            (landmarks.rightEye.y - landmarks.leftEye.y) * height
        )
        val faceScale = max(interEyeDist, width * 0.25f)
        val maxShift = faceScale * 0.16f * params.cumulativeMultiplier

        // Landmark absolute coordinates
        val leftEyePx = PointF(landmarks.leftEye.x * width, landmarks.leftEye.y * height)
        val rightEyePx = PointF(landmarks.rightEye.x * width, landmarks.rightEye.y * height)
        val mouthPx = PointF(landmarks.mouth.x * width, landmarks.mouth.y * height)
        val leftMouthCornerPx = PointF(landmarks.leftMouthCorner.x * width, landmarks.leftMouthCorner.y * height)
        val rightMouthCornerPx = PointF(landmarks.rightMouthCorner.x * width, landmarks.rightMouthCorner.y * height)
        val jawLeftPx = PointF(landmarks.jawLeft.x * width, landmarks.jawLeft.y * height)
        val jawRightPx = PointF(landmarks.jawRight.x * width, landmarks.jawRight.y * height)

        val eyeInfluenceRadius = faceScale * 0.55f
        val mouthInfluenceRadius = faceScale * 0.65f
        val jawInfluenceRadius = faceScale * 0.85f

        // Landmark displacement vectors
        val leftEyeDx = (params.leftEyeShiftX - params.eyeDistanceDrift * 0.4f) * maxShift
        val leftEyeDy = params.leftEyeShiftY * maxShift

        val rightEyeDx = (params.rightEyeShiftX + params.eyeDistanceDrift * 0.4f) * maxShift
        val rightEyeDy = params.rightEyeShiftY * maxShift

        val mouthDx = params.mouthShiftX * maxShift
        val mouthDy = params.mouthShiftY * maxShift

        val mouthSlantDy = params.mouthSlant * maxShift * 0.5f
        val jawWidthDx = params.jawWidthDrift * maxShift * 0.6f

        var idx = 0
        for (row in 0..MESH_HEIGHT) {
            val origY = row * cellH
            for (col in 0..MESH_WIDTH) {
                val origX = col * cellW

                var dx = 0f
                var dy = 0f

                // Left Eye Influence
                val distLeftEye = hypot(origX - leftEyePx.x, origY - leftEyePx.y)
                if (distLeftEye < eyeInfluenceRadius) {
                    val w = smoothFalloff(distLeftEye, eyeInfluenceRadius)
                    dx += leftEyeDx * w
                    dy += leftEyeDy * w

                    // Eye scale (squint/enlarge)
                    if (params.leftEyeScale != 1.0f) {
                        val scaleFactor = (params.leftEyeScale - 1.0f) * w
                        dx += (origX - leftEyePx.x) * scaleFactor
                        dy += (origY - leftEyePx.y) * scaleFactor
                    }
                }

                // Right Eye Influence
                val distRightEye = hypot(origX - rightEyePx.x, origY - rightEyePx.y)
                if (distRightEye < eyeInfluenceRadius) {
                    val w = smoothFalloff(distRightEye, eyeInfluenceRadius)
                    dx += rightEyeDx * w
                    dy += rightEyeDy * w

                    // Eye scale
                    if (params.rightEyeScale != 1.0f) {
                        val scaleFactor = (params.rightEyeScale - 1.0f) * w
                        dx += (origX - rightEyePx.x) * scaleFactor
                        dy += (origY - rightEyePx.y) * scaleFactor
                    }
                }

                // Mouth Influence
                val distMouth = hypot(origX - mouthPx.x, origY - mouthPx.y)
                if (distMouth < mouthInfluenceRadius) {
                    val w = smoothFalloff(distMouth, mouthInfluenceRadius)
                    dx += mouthDx * w
                    dy += mouthDy * w

                    if (params.mouthScale != 1.0f) {
                        val scaleFactor = (params.mouthScale - 1.0f) * w
                        dx += (origX - mouthPx.x) * scaleFactor
                        dy += (origY - mouthPx.y) * scaleFactor
                    }
                }

                // Mouth Slant (corners moving in opposite directions)
                val distLeftCorner = hypot(origX - leftMouthCornerPx.x, origY - leftMouthCornerPx.y)
                if (distLeftCorner < eyeInfluenceRadius * 0.6f) {
                    val w = smoothFalloff(distLeftCorner, eyeInfluenceRadius * 0.6f)
                    dy += mouthSlantDy * w
                }
                val distRightCorner = hypot(origX - rightMouthCornerPx.x, origY - rightMouthCornerPx.y)
                if (distRightCorner < eyeInfluenceRadius * 0.6f) {
                    val w = smoothFalloff(distRightCorner, eyeInfluenceRadius * 0.6f)
                    dy -= mouthSlantDy * w
                }

                // Jaw Width Drift
                val distJawLeft = hypot(origX - jawLeftPx.x, origY - jawLeftPx.y)
                if (distJawLeft < jawInfluenceRadius) {
                    val w = smoothFalloff(distJawLeft, jawInfluenceRadius)
                    dx -= jawWidthDx * w
                }
                val distJawRight = hypot(origX - jawRightPx.x, origY - jawRightPx.y)
                if (distJawRight < jawInfluenceRadius) {
                    val w = smoothFalloff(distJawRight, jawInfluenceRadius)
                    dx += jawWidthDx * w
                }

                verts[idx] = origX + dx
                verts[idx + 1] = origY + dy
                idx += 2
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmapMesh(source, MESH_WIDTH, MESH_HEIGHT, verts, 0, null, 0, paint)

        // 2. Latent Diffusion Texture Softening (Micro-blur)
        val processedBitmap = if (params.textureSoftening > 0.05f) {
            applyDiffusionSoftening(result, params.textureSoftening)
        } else {
            result
        }

        // 3. Chromatic / Saturation Temperature Variance
        return if (abs(params.colorVariance) > 0.05f) {
            applyColorVariance(processedBitmap, params.colorVariance)
        } else {
            processedBitmap
        }
    }

    /**
     * Computes an amplified difference heatmap comparing original reference and drifted bitmap.
     */
    fun generateDifferenceHeatmap(
        original: Bitmap,
        drifted: Bitmap
    ): Bitmap {
        val width = original.width
        val height = original.height

        val heatmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val origPixels = IntArray(width * height)
        val driftPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        original.getPixels(origPixels, 0, width, 0, 0, width, height)
        drifted.getPixels(driftPixels, 0, width, 0, 0, width, height)

        val amplification = 6.5f

        for (i in origPixels.indices) {
            val c1 = origPixels[i]
            val c2 = driftPixels[i]

            val r1 = (c1 shr 16) and 0xFF
            val g1 = (c1 shr 8) and 0xFF
            val b1 = c1 and 0xFF

            val r2 = (c2 shr 16) and 0xFF
            val g2 = (c2 shr 8) and 0xFF
            val b2 = c2 and 0xFF

            val delta = ((abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)) / 3f) * amplification
            val intensity = (min(255f, delta) / 255f)

            // Turbo/Magma false color gradient
            outPixels[i] = intensityToHeatmapColor(intensity)
        }

        heatmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return heatmap
    }

    /**
     * Compares an actual Control/Reference image against a real Target Test Frame (from video or post-fix).
     * Calculates an amplified sub-perceptual heatmap and quantitative test-bed likeness metrics.
     */
    fun calculateRealImageDifference(
        original: Bitmap,
        testImage: Bitmap
    ): Pair<Bitmap, DriftMetrics> {
        val width = original.width
        val height = original.height

        val scaledTest = if (testImage.width != width || testImage.height != height) {
            Bitmap.createScaledBitmap(testImage, width, height, true)
        } else {
            testImage
        }

        val heatmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val origPixels = IntArray(width * height)
        val testPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        original.getPixels(origPixels, 0, width, 0, 0, width, height)
        scaledTest.getPixels(testPixels, 0, width, 0, 0, width, height)

        var totalDelta = 0.0
        var maxDelta = 0f
        val amplification = 7.5f

        for (i in origPixels.indices) {
            val c1 = origPixels[i]
            val c2 = testPixels[i]

            val r1 = (c1 shr 16) and 0xFF
            val g1 = (c1 shr 8) and 0xFF
            val b1 = c1 and 0xFF

            val r2 = (c2 shr 16) and 0xFF
            val g2 = (c2 shr 8) and 0xFF
            val b2 = c2 and 0xFF

            val pixelDelta = (abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)) / 3f
            totalDelta += pixelDelta
            if (pixelDelta > maxDelta) maxDelta = pixelDelta

            val intensity = min(1f, (pixelDelta * amplification) / 255f)
            outPixels[i] = intensityToHeatmapColor(intensity)
        }

        heatmap.setPixels(outPixels, 0, width, 0, 0, width, height)

        val meanDelta = (totalDelta / (width * height)).toFloat()
        // Convert to percentage likeness
        val likeness = max(0f, min(100f, 100f - (meanDelta / 28f) * 45f))

        val verdict = when {
            likeness >= 96f -> "SUB-PERCEPTUAL MATCH (EXACT)"
            likeness >= 88f -> "ACCEPTABLE TOLERANCE (PASS)"
            likeness >= 76f -> "EARLY DRIFT DETECTED"
            likeness >= 60f -> "NOTICEABLE IDENTITY DECAY"
            else -> "CRITICAL: RE-ANCHOR REQUIRED"
        }

        val category = when {
            likeness >= 95f -> "Zero Drift"
            likeness >= 88f -> "Micro-Shift"
            likeness >= 76f -> "Visible Drift"
            else -> "Likeness Loss"
        }

        val metrics = DriftMetrics(
            likenessRetentionScore = (likeness * 10).roundToInt() / 10f,
            meanFeatureShiftPx = (meanDelta * 10).roundToInt() / 10f,
            maxFeatureShiftPx = (maxDelta * 10).roundToInt() / 10f,
            eyeAsymmetryPct = ((meanDelta / 15f) * 100f).coerceIn(0f, 100f),
            textureSoftnessPct = ((meanDelta / 25f) * 100f).coerceIn(0f, 100f),
            statusVerdict = verdict,
            driftCategory = "Real Compare ($category)"
        )

        return Pair(heatmap, metrics)
    }

    /**
     * Calculates quantitative drift statistics for agent threshold verification.
     */
    fun calculateMetrics(
        landmarks: FaceLandmarks,
        params: DriftParameters,
        imageWidth: Int,
        imageHeight: Int
    ): DriftMetrics {
        val interEyeDist = hypot(
            (landmarks.rightEye.x - landmarks.leftEye.x) * imageWidth,
            (landmarks.rightEye.y - landmarks.leftEye.y) * imageHeight
        )
        val maxShift = interEyeDist * 0.16f * params.cumulativeMultiplier

        val leftEyeShift = hypot(
            (params.leftEyeShiftX - params.eyeDistanceDrift * 0.4f) * maxShift,
            params.leftEyeShiftY * maxShift
        ) + abs(params.leftEyeScale - 1f) * maxShift

        val rightEyeShift = hypot(
            (params.rightEyeShiftX + params.eyeDistanceDrift * 0.4f) * maxShift,
            params.rightEyeShiftY * maxShift
        ) + abs(params.rightEyeScale - 1f) * maxShift

        val mouthShift = hypot(
            params.mouthShiftX * maxShift,
            params.mouthShiftY * maxShift
        ) + abs(params.mouthScale - 1f) * maxShift + abs(params.mouthSlant) * maxShift * 0.5f

        val jawShift = abs(params.jawWidthDrift) * maxShift * 0.6f

        val meanShiftPx = (leftEyeShift + rightEyeShift + mouthShift + jawShift) / 4f
        val maxShiftPx = max(max(leftEyeShift, rightEyeShift), max(mouthShift, jawShift))

        val eyeAsymmetry = abs(leftEyeShift - rightEyeShift) / (max(leftEyeShift, rightEyeShift) + 0.001f) * 100f

        // Likeness score calculation
        val shiftPenalty = (meanShiftPx / (interEyeDist * 0.25f)) * 50f
        val texturePenalty = params.textureSoftening * 18f
        val colorPenalty = abs(params.colorVariance) * 8f
        val totalPenalty = shiftPenalty + texturePenalty + colorPenalty

        val likenessScore = max(0f, min(100f, 100f - totalPenalty))

        val verdict = when {
            likenessScore >= 96f -> "PERFECT ALIGNMENT"
            likenessScore >= 90f -> "WITHIN DRIFT TOLERANCE"
            likenessScore >= 80f -> "DRIFT DETECTED (ANCHOR SOON)"
            likenessScore >= 65f -> "RE-ANCHOR RECOMMENDED"
            else -> "CRITICAL DEGRADATION: CUT & REGENERATE"
        }

        val category = when {
            likenessScore >= 95f -> "Zero Drift"
            likenessScore >= 88f -> "Subtle (1-Clip)"
            likenessScore >= 78f -> "Noticeable (2-Clips)"
            likenessScore >= 65f -> "Severe (Multi-Clip)"
            else -> "Identity Loss"
        }

        return DriftMetrics(
            likenessRetentionScore = (likenessScore * 10).roundToInt() / 10f,
            meanFeatureShiftPx = (meanShiftPx * 10).roundToInt() / 10f,
            maxFeatureShiftPx = (maxShiftPx * 10).roundToInt() / 10f,
            eyeAsymmetryPct = (eyeAsymmetry * 10).roundToInt() / 10f,
            textureSoftnessPct = (params.textureSoftening * 1000).roundToInt() / 10f,
            statusVerdict = verdict,
            driftCategory = category
        )
    }

    private fun smoothFalloff(dist: Float, radius: Float): Float {
        val normalized = dist / radius
        if (normalized >= 1.0f) return 0f
        // Smooth Hermite cubic interpolation
        val t = 1.0f - normalized
        return t * t * (3.0f - 2.0f * t)
    }

    private fun applyDiffusionSoftening(source: Bitmap, factor: Float): Bitmap {
        val width = source.width
        val height = source.height
        // Downscale factor proportional to softening to mimic latent loss
        val scaleDown = max(0.15f, 1.0f - factor * 0.70f)
        val smallW = max(16, (width * scaleDown).toInt())
        val smallH = max(16, (height * scaleDown).toInt())

        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val blurred = Bitmap.createScaledBitmap(small, width, height, true)
        small.recycle()

        // Blend with original with opacity proportional to factor
        val blended = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blended)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(source, 0f, 0f, paint)
        paint.alpha = (min(1.0f, factor * 0.95f) * 255).toInt()
        canvas.drawBitmap(blurred, 0f, 0f, paint)
        blurred.recycle()

        return blended
    }

    private fun applyColorVariance(source: Bitmap, variance: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()
        // Saturation drift: slightly desaturates or oversaturates
        cm.setSaturation(max(0.7f, 1.0f - variance * 0.2f))

        // Temperature balance shift
        val matrixArray = cm.array
        matrixArray[4] += variance * 15f   // Red bias
        matrixArray[14] -= variance * 15f  // Blue bias

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun intensityToHeatmapColor(t: Float): Int {
        // Dark blue (cold) -> Cyan -> Green -> Yellow -> Red/Hot Pink
        val clamped = max(0f, min(1f, t))
        return when {
            clamped < 0.15f -> {
                val p = clamped / 0.15f
                Color.argb(255, (15 * p).toInt(), (20 * p).toInt(), (40 + 100 * p).toInt())
            }
            clamped < 0.40f -> {
                val p = (clamped - 0.15f) / 0.25f
                Color.argb(255, 0, (120 + 120 * p).toInt(), (220 - 40 * p).toInt())
            }
            clamped < 0.70f -> {
                val p = (clamped - 0.40f) / 0.30f
                Color.argb(255, (220 * p).toInt(), 240, (180 * (1 - p)).toInt())
            }
            else -> {
                val p = (clamped - 0.70f) / 0.30f
                Color.argb(255, 255, (240 * (1 - p)).toInt(), (50 + 150 * p).toInt())
            }
        }
    }
}
