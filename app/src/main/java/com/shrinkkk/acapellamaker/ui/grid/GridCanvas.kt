package com.shrinkkk.acapellamaker.ui.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shrinkkk.acapellamaker.model.GridLayout

/**
 * Lays out [layout]'s cells inside this canvas. The caller sets the canvas size/aspect ratio
 * (normally GridLayouts.CANVAS_ASPECT) and draws each cell's content via [cell].
 */
@Composable
fun GridCanvas(
    layout: GridLayout,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
    corner: Dp = 4.dp,
    cell: @Composable BoxScope.(index: Int) -> Unit,
) {
    BoxWithConstraints(modifier) {
        layout.cells.forEachIndexed { index, r ->
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * r.x, y = maxHeight * r.y)
                    .size(width = maxWidth * r.w, height = maxHeight * r.h)
                    .padding(gap / 2)
                    .clip(RoundedCornerShape(corner)),
            ) {
                cell(index)
            }
        }
    }
}
