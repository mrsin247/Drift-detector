package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriftMetrics
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.HeatmapHot
import com.example.ui.theme.HeatmapWarm
import com.example.ui.theme.StudioBackground

@Composable
fun DriftHeatmapView(
    heatmapBitmap: Bitmap,
    metrics: DriftMetrics,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(heatmapBitmap) { heatmapBitmap.asImageBitmap() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("drift_heatmap_view"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Facial likeness drift heatmap",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Top Status Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = "DELTA HEATMAP",
                    color = HeatmapHot,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Peak Shift: ${metrics.maxFeatureShiftPx} px • Mean: ${metrics.meanFeatureShiftPx} px",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        // Bottom Color Scale Legend
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0% Shift (Anchored)", color = CyanAccent, fontSize = 10.sp)
                    Text("Moderate (3-5%)", color = HeatmapWarm, fontSize = 10.sp)
                    Text("Critical (>8% Morph)", color = HeatmapHot, fontSize = 10.sp)
                }

                // Gradient Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF0284C7),
                                    Color(0xFF10B981),
                                    Color(0xFFF59E0B),
                                    Color(0xFFEF4444)
                                )
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}
