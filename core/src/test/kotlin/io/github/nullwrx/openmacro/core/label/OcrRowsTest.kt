package io.github.nullwrx.openmacro.core.label

import io.github.nullwrx.openmacro.core.Barcodes
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OcrRowsTest {

    @Test
    fun columnsAreRejoinedIntoRows() {
        // Left column of names and right column of values, as separate OCR blocks.
        val boxes = listOf(
            OcrRows.Box(10, 100, 120, 120, "Total Fat"),
            OcrRows.Box(10, 130, 140, 150, "Saturated Fat"),
            OcrRows.Box(10, 160, 100, 180, "Protein"),
            OcrRows.Box(300, 102, 340, 121, "8g"),
            OcrRows.Box(300, 131, 340, 149, "1g"),
            OcrRows.Box(300, 161, 340, 181, "3g"),
            OcrRows.Box(150, 101, 190, 119, "10%"),
        )
        assertEquals("Total Fat 10% 8g\nSaturated Fat 1g\nProtein 3g", OcrRows.toText(boxes))
    }

    @Test
    fun rejoinedRowsParse() {
        val boxes = listOf(
            OcrRows.Box(10, 100, 120, 120, "Calories"),
            OcrRows.Box(300, 98, 360, 124, "230"),
            OcrRows.Box(10, 140, 120, 160, "Protein"),
            OcrRows.Box(300, 141, 340, 159, "3g"),
        )
        val parsed = LabelParser.parse(OcrRows.toText(boxes))
        assertEquals(230.0, parsed[LabelField.ENERGY_KCAL])
        assertEquals(3.0, parsed[LabelField.PROTEIN])
    }

    @Test
    fun upcEExpandsToUpcA() {
        // Well-known examples: 01234565 -> 012345000065, 04252614 -> 042100005264.
        assertEquals("012345000065", Barcodes.upcEToUpcA("01234565"))
        assertEquals("042100005264", Barcodes.upcEToUpcA("04252614"))
        assertNull(Barcodes.upcEToUpcA("01234566"))
        assertNull(Barcodes.upcEToUpcA("91234565"))
    }
}
