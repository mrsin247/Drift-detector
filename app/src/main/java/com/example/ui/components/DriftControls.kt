package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriftParameters
import com.example.model.DriftPreset
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun DriftControls(
    currentGen: Int,
    parameters: DriftParameters,
    activePreset: DriftPreset?,
    onParametersChange: (DriftParameters) -> Unit,
    onPresetSelect: (DriftPreset) -> Unit,
    onCompoundStep: () -> Unit,
    onResetGen0: () -> Unit,
    onRandomSeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedSliders by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(StudioSurface)
            .border(1.dp, StudioCardBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Primary Action Row: Compound Step & Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onCompoundStep,
                modifier = Modifier
                    .weight(1.8f)
                    .height(48.dp)
                    .testTag("compound_step_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurpleAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Compound generation",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Compound Step (+1 Clip)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onRandomSeed,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("random_seed_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanAccent)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = "Randomize seed",
                    modifier = Modifier.size(18.dp)
                )
            }

            OutlinedButton(
                onClick = onResetGen0,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("reset_gen0_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseAccent),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(RoseAccent.copy(alpha = 0.6f))),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset to Gen 0",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 2. Preset Chips Row
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "SIMULATED DRIFT PRESETS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DriftPreset.values().forEach { preset ->
                    val isSelected = activePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) CyanAccent.copy(alpha = 0.25f) else StudioSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isSelected) CyanAccent else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onPresetSelect(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("preset_chip_${preset.name.lowercase()}")
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) CyanAccent else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 3. Fine-Tuning Sliders Accordion Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showAdvancedSliders = !showAdvancedSliders }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAdvancedSliders) "Hide Granular Sliders" else "Tune Granular Sliders (Eyes, Mouth, Noise)",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (showAdvancedSliders) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Toggle sliders",
                tint = CyanAccent
            )
        }

        // 4. Granular Sliders (Animated Expandable)
        AnimatedVisibility(
            visible = showAdvancedSliders,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section: Eyes
                Text(
                    text = "EYE MORPH & ASYMMETRY",
                    color = PurpleAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                SliderControl(
                    label = "Left Eye Asymmetry (Scale/Squint)",
                    value = parameters.leftEyeScale,
                    range = 0.85f..1.15f,
                    displayFormat = { "${((it - 1f) * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(leftEyeScale = it)) }
                )

                SliderControl(
                    label = "Right Eye Offset Shift",
                    value = parameters.rightEyeShiftY,
                    range = -1f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(rightEyeShiftY = it)) }
                )

                SliderControl(
                    label = "Inter-Ocular Distance (Convergence)",
                    value = parameters.eyeDistanceDrift,
                    range = -1f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(eyeDistanceDrift = it)) }
                )

                // Section: Mouth & Jaw
                Text(
                    text = "MOUTH & JAWLINE WARP",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                SliderControl(
                    label = "Mouth Slant / Lip Asymmetry",
                    value = parameters.mouthSlant,
                    range = -1f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(mouthSlant = it)) }
                )

                SliderControl(
                    label = "Mouth Morph Offset",
                    value = parameters.mouthShiftY,
                    range = -1f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(mouthShiftY = it)) }
                )

                SliderControl(
                    label = "Jawline Width Drift",
                    value = parameters.jawWidthDrift,
                    range = -1f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(jawWidthDrift = it)) }
                )

                // Section: Diffusion Artifacts
                Text(
                    text = "DIFFUSION LATENT ARTIFACTS",
                    color = RoseAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                SliderControl(
                    label = "Skin Texture Softening (Micro-Blur)",
                    value = parameters.textureSoftening,
                    range = 0f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(textureSoftening = it)) }
                )

                SliderControl(
                    label = "Chromatic / Temperature Drift",
                    value = parameters.colorVariance,
                    range = -1f..1f,
                    displayFormat = { "${(it * 100).roundToInt()}%" },
                    onValueChange = { onParametersChange(parameters.copy(colorVariance = it)) }
                )
            }
        }
    }
}

@Composable
private fun SliderControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayFormat: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = TextSecondary, fontSize = 11.sp)
            Text(text = displayFormat(value), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = StudioSurfaceVariant
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
