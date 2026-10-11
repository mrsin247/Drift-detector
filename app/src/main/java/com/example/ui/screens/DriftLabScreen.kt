package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ViewMode
import com.example.ui.components.DriftControls
import com.example.ui.components.DriftHeatmapView
import com.example.ui.components.LandmarkOverlayView
import com.example.ui.components.MetricsCard
import com.example.ui.components.SplitCompareView
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DriftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriftLabScreen(
    viewModel: DriftViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val referenceBitmap by viewModel.referenceBitmap.collectAsState()
    val driftedBitmap by viewModel.driftedBitmap.collectAsState()
    val heatmapBitmap by viewModel.heatmapBitmap.collectAsState()
    val landmarks by viewModel.landmarks.collectAsState()
    val parameters by viewModel.parameters.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val currentGen by viewModel.currentGen.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val splitPosition by viewModel.splitPosition.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isComparisonMode by viewModel.isComparisonMode.collectAsState()
    val testCompareBitmap by viewModel.testCompareBitmap.collectAsState()

    // Control Photo Picker (Slot 1: Reference)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadUserImage(context, uri)
        }
    }

    // Target Test Frame Picker (Slot 2: Image to test against Reference)
    val testPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadTestCompareImage(context, uri)
        }
    }

    // Initialize with bundled sample portrait on first launch
    LaunchedEffect(Unit) {
        viewModel.initializeWithSample(context)
    }

    // Collect snackbar messages
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Likeness Drift Lab",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isComparisonMode) "2-Photo Test Bed Mode" else "Video Drift Simulator Mode",
                            fontSize = 11.sp,
                            color = if (isComparisonMode) AmberAccent else CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = CyanAccent
                ),
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help and Guide",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Load Control Reference",
                            tint = CyanAccent
                        )
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("save_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Save Frame",
                            tint = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.shareDriftedImage(context, isJpeg = true) },
                        modifier = Modifier.testTag("share_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share to Agent",
                            tint = PurpleAccent
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioBackground)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Lab Mode Switcher Row: Simulator vs 2-Photo Benchmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isComparisonMode) CyanAccent.copy(alpha = 0.2f) else StudioSurfaceVariant)
                        .border(1.dp, if (!isComparisonMode) CyanAccent else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { if (isComparisonMode) viewModel.exitComparisonMode() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Simulator",
                            tint = if (!isComparisonMode) CyanAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Drift Simulator",
                            fontSize = 12.sp,
                            fontWeight = if (!isComparisonMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isComparisonMode) CyanAccent else TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isComparisonMode) AmberAccent.copy(alpha = 0.2f) else StudioSurfaceVariant)
                        .border(1.dp, if (isComparisonMode) AmberAccent else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            if (!isComparisonMode) {
                                testPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Benchmark 2 Photos",
                            tint = if (isComparisonMode) AmberAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "2-Photo Test Bed",
                            fontSize = 12.sp,
                            fontWeight = if (isComparisonMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isComparisonMode) AmberAccent else TextSecondary
                        )
                    }
                }
            }

            // View Mode Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewMode.values().forEach { mode ->
                    val isSelected = viewMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setViewMode(mode) },
                        label = {
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = (if (isComparisonMode) AmberAccent else CyanAccent).copy(alpha = 0.2f),
                            selectedLabelColor = if (isComparisonMode) AmberAccent else CyanAccent,
                            containerColor = StudioSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("view_mode_${mode.name.lowercase()}")
                    )
                }
            }

            // Main Visual Canvas Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = CyanAccent)
                } else {
                    val ref = referenceBitmap
                    val drift = driftedBitmap
                    val heat = heatmapBitmap

                    if (ref != null && drift != null) {
                        when (viewMode) {
                            ViewMode.SPLIT_COMPARE -> {
                                SplitCompareView(
                                    referenceBitmap = ref,
                                    driftedBitmap = drift,
                                    splitPosition = splitPosition,
                                    onSplitChange = { viewModel.setSplitPosition(it) },
                                    genNumber = currentGen
                                )
                            }
                            ViewMode.DRIFTED -> {
                                Image(
                                    bitmap = drift.asImageBitmap(),
                                    contentDescription = "Test frame output",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            ViewMode.REFERENCE -> {
                                Image(
                                    bitmap = ref.asImageBitmap(),
                                    contentDescription = "Original control reference",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            ViewMode.HEATMAP -> {
                                if (heat != null) {
                                    DriftHeatmapView(
                                        heatmapBitmap = heat,
                                        metrics = metrics
                                    )
                                }
                            }
                            ViewMode.VECTORS -> {
                                LandmarkOverlayView(
                                    bitmap = drift,
                                    landmarks = landmarks,
                                    parameters = parameters,
                                    onLandmarkDrag = { node, point ->
                                        viewModel.updateLandmark(node, point)
                                    }
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = CyanAccent)
                    }
                }
            }

            // Metrics Card
            MetricsCard(
                genNumber = currentGen,
                metrics = metrics,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // 2-Photo Benchmark Deck (Active when comparing two real images)
            if (isComparisonMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioSurface)
                        .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "REAL-WORLD BENCHMARK ACTIVE",
                                color = AmberAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Control vs. Test Frame",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = "Analyzing sub-perceptual likeness drift between your Control Reference and your Test Video Frame. Use the Delta Heatmap to reveal microscopic shifts invisible to the naked eye.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    testPhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberAccent,
                                    contentColor = StudioBackground
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Load New Test Frame", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.exitComparisonMode() },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(CyanAccent)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Back to Simulator", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // Pipeline & Export Actions Deck
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Save JPEG button
                    Button(
                        onClick = { viewModel.saveDriftedImage(context, isJpeg = true) },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp)
                            .testTag("save_jpeg_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = StudioBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save JPEG",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save JPEG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Share to Muse / Fixer button
                    Button(
                        onClick = { viewModel.shareDriftedImage(context, isJpeg = true) },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp)
                            .testTag("share_frame_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share to Fixer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Loop Back as New Reference (Run back through)
                    OutlinedButton(
                        onClick = { viewModel.setDriftedAsNewBaseline() },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp)
                            .testTag("loop_back_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = SolidColor(AmberAccent)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Loop back",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Loop As Ref",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Drift Controls Deck (Sliders, Compounding, Presets)
                DriftControls(
                    currentGen = currentGen,
                    parameters = parameters,
                    activePreset = activePreset,
                    onParametersChange = { viewModel.updateParameters(it) },
                    onPresetSelect = { viewModel.applyPreset(it) },
                    onCompoundStep = { viewModel.compoundStep() },
                    onResetGen0 = { viewModel.resetToGen0() },
                    onRandomSeed = { viewModel.randomizeDriftSeed() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Export Format Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Export Drifted Frame",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose an image format to save to your device's Pictures folder (Pictures/LikenessDriftLab):",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveDriftedImage(context, isJpeg = true)
                                showExportDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = StudioBackground)
                        ) {
                            Text("Save JPEG (.jpg)", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.saveDriftedImage(context, isJpeg = false)
                                showExportDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
                        ) {
                            Text("Save PNG (.png)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = StudioSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Testing Guide Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "Likeness Drift Lab Testing Guide",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "1. Drift Simulator Mode:",
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Load a reference photo and simulate synthetic video drift across multiple clips with 'Compound Step (+1 Clip)'. Save or share the JPEG to test if your agent catches the drift.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Text(
                        text = "2. Real 2-Photo Benchmark Test Bed:",
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Switch to '2-Photo Test Bed'. Put in your Control/Reference photo and any real frame from your video generation or after Muse's fix. The Delta Heatmap amplifies differences 7.5x to reveal sub-perceptual drift in eyes, lips, and contours that the naked eye misses!",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got It", color = CyanAccent)
                }
            },
            containerColor = StudioSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
