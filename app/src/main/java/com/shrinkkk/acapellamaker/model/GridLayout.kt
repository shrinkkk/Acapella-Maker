package com.shrinkkk.acapellamaker.model

import kotlin.math.ceil
import kotlin.math.sqrt

/** A cell's position in the final video, as fractions (0..1) of canvas width/height. */
data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float)

/** How takes are arranged on the final video canvas. One cell per take, in take order. */
data class GridLayout(val name: String, val cells: List<CellRect>) {
    val takeCount: Int get() = cells.size
}

object GridLayouts {
    /** Final video is phone-shaped portrait (9:16). Width / height. */
    const val CANVAS_ASPECT = 9f / 16f
    const val MAX_TAKES = 9

    /** A few standard arrangements for [takes] takes, without duplicates. */
    fun optionsFor(takes: Int): List<GridLayout> {
        require(takes in 1..MAX_TAKES)
        if (takes == 1) return listOf(GridLayout("Full screen", listOf(CellRect(0f, 0f, 1f, 1f))))

        val side = ceil(sqrt(takes.toDouble())).toInt()
        val candidates = buildList {
            if (takes <= 6) add(grid("Stacked", takes, rows = takes, cols = 1))
            if (takes <= 4) add(grid("Side by side", takes, rows = 1, cols = takes))
            // "Wide" grid: more columns than rows when it can't be square, and the opposite.
            val wideRows = ceil(takes / side.toDouble()).toInt()
            add(grid("Grid $wideRows×$side", takes, rows = wideRows, cols = side))
            val tallCols = ceil(takes / side.toDouble()).toInt()
            add(grid("Grid $side×$tallCols", takes, rows = side, cols = tallCols))
        }
        return candidates.distinctBy { it.cells }
    }

    /** rows x cols grid filled left-to-right, top-to-bottom; a partly filled last row is centred. */
    private fun grid(name: String, takes: Int, rows: Int, cols: Int): GridLayout {
        val w = 1f / cols
        val h = 1f / rows
        val cells = (0 until takes).map { i ->
            val row = i / cols
            val col = i % cols
            val cellsInRow = if (row == rows - 1) takes - row * cols else cols
            val rowOffset = (cols - cellsInRow) * w / 2f
            CellRect(rowOffset + col * w, row * h, w, h)
        }
        return GridLayout(name, cells)
    }
}
