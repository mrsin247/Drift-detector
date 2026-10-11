package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriftParameters
import com.example.model.FaceLandmarks
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.StudioBackground
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun LandmarkOverlayView(
    bitmap: Bitmap,
    landmarks: FaceLandmarks,
    parameters: DriftParameters,
    onLandmarkDrag: (String, PointF) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var activeNode by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("landmark_overlay_view")
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Facial mesh alignment",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(landmarks) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val imgW = bitmap.width.toFloat()
                            val imgH = bitmap.height.toFloat()
                            val scale = min(canvasW / imgW, canvasH / imgH)
                            val left = (canvasW - imgW * scale) / 2f
                            val top = (canvasH - imgH * scale) / 2f

                            fun toCanvas(p: PointF) = Offset(left + p.x * imgW * scale, top + p.y * imgH * scale)
                            val threshold = 36.dp.toPx()

                            val nodes = listOf(
                                "leftEye" to toCanvas(landmarks.leftEye),
                                "rightEye" to toCanvas(landmarks.rightEye),
                                "nose" to toCanvas(landmarks.nose),
                                "mouth" to toCanvas(landmarks.mouth),
                                "jawLeft" to toCanvas(landmarks.jawLeft),
                                "jawRight" to toCanvas(landmarks.jawRight)
                            )
                            activeNode = nodes.firstOrNull { (_, pos) ->
                                hypot(offset.x - pos.x, offset.y - pos.y) <= threshold
                            }?.first
                        },
                        onDrag = { change, _ ->
                            activeNode?.let { node ->
                                change.consume()
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val imgW = bitmap.width.toFloat()
                                val imgH = bitmap.height.toFloat()
                                val scale = min(canvasW / imgW, canvasH / imgH)
                                val left = (canvasW - imgW * scale) / 2f
                                val top = (canvasH - imgH * scale) / 2f

                                val normX = ((change.position.x - left) / (imgW * scale)).coerceIn(0.1f, 0.9f)
                                val normY = ((change.position.y - top) / (imgH * scale)).coerceIn(0.1f, 0.9f)
                                onLandmarkDrag(node, PointF(normX, normY))
                            }
                        },
                        onDragEnd = { activeNode = null },
                        onDragCancel = { activeNode = null }
                    )
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val scale = min(canvasW / imgW, canvasH / imgH)
            val left = (canvasW - imgW * scale) / 2f
            val top = (canvasH - imgH * scale) / 2f

            fun toCanvas(p: PointF): Offset {
                return Offset(left + p.x * imgW * scale, top + p.y * imgH * scale)
            }

            val le = toCanvas(landmarks.leftEye)
            val re = toCanvas(landmarks.rightEye)
            val n = toCanvas(landmarks.nose)
            val m = toCanvas(landmarks.mouth)
            val lmc = toCanvas(landmarks.leftMouthCorner)
            val rmc = toCanvas(landmarks.rightMouthCorner)
            val jl = toCanvas(landmarks.jawLeft)
            val jr = toCanvas(landmarks.jawRight)
            val ch = toCanvas(landmarks.chin)

            // Connective facial wireframe mesh
            val meshColor = CyanAccent.copy(alpha = 0.45f)
            val wireWidth = 1.5.dp.toPx()

            // Eyes crossline
            drawLine(meshColor, le, re, wireWidth)
            // Eyes to nose
            drawLine(meshColor, le, n, wireWidth)
            drawLine(meshColor, re, n, wireWidth)
            // Nose to mouth
            drawLine(meshColor, n, m, wireWidth)
            // Mouth contour
            drawLine(meshColor, lmc, m, wireWidth)
            drawLine(meshColor, m, rmc, wireWidth)
            // Jawline contour
            drawLine(meshColor, jl, ch, wireWidth)
            drawLine(meshColor, jr, ch, wireWidth)
            drawLine(meshColor, le, jl, wireWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawLine(meshColor, re, jr, wireWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))

            // Displacement vectors (from original reference anchor to drifted position)
            val maxShiftPx = (hypot(re.x - le.x, re.y - le.y) * 0.16f * parameters.cumulativeMultiplier)

            val leftEyeDrift = Offset(
                (parameters.leftEyeShiftX - parameters.eyeDistanceDrift * 0.4f) * maxShiftPx,
                parameters.leftEyeShiftY * maxShiftPx
            )
            val rightEyeDrift = Offset(
                (parameters.rightEyeShiftX + parameters.eyeDistanceDrift * 0.4f) * maxShiftPx,
                parameters.rightEyeShiftY * maxShiftPx
            )
            val mouthDrift = Offset(
                parameters.mouthShiftX * maxShiftPx,
                parameters.mouthShiftY * maxShiftPx
            )

            drawDisplacementArrow(le, le + leftEyeDrift, RoseAccent)
            drawDisplacementArrow(re, re + rightEyeDrift, RoseAccent)
            drawDisplacementArrow(m, m + mouthDrift, RoseAccent)

            // Draw Landmark Anchor Nodes
            listOf(
                le to "Left Eye",
                re to "Right Eye",
                n to "Nose",
                m to "Mouth",
                jl to "Jaw L",
                jr to "Jaw R",
                ch to "Chin"
            ).forEach { (pos, _) ->
                drawCircle(Color.Black.copy(alpha = 0.7f), radius = 8.dp.toPx(), center = pos)
                drawCircle(CyanAccent, radius = 5.dp.toPx(), center = pos)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = pos)
            }
        }

        // Info Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = "FACIAL LANDMARKS & DRIFT VECTORS",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Drag nodes to align with custom face contours",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun DrawScope.drawDisplacementArrow(start: Offset, end: Offset, color: Color) {
    val length = hypot(end.x - start.x, end.y - start.y)
    if (length < 3f) return

    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 3.dp.toPx()
    )

    // Arrowhead
    val angle = atan2(end.y - start.y, end.x - start.x)
    val headLen = 14f
    val headAngle = 0.5f

    val arrowP1 = Offset(
        end.x - headLen * cos(angle - headAngle),
        end.y - headLen * sin(angle - headAngle)
    )
    val arrowP2 = Offset(
        end.x - headLen * cos(angle + headAngle),
        end.y - headLen * sin(angle + headAngle)
    )

    drawLine(color = color, start = end, end = arrowP1, strokeWidth = 3.dp.toPx())
    drawLine(color = color, start = end, end = arrowP2, strokeWidth = 3.dp.toPx())
}
