package com.aritiq.calcnote.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val grainColors = intArrayOf(
    0xFFD7CCC8.toInt(), // warm tan
    0xFFBCAAA4.toInt(), // muted brown
    0xFFC4B8A8.toInt(), // grey-brown
    0xFFB8ADA3.toInt(), // cool tan
    0xFFCCC4B8.toInt(), // light warm
)

/**
 * Procedural paper grain overlay.
 * Draws a grid of small semi-transparent warm-tone rectangles whose
 * position is deterministic (hash-based) so it doesn't flicker.
 * ~3% opacity keeps it subtle without hurting readability.
 */
@Composable
fun PaperGrainOverlay(
    modifier: Modifier = Modifier,
    opacity: Float = 0.03f,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellDp = 6f
            val cell = cellDp.dp.toPx()
            val cols = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1

            for (row in 0..rows) {
                for (col in 0..cols) {
                    // Deterministic pseudo-random: simple integer hash
                    val seed = col * 7919 + row * 6271
                    val hash = (seed xor (seed shr 13)) * 0x5bd1e995
                    val bucket = (hash and 0x7FFFFFFF) % grainColors.size
                    val color = Color(grainColors[bucket])

                    // Offset each cell's fill slightly for organic feel
                    val offsetX = ((hash shr 8) and 0xFF).toFloat() / 256f * cell * 0.3f
                    val offsetY = ((hash shr 16) and 0xFF).toFloat() / 256f * cell * 0.3f

                    drawRect(
                        color = color,
                        topLeft = Offset(col * cell + offsetX, row * cell + offsetY),
                        size = Size(cell * 0.6f, cell * 0.6f),
                    )
                }
            }
        }
    }
}
