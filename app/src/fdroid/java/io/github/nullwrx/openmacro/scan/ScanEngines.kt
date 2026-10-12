package io.github.nullwrx.openmacro.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import io.github.nullwrx.openmacro.core.Barcodes

/** F-Droid build: ZXing only. Label OCR waits for a free OCR engine (see the roadmap). */
object ScanEngines : ScanEngineProvider {
    override val engineName = "ZXing"
    override val supportsLabelOcr = false

    override fun barcodeAnalyzer(onBarcode: (String) -> Unit): ImageAnalysis.Analyzer =
        ZxingBarcodeAnalyzer(onBarcode)

    override fun labelAnalyzer(onText: (String) -> Unit): ImageAnalysis.Analyzer? = null
}

private class ZxingBarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                ),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val luminance = ByteArray(width * height)
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(luminance, y * width, width)
            }
            val rotation = image.imageInfo.rotationDegrees
            val upright = rotation == 90 || rotation == 270
            val data = if (upright) rotate90(luminance, width, height) else luminance
            val w = if (upright) height else width
            val h = if (upright) width else height
            val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            val result = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (e: ReaderException) {
                null
            } finally {
                reader.reset()
            }
            if (result != null) {
                val text = result.text
                val value = if (result.barcodeFormat == BarcodeFormat.UPC_E) Barcodes.upcEToUpcA(text) ?: text else text
                onBarcode(value)
            }
        } catch (e: Exception) {
            // A bad frame is skipped; the next one gets a fresh attempt.
        } finally {
            image.close()
        }
    }

    private fun rotate90(src: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = src[row + x]
            }
        }
        return out
    }
}
