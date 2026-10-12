package io.github.nullwrx.openmacro.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.LabelScan
import io.github.nullwrx.openmacro.core.Barcodes
import io.github.nullwrx.openmacro.core.label.LabelAccumulator
import io.github.nullwrx.openmacro.core.label.LabelParser
import io.github.nullwrx.openmacro.core.label.ParsedLabel
import io.github.nullwrx.openmacro.data.BarcodeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScanMode { BARCODE, LABEL }

sealed interface BarcodeUi {
    data object Scanning : BarcodeUi
    data class LookingUp(val code: String) : BarcodeUi
    data class Done(val result: BarcodeResult) : BarcodeUi
}

data class LabelUi(val readings: Int = 0, val best: ParsedLabel = ParsedLabel())

class ScannerViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val mode: ScanMode = ScanMode.valueOf(checkNotNull<String>(handle["mode"]))
    val barcodeArg: String? = handle["barcode"]
    val returnToEditor: Boolean = handle["returnToEditor"] ?: false
    /** Day and meal to log to, passed through to the next screen; -1 lets it choose (today, meal by time). */
    val date: Long = handle["date"] ?: -1L
    val meal: Int = handle["meal"] ?: -1

    private val _barcode = MutableStateFlow<BarcodeUi>(BarcodeUi.Scanning)
    val barcode: StateFlow<BarcodeUi> = _barcode.asStateFlow()

    private val accumulator = LabelAccumulator()
    private val _label = MutableStateFlow(LabelUi())
    val label: StateFlow<LabelUi> = _label.asStateFlow()

    /** Called from the camera analyzer for every decoded barcode. */
    fun onBarcode(raw: String) {
        val code = Barcodes.normalize(raw)
        if (_barcode.value !is BarcodeUi.Scanning || !Barcodes.isValidGtin(code)) return
        lookup(code)
    }

    fun lookup(code: String) {
        _barcode.value = BarcodeUi.LookingUp(code)
        viewModelScope.launch {
            _barcode.value = BarcodeUi.Done(c.foods.lookupBarcode(code))
        }
    }

    fun resumeScanning() {
        _barcode.value = BarcodeUi.Scanning
    }

    /** Called from the camera analyzer with OCR text, about twice a second. */
    fun onLabelText(text: String) {
        val parsed = LabelParser.parse(text)
        if (parsed.nutrientCount == 0) return
        synchronized(accumulator) {
            accumulator.add(parsed)
            _label.value = LabelUi(accumulator.readings, accumulator.best())
        }
    }

    fun resetLabel() {
        synchronized(accumulator) {
            accumulator.reset()
            _label.value = LabelUi()
        }
    }

    /** Hands the label values to the food editor. */
    fun finishLabel() {
        c.pendingLabelScan.value = LabelScan(_label.value.best, barcodeArg)
    }
}
