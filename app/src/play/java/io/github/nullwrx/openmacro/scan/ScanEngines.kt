package io.github.nullwrx.openmacro.scan

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.nullwrx.openmacro.core.Barcodes
import io.github.nullwrx.openmacro.core.label.OcrRows

/** Play build: ML Kit with bundled models, fully on-device and offline. */
object ScanEngines : ScanEngineProvider {
    override val engineName = "ML Kit (on-device)"
    override val supportsLabelOcr = true

    override fun barcodeAnalyzer(onBarcode: (String) -> Unit): ImageAnalysis.Analyzer =
        MlKitBarcodeAnalyzer(onBarcode)

    override fun labelAnalyzer(onText: (String) -> Unit): ImageAnalysis.Analyzer =
        MlKitTextAnalyzer(onText)
}

private class MlKitBarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val code = codes.firstOrNull { !it.rawValue.isNullOrBlank() } ?: return@addOnSuccessListener
                val raw = code.rawValue!!
                val value = if (code.format == Barcode.FORMAT_UPC_E) Barcodes.upcEToUpcA(raw) ?: raw else raw
                onBarcode(value)
            }
            .addOnCompleteListener { image.close() }
    }
}

private class MlKitTextAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastRun = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = image.image
        if (media == null || now - lastRun < MIN_INTERVAL_MS) {
            image.close()
            return
        }
        lastRun = now
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val boxes = result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                    val r = line.boundingBox ?: return@mapNotNull null
                    OcrRows.Box(r.left, r.top, r.right, r.bottom, line.text)
                }
                if (boxes.isNotEmpty()) onText(OcrRows.toText(boxes))
            }
            .addOnCompleteListener { image.close() }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 500L
    }
}
