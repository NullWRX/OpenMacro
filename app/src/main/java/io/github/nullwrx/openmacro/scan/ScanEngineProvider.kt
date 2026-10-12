package io.github.nullwrx.openmacro.scan

import androidx.camera.core.ImageAnalysis

/**
 * Camera analyzers differ per build: the Play build uses Google's on-device ML Kit models,
 * the F-Droid build uses only free software (ZXing). Each flavor provides `ScanEngines`.
 */
interface ScanEngineProvider {
    /** Shown in About so people know which engine their build uses. */
    val engineName: String

    val supportsLabelOcr: Boolean

    /** Calls [onBarcode] with a product code (UPC-E already expanded to UPC-A). */
    fun barcodeAnalyzer(onBarcode: (String) -> Unit): ImageAnalysis.Analyzer

    /** Calls [onText] with OCR text rebuilt into label rows, or null when OCR is unavailable. */
    fun labelAnalyzer(onText: (String) -> Unit): ImageAnalysis.Analyzer?
}
