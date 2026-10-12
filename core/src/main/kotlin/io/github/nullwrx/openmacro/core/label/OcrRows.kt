package io.github.nullwrx.openmacro.core.label

import kotlin.math.abs
import kotlin.math.max

/**
 * OCR engines return text in blocks, often one block per table column, so "Total Fat" and "8g"
 * arrive far apart. This rebuilds the label's rows from each text line's position: lines whose
 * vertical centres sit close together form one row, read left to right.
 */
object OcrRows {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int, val text: String) {
        val centerY: Double get() = (top + bottom) / 2.0
        val height: Int get() = (bottom - top).coerceAtLeast(1)
    }

    fun toText(boxes: List<Box>): String {
        val sorted = boxes.filter { it.text.isNotBlank() }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<Box>>()
        for (box in sorted) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.sumOf { it.centerY } / row.size
                val rowHeight = row.maxOf { it.height }
                if (abs(box.centerY - rowCenter) <= 0.5 * max(rowHeight, box.height)) {
                    row += box
                    continue
                }
            }
            rows += mutableListOf(box)
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
