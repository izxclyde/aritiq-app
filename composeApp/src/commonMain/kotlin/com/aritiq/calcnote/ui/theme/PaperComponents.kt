package com.aritiq.calcnote.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Procedural paper grain overlay.
 * Draws a grid of small semi-transparent rectangles whose position is deterministic
 * (hash-based) so it doesn't flicker. Uses the theme outline color so it reads correctly
 * on both cream (light) and sepia (dark) backgrounds.
 * ~3% opacity keeps it subtle without hurting readability.
 */
@Composable
fun PaperGrainOverlay(
    modifier: Modifier = Modifier,
    opacity: Float = 0.03f,
) {
    val grainColor = MaterialTheme.colorScheme.outline
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

                    // Offset each cell's fill slightly for organic feel
                    val offsetX = ((hash shr 8) and 0xFF).toFloat() / 256f * cell * 0.3f
                    val offsetY = ((hash shr 16) and 0xFF).toFloat() / 256f * cell * 0.3f

                    drawRect(
                        color = grainColor,
                        topLeft = Offset(col * cell + offsetX, row * cell + offsetY),
                        size = Size(cell * 0.6f, cell * 0.6f),
                    )
                }
            }
        }
    }
}
