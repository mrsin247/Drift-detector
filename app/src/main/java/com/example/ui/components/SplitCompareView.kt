package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.StudioBackground
import kotlin.math.max
import kotlin.math.min

@Composable
fun SplitCompareView(
    referenceBitmap: Bitmap,
    driftedBitmap: Bitmap,
    splitPosition: Float,
    onSplitChange: (Float) -> Unit,
    genNumber: Int,
    modifier: Modifier = Modifier
) {
    val refImage = remember(referenceBitmap) { referenceBitmap.asImageBitmap() }
    val driftImage = remember(driftedBitmap) { driftedBitmap.asImageBitmap() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("split_compare_view")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newPos = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                        onSplitChange(newPos)
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // Calculate scaled drawing destination maintaining aspect ratio
            val imgW = refImage.width.toFloat()
            val imgH = refImage.height.toFloat()
            val scale = min(canvasW / imgW, canvasH / imgH)

            val destW = (imgW * scale)
            val destH = (imgH * scale)
            val left = (canvasW - destW) / 2f
            val top = (canvasH - destH) / 2f

            val splitX = canvasW * splitPosition

            val destOffset = IntOffset(left.toInt(), top.toInt())
            val destSize = IntSize(destW.toInt(), destH.toInt())

            // 1. Draw Drifted Image on entire area or right side
            clipRect(left = splitX, top = 0f, right = canvasW, bottom = canvasH) {
                drawImage(
                    image = driftImage,
                    dstOffset = destOffset,
                    dstSize = destSize
                )
            }

            // 2. Draw Reference Image on the left side
            clipRect(left = 0f, top = 0f, right = splitX, bottom = canvasH) {
                drawImage(
                    image = refImage,
                    dstOffset = destOffset,
                    dstSize = destSize
                )
            }

            // 3. Draw Split Divider Line
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(splitX, top),
                end = Offset(splitX, top + destH),
                strokeWidth = 3.dp.toPx()
            )

            // Split handle circle with cyan & purple accents
            val centerY = top + destH / 2f
            drawCircle(
                color = Color.Black.copy(alpha = 0.6f),
                radius = 18.dp.toPx(),
                center = Offset(splitX, centerY)
            )
            drawCircle(
                color = CyanAccent,
                radius = 14.dp.toPx(),
                center = Offset(splitX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(splitX, centerY)
            )
        }

        // Left Label: Original Reference
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "GEN 0: REFERENCE",
                color = CyanAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Right Label: Drifted Output
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (genNumber == 0) "SIMULATED DRIFT" else "GEN $genNumber (DRIFTED)",
                color = PurpleAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
