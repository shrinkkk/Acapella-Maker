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
            // Stacked only up to 5: beyond that the strips get too thin.
            if (takes <= 5) add(grid("Stacked", takes, rows = takes, cols = 1))
            // Side by side only for 2: with 3+ the columns get too thin to use.
            if (takes == 2) add(grid("Side by side", takes, rows = 1, cols = 2))

            // Square-ish grids. A partly filled last row is centred.
            val wideRows = ceil(takes / side.toDouble()).toInt()
            // Skipped for 5: three across with two centred below wasn't wanted.
            if (takes != 5) add(grid("Grid $wideRows×$side", takes, rows = wideRows, cols = side))
            val tallCols = ceil(takes / side.toDouble()).toInt()
            add(grid("Grid $side×$tallCols", takes, rows = side, cols = tallCols))

            // Rows that stretch to fill the full width.
            when (takes) {
                3 -> add(filledRows("2 + 1 wide", listOf(2, 1)))
                5 -> add(filledRows("2 + 2 + 1 wide", listOf(2, 2, 1)))
                7 -> {
                    add(filledRows("3 + 3 + 1 wide", listOf(3, 3, 1)))
                    add(filledRows("3 + 2 + 2", listOf(3, 2, 2)))
                }
                8 -> add(filledRows("3 + 3 + 2 wide", listOf(3, 3, 2)))
            }
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

    /** Equal-height rows; each row's cells split the full width evenly (e.g. [2, 1] = two on top, one wide below). */
    private fun filledRows(name: String, cellsPerRow: List<Int>): GridLayout {
        val h = 1f / cellsPerRow.size
        val cells = cellsPerRow.flatMapIndexed { row, count ->
            val w = 1f / count
            (0 until count).map { col -> CellRect(col * w, row * h, w, h) }
        }
        return GridLayout(name, cells)
    }
}
