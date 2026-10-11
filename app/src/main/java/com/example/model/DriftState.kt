package com.example.model

import android.graphics.PointF

/**
 * Normalized face landmarks (0.0 to 1.0 relative to image width/height)
 */
data class FaceLandmarks(
    val leftEye: PointF = PointF(0.40f, 0.38f),
    val rightEye: PointF = PointF(0.60f, 0.38f),
    val nose: PointF = PointF(0.50f, 0.50f),
    val mouth: PointF = PointF(0.50f, 0.65f),
    val leftMouthCorner: PointF = PointF(0.42f, 0.65f),
    val rightMouthCorner: PointF = PointF(0.58f, 0.65f),
    val jawLeft: PointF = PointF(0.30f, 0.70f),
    val jawRight: PointF = PointF(0.70f, 0.70f),
    val chin: PointF = PointF(0.50f, 0.82f)
)

/**
 * Parameters controlling algorithmic facial drift simulation
 */
data class DriftParameters(
    // Eye distortions
    val leftEyeShiftX: Float = 0f,       // -1.0 .. 1.0 (-20px to +20px normalized)
    val leftEyeShiftY: Float = 0f,       // -1.0 .. 1.0
    val leftEyeScale: Float = 1.0f,      // 0.85 .. 1.15
    val rightEyeShiftX: Float = 0f,      // -1.0 .. 1.0
    val rightEyeShiftY: Float = 0f,      // -1.0 .. 1.0
    val rightEyeScale: Float = 1.0f,     // 0.85 .. 1.15
    val eyeDistanceDrift: Float = 0f,    // -1.0 .. 1.0 (converge or diverge)

    // Mouth / Jaw distortions
    val mouthShiftX: Float = 0f,         // -1.0 .. 1.0
    val mouthShiftY: Float = 0f,         // -1.0 .. 1.0
    val mouthScale: Float = 1.0f,        // 0.85 .. 1.15
    val mouthSlant: Float = 0f,          // -1.0 .. 1.0 (one corner droops)
    val jawWidthDrift: Float = 0f,       // -1.0 .. 1.0 (jawline expansion/slimming)

    // Latent diffusion artifacts
    val textureSoftening: Float = 0f,    // 0.0 .. 1.0 (micro-blur/skin texture mush)
    val colorVariance: Float = 0f,       // -1.0 .. 1.0 (chromatic/temp drift)
    
    // Multiplier for compound generations
    val cumulativeMultiplier: Float = 1.0f
) {
    val isZero: Boolean
        get() = leftEyeShiftX == 0f && leftEyeShiftY == 0f && leftEyeScale == 1.0f &&
                rightEyeShiftX == 0f && rightEyeShiftY == 0f && rightEyeScale == 1.0f &&
                eyeDistanceDrift == 0f && mouthShiftX == 0f && mouthShiftY == 0f &&
                mouthScale == 1.0f && mouthSlant == 0f && jawWidthDrift == 0f &&
                textureSoftening == 0f && colorVariance == 0f
}

/**
 * Quantitative measurements for AI agent threshold validation
 */
data class DriftMetrics(
    val likenessRetentionScore: Float = 100f,    // 0% .. 100%
    val meanFeatureShiftPx: Float = 0f,          // Average landmark displacement
    val maxFeatureShiftPx: Float = 0f,           // Peak localized displacement
    val eyeAsymmetryPct: Float = 0f,             // % difference between eyes
    val textureSoftnessPct: Float = 0f,          // High-frequency detail loss
    val statusVerdict: String = "ANCHORED",      // Verdict badge
    val driftCategory: String = "Zero Drift"
)

/**
 * An individual snapshot in the multi-clip generation chain
 */
data class GenerationStep(
    val genNumber: Int,
    val title: String,
    val parameters: DriftParameters,
    val metrics: DriftMetrics
)

enum class ViewMode(val label: String) {
    SPLIT_COMPARE("Split Slider"),
    DRIFTED("Drifted Frame"),
    HEATMAP("Delta Heatmap"),
    VECTORS("Landmark Vectors"),
    REFERENCE("Original Ref")
}

enum class DriftPreset(val label: String, val description: String) {
    SUBTLE_ONE_CLIP("1-Clip Subtle (~24f)", "Slight eye slant & micro-jaw relaxation typical of 1-second video chunks"),
    MODERATE_DRIFT("Moderate Drift (~48f)", "Noticeable eye asymmetry, mouth corner droop, and slight texture softening"),
    RUNAWAY_MULTICLIP("Multi-Clip Runaway", "Heavy likeness decay across 3+ video generations without re-anchoring"),
    EYE_ASYMMETRY_GLITCH("Eye Asymmetry Glitch", "One eye shrinks while the other drifts off-axis"),
    MOUTH_SYNC_WARP("Lip Sync Drift", "Mouth warping from audio-driven latent diffusion"),
    TEXTURE_DECAY("Latent Texture Decay", "Loss of high-frequency skin pores and micro-details")
}
