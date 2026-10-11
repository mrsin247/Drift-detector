package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.DriftEngine
import com.example.engine.ImageUtils
import com.example.model.DriftMetrics
import com.example.model.DriftParameters
import com.example.model.DriftPreset
import com.example.model.FaceLandmarks
import com.example.model.GenerationStep
import com.example.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class DriftViewModel : ViewModel() {

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    private val _driftedBitmap = MutableStateFlow<Bitmap?>(null)
    val driftedBitmap: StateFlow<Bitmap?> = _driftedBitmap.asStateFlow()

    private val _heatmapBitmap = MutableStateFlow<Bitmap?>(null)
    val heatmapBitmap: StateFlow<Bitmap?> = _heatmapBitmap.asStateFlow()

    private val _landmarks = MutableStateFlow(FaceLandmarks())
    val landmarks: StateFlow<FaceLandmarks> = _landmarks.asStateFlow()

    private val _parameters = MutableStateFlow(DriftParameters())
    val parameters: StateFlow<DriftParameters> = _parameters.asStateFlow()

    private val _metrics = MutableStateFlow(DriftMetrics())
    val metrics: StateFlow<DriftMetrics> = _metrics.asStateFlow()

    private val _currentGen = MutableStateFlow(0)
    val currentGen: StateFlow<Int> = _currentGen.asStateFlow()

    private val _generationHistory = MutableStateFlow<List<GenerationStep>>(emptyList())
    val generationHistory: StateFlow<List<GenerationStep>> = _generationHistory.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.SPLIT_COMPARE)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _splitPosition = MutableStateFlow(0.5f)
    val splitPosition: StateFlow<Float> = _splitPosition.asStateFlow()

    private val _activePreset = MutableStateFlow<DriftPreset?>(null)
    val activePreset: StateFlow<DriftPreset?> = _activePreset.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _isComparisonMode = MutableStateFlow(false)
    val isComparisonMode: StateFlow<Boolean> = _isComparisonMode.asStateFlow()

    private val _testCompareBitmap = MutableStateFlow<Bitmap?>(null)
    val testCompareBitmap: StateFlow<Bitmap?> = _testCompareBitmap.asStateFlow()

    // Base bitmap that current gen builds upon (for multi-stage compounding)
    private var baseCompoundBitmap: Bitmap? = null

    fun initializeWithSample(context: Context) {
        if (_referenceBitmap.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val sample = ImageUtils.loadSamplePortrait(context)
            withContext(Dispatchers.Main) {
                setReferenceImage(sample)
            }
        }
    }

    fun loadUserImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val bitmap = ImageUtils.loadBitmapFromUri(context, uri)
            withContext(Dispatchers.Main) {
                _isProcessing.value = false
                if (bitmap != null) {
                    _isComparisonMode.value = false
                    _testCompareBitmap.value = null
                    setReferenceImage(bitmap)
                    _userMessage.tryEmit("Reference portrait loaded successfully")
                } else {
                    _userMessage.tryEmit("Unable to load selected photo")
                }
            }
        }
    }

    /**
     * Loads a second real image (e.g. video generation frame or re-anchored frame)
     * to benchmark and detect invisible drift against the Control/Reference image.
     */
    fun loadTestCompareImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val bitmap = ImageUtils.loadBitmapFromUri(context, uri)
            withContext(Dispatchers.Main) {
                _isProcessing.value = false
                val ref = _referenceBitmap.value
                if (bitmap != null && ref != null) {
                    _testCompareBitmap.value = bitmap
                    _isComparisonMode.value = true
                    _driftedBitmap.value = bitmap
                    _currentGen.value = 0

                    viewModelScope.launch(Dispatchers.Default) {
                        val (heat, metrics) = DriftEngine.calculateRealImageDifference(ref, bitmap)
                        withContext(Dispatchers.Main) {
                            _heatmapBitmap.value = heat
                            _metrics.value = metrics
                        }
                    }
                    _userMessage.tryEmit("Real test frame loaded! Analyzing sub-perceptual drift...")
                } else {
                    _userMessage.tryEmit("Failed to load test comparison frame")
                }
            }
        }
    }

    fun exitComparisonMode() {
        _isComparisonMode.value = false
        _testCompareBitmap.value = null
        renderDrift()
        viewModelScope.launch {
            _userMessage.emit("Switched to Drift Simulation Mode")
        }
    }

    private fun setReferenceImage(bitmap: Bitmap) {
        _referenceBitmap.value = bitmap
        baseCompoundBitmap = bitmap
        _currentGen.value = 0
        _generationHistory.value = listOf(
            GenerationStep(
                genNumber = 0,
                title = "Gen 0: Pristine Reference",
                parameters = DriftParameters(),
                metrics = DriftMetrics()
            )
        )
        _parameters.value = DriftParameters()
        _activePreset.value = null
        renderDrift()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun setSplitPosition(pos: Float) {
        _splitPosition.value = pos.coerceIn(0f, 1f)
    }

    fun updateParameters(params: DriftParameters) {
        _parameters.value = params
        _activePreset.value = null
        renderDrift()
    }

    fun applyPreset(preset: DriftPreset) {
        _activePreset.value = preset
        val newParams = when (preset) {
            DriftPreset.SUBTLE_ONE_CLIP -> DriftParameters(
                leftEyeShiftX = 0.12f,
                leftEyeShiftY = -0.08f,
                leftEyeScale = 0.96f,
                rightEyeShiftX = -0.08f,
                rightEyeShiftY = 0.05f,
                rightEyeScale = 1.02f,
                mouthShiftY = 0.10f,
                mouthSlant = 0.15f,
                jawWidthDrift = 0.08f,
                textureSoftening = 0.12f,
                colorVariance = 0.08f
            )
            DriftPreset.MODERATE_DRIFT -> DriftParameters(
                leftEyeShiftX = 0.28f,
                leftEyeShiftY = -0.22f,
                leftEyeScale = 0.90f,
                rightEyeShiftX = -0.24f,
                rightEyeShiftY = 0.18f,
                rightEyeScale = 1.08f,
                eyeDistanceDrift = 0.15f,
                mouthShiftX = 0.18f,
                mouthShiftY = 0.25f,
                mouthScale = 0.94f,
                mouthSlant = 0.35f,
                jawWidthDrift = 0.22f,
                textureSoftening = 0.32f,
                colorVariance = -0.20f
            )
            DriftPreset.RUNAWAY_MULTICLIP -> DriftParameters(
                leftEyeShiftX = 0.55f,
                leftEyeShiftY = -0.48f,
                leftEyeScale = 0.82f,
                rightEyeShiftX = -0.45f,
                rightEyeShiftY = 0.38f,
                rightEyeScale = 1.18f,
                eyeDistanceDrift = 0.35f,
                mouthShiftX = 0.35f,
                mouthShiftY = 0.50f,
                mouthScale = 0.88f,
                mouthSlant = 0.65f,
                jawWidthDrift = 0.45f,
                textureSoftening = 0.60f,
                colorVariance = 0.45f,
                cumulativeMultiplier = 1.4f
            )
            DriftPreset.EYE_ASYMMETRY_GLITCH -> DriftParameters(
                leftEyeShiftX = -0.42f,
                leftEyeShiftY = 0.35f,
                leftEyeScale = 0.82f,
                rightEyeShiftX = 0.15f,
                rightEyeShiftY = -0.10f,
                rightEyeScale = 1.15f,
                eyeDistanceDrift = -0.25f,
                mouthSlant = 0.40f,
                textureSoftening = 0.20f
            )
            DriftPreset.MOUTH_SYNC_WARP -> DriftParameters(
                mouthShiftX = 0.32f,
                mouthShiftY = 0.45f,
                mouthScale = 1.15f,
                mouthSlant = -0.55f,
                jawWidthDrift = 0.35f,
                textureSoftening = 0.18f
            )
            DriftPreset.TEXTURE_DECAY -> DriftParameters(
                textureSoftening = 0.85f,
                colorVariance = -0.45f,
                jawWidthDrift = 0.20f,
                mouthScale = 0.95f
            )
        }
        _parameters.value = newParams
        renderDrift()
    }

    /**
     * Simulates passing the current drifted output into the next video generation cycle.
     * Accumulates error compounding identically to multi-clip video generation models!
     */
    fun compoundStep() {
        val currentResult = _driftedBitmap.value ?: return
        val currentP = _parameters.value
        val nextGen = _currentGen.value + 1

        // The current drifted output becomes the base input for subsequent drift
        baseCompoundBitmap = currentResult

        // Stochastic micro-perturbation added to next generation
        val randomJitter = Random(System.currentTimeMillis())
        val additionalParams = DriftParameters(
            leftEyeShiftX = (currentP.leftEyeShiftX + randomJitter.nextFloat() * 0.18f - 0.09f).coerceIn(-1f, 1f),
            leftEyeShiftY = (currentP.leftEyeShiftY + randomJitter.nextFloat() * 0.18f - 0.09f).coerceIn(-1f, 1f),
            leftEyeScale = (currentP.leftEyeScale * (0.97f + randomJitter.nextFloat() * 0.06f)).coerceIn(0.80f, 1.20f),
            rightEyeShiftX = (currentP.rightEyeShiftX + randomJitter.nextFloat() * 0.18f - 0.09f).coerceIn(-1f, 1f),
            rightEyeShiftY = (currentP.rightEyeShiftY + randomJitter.nextFloat() * 0.18f - 0.09f).coerceIn(-1f, 1f),
            rightEyeScale = (currentP.rightEyeScale * (0.97f + randomJitter.nextFloat() * 0.06f)).coerceIn(0.80f, 1.20f),
            eyeDistanceDrift = (currentP.eyeDistanceDrift + randomJitter.nextFloat() * 0.10f - 0.05f).coerceIn(-1f, 1f),
            mouthShiftX = (currentP.mouthShiftX + randomJitter.nextFloat() * 0.15f - 0.075f).coerceIn(-1f, 1f),
            mouthShiftY = (currentP.mouthShiftY + randomJitter.nextFloat() * 0.15f - 0.075f).coerceIn(-1f, 1f),
            mouthScale = (currentP.mouthScale * (0.97f + randomJitter.nextFloat() * 0.06f)).coerceIn(0.80f, 1.20f),
            mouthSlant = (currentP.mouthSlant + randomJitter.nextFloat() * 0.15f - 0.075f).coerceIn(-1f, 1f),
            jawWidthDrift = (currentP.jawWidthDrift + randomJitter.nextFloat() * 0.12f - 0.06f).coerceIn(-1f, 1f),
            textureSoftening = (currentP.textureSoftening + 0.12f).coerceIn(0f, 1f),
            colorVariance = (currentP.colorVariance + randomJitter.nextFloat() * 0.12f - 0.06f).coerceIn(-1f, 1f),
            cumulativeMultiplier = currentP.cumulativeMultiplier * 1.15f
        )

        _currentGen.value = nextGen
        _parameters.value = additionalParams

        val historyList = _generationHistory.value.toMutableList()
        val latestStep = GenerationStep(
            genNumber = nextGen,
            title = "Gen $nextGen (+${nextGen} Clips)",
            parameters = additionalParams,
            metrics = _metrics.value
        )
        historyList.add(latestStep)
        _generationHistory.value = historyList

        renderDrift()
        viewModelScope.launch {
            _userMessage.emit("Compounded to Gen $nextGen (+1 Video Chunk)")
        }
    }

    /**
     * Resets back to the original reference photo (Gen 0).
     */
    fun resetToGen0() {
        val original = _referenceBitmap.value ?: return
        baseCompoundBitmap = original
        _currentGen.value = 0
        _parameters.value = DriftParameters()
        _activePreset.value = null
        _generationHistory.value = listOf(
            GenerationStep(
                genNumber = 0,
                title = "Gen 0: Pristine Reference",
                parameters = DriftParameters(),
                metrics = DriftMetrics()
            )
        )
        renderDrift()
        viewModelScope.launch {
            _userMessage.emit("Reset to Gen 0 Reference Face")
        }
    }

    /**
     * Generates a new stochastic random seed drift without changing generation depth.
     */
    fun randomizeDriftSeed() {
        val rnd = Random(System.currentTimeMillis())
        val randomized = DriftParameters(
            leftEyeShiftX = (rnd.nextFloat() * 0.6f - 0.3f),
            leftEyeShiftY = (rnd.nextFloat() * 0.6f - 0.3f),
            leftEyeScale = (0.90f + rnd.nextFloat() * 0.20f),
            rightEyeShiftX = (rnd.nextFloat() * 0.6f - 0.3f),
            rightEyeShiftY = (rnd.nextFloat() * 0.6f - 0.3f),
            rightEyeScale = (0.90f + rnd.nextFloat() * 0.20f),
            eyeDistanceDrift = (rnd.nextFloat() * 0.4f - 0.2f),
            mouthShiftX = (rnd.nextFloat() * 0.5f - 0.25f),
            mouthShiftY = (rnd.nextFloat() * 0.5f - 0.25f),
            mouthScale = (0.92f + rnd.nextFloat() * 0.16f),
            mouthSlant = (rnd.nextFloat() * 0.6f - 0.3f),
            jawWidthDrift = (rnd.nextFloat() * 0.4f - 0.2f),
            textureSoftening = rnd.nextFloat() * 0.35f,
            colorVariance = (rnd.nextFloat() * 0.4f - 0.2f),
            cumulativeMultiplier = _parameters.value.cumulativeMultiplier
        )
        _activePreset.value = null
        _parameters.value = randomized
        renderDrift()
    }

    fun updateLandmark(name: String, normalizedPoint: PointF) {
        val current = _landmarks.value
        val clamped = PointF(normalizedPoint.x.coerceIn(0.1f, 0.9f), normalizedPoint.y.coerceIn(0.1f, 0.9f))
        val updated = when (name) {
            "leftEye" -> current.copy(leftEye = clamped)
            "rightEye" -> current.copy(rightEye = clamped)
            "nose" -> current.copy(nose = clamped)
            "mouth" -> current.copy(mouth = clamped)
            "jawLeft" -> current.copy(jawLeft = clamped)
            "jawRight" -> current.copy(jawRight = clamped)
            else -> current
        }
        _landmarks.value = updated
        renderDrift()
    }

    private fun renderDrift() {
        val ref = _referenceBitmap.value ?: return
        val base = baseCompoundBitmap ?: ref
        val lm = _landmarks.value
        val params = _parameters.value

        viewModelScope.launch(Dispatchers.Default) {
            val drifted = if (params.isZero && _currentGen.value == 0) {
                ref
            } else {
                DriftEngine.applyDrift(base, lm, params)
            }

            val heatmap = DriftEngine.generateDifferenceHeatmap(ref, drifted)
            val metrics = DriftEngine.calculateMetrics(lm, params, ref.width, ref.height)

            withContext(Dispatchers.Main) {
                _driftedBitmap.value = drifted
                _heatmapBitmap.value = heatmap
                _metrics.value = metrics
            }
        }
    }

    /**
     * Takes the current output frame and sets it as the new Gen 0 Reference baseline.
     * This enables looping the output straight back into the simulator.
     */
    fun setDriftedAsNewBaseline() {
        val currentOutput = _driftedBitmap.value ?: return
        setReferenceImage(currentOutput)
        viewModelScope.launch {
            _userMessage.emit("Output set as new baseline (Gen 0) — ready to run back through!")
        }
    }

    fun saveDriftedImage(context: Context, isJpeg: Boolean = true) {
        val bitmap = _driftedBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val gen = _currentGen.value
            val (uri, fileName) = ImageUtils.saveBitmapToGallery(context, bitmap, "Gen${gen}", isJpeg)
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    val formatName = if (isJpeg) "JPEG" else "PNG"
                    _userMessage.tryEmit("Saved $formatName: $fileName to Pictures/LikenessDriftLab")
                } else {
                    _userMessage.tryEmit("Failed to save image")
                }
            }
        }
    }

    fun shareDriftedImage(context: Context, isJpeg: Boolean = true) {
        val bitmap = _driftedBitmap.value ?: return
        val gen = _currentGen.value
        val score = _metrics.value.likenessRetentionScore
        val intent = ImageUtils.createShareIntent(context, bitmap, gen, score, isJpeg)
        if (intent != null) {
            context.startActivity(android.content.Intent.createChooser(intent, "Share Drifted Frame (${if (isJpeg) "JPEG" else "PNG"}) to Agent / Fixer"))
        } else {
            viewModelScope.launch {
                _userMessage.emit("Unable to share frame")
            }
        }
    }
}
