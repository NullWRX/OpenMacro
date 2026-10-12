package io.github.nullwrx.openmacro.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.core.label.LabelField
import io.github.nullwrx.openmacro.data.BarcodeResult
import io.github.nullwrx.openmacro.scan.ScanEngines
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.theme.NumberStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onFoodFound: (foodId: Long, date: Long, meal: Int) -> Unit,
    onScanLabelInstead: (barcode: String, date: Long, meal: Int) -> Unit,
    onEnterManually: (barcode: String?, name: String?, brand: String?, date: Long, meal: Int) -> Unit,
    onLabelDone: (returnToEditor: Boolean, barcode: String?, date: Long, meal: Int) -> Unit,
) {
    val vm = appViewModel { c, h -> ScannerViewModel(c, h) }
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val labelSupported = ScanEngines.supportsLabelOcr

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.mode == ScanMode.BARCODE) "Scan barcode" else "Scan nutrition label") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (torchAvailable) {
                        IconButton(onClick = { torchOn = !torchOn }) {
                            Icon(
                                if (torchOn) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                                contentDescription = if (torchOn) "Turn light off" else "Turn light on",
                            )
                        }
                    }
                    if (vm.mode == ScanMode.BARCODE) {
                        IconButton(onClick = { typing = true }) {
                            Icon(Icons.Outlined.Keyboard, contentDescription = "Type the barcode")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val cameraWanted = vm.mode == ScanMode.BARCODE || labelSupported
            when {
                !cameraWanted -> LabelUnsupported(
                    onManual = {
                        if (vm.returnToEditor) onBack() else onEnterManually(vm.barcodeArg, null, null, vm.date, vm.meal)
                    },
                )
                !granted -> PermissionNeeded(
                    permanentlyDenied = askedOnce,
                    onAsk = { permission.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
                vm.mode == ScanMode.BARCODE -> BarcodeMode(vm, torchOn, { torchAvailable = it }, onFoodFound, onScanLabelInstead, onEnterManually)
                else -> LabelMode(vm, torchOn, { torchAvailable = it }, onLabelDone)
            }
        }
    }

    if (typing) {
        TypeBarcodeDialog(
            onDismiss = { typing = false },
            onSubmit = { code ->
                typing = false
                vm.lookup(code)
            },
        )
    }
}

@Composable
private fun BarcodeMode(
    vm: ScannerViewModel,
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onFoodFound: (foodId: Long, date: Long, meal: Int) -> Unit,
    onScanLabelInstead: (barcode: String, date: Long, meal: Int) -> Unit,
    onEnterManually: (barcode: String?, name: String?, brand: String?, date: Long, meal: Int) -> Unit,
) {
    val state by vm.barcode.collectAsStateWithLifecycle()
    val analyzer = remember { ScanEngines.barcodeAnalyzer { vm.onBarcode(it) } }

    LaunchedEffect(state) {
        val s = state
        if (s is BarcodeUi.Done && s.result is BarcodeResult.Found) {
            onFoodFound(s.result.food.id, vm.date, vm.meal)
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(analyzer, Size(1280, 720), torchOn, Modifier.fillMaxSize(), onTorchAvailable)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 280.dp, height = 160.dp)
                .border(BorderStroke(3.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(16.dp)),
        )
        StatusCard(Modifier.align(Alignment.BottomCenter)) {
            when (val s = state) {
                BarcodeUi.Scanning -> Text("Point the camera at a barcode. It scans on its own.")
                is BarcodeUi.LookingUp -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Looking up ${s.code}…")
                }
                is BarcodeUi.Done -> Text("Barcode ${s.result.code}")
            }
        }
    }

    val done = state as? BarcodeUi.Done
    when (val r = done?.result) {
        is BarcodeResult.Incomplete -> ScanResultDialog(
            title = r.name,
            text = "Open Food Facts knows this product but has no nutrition facts for it yet. Scan the label to add them.",
            code = r.code,
            onLabel = { onScanLabelInstead(r.code, vm.date, vm.meal) },
            onManual = { onEnterManually(r.code, r.name, r.brand, vm.date, vm.meal) },
            onRetry = vm::resumeScanning,
            retryLabel = "Scan another",
        )
        is BarcodeResult.NotFound -> ScanResultDialog(
            title = "Not found",
            text = "Barcode ${r.code} is not on this phone or in Open Food Facts. Add it once and it will be there next time.",
            code = r.code,
            onLabel = { onScanLabelInstead(r.code, vm.date, vm.meal) },
            onManual = { onEnterManually(r.code, null, null, vm.date, vm.meal) },
            onRetry = vm::resumeScanning,
            retryLabel = "Scan another",
        )
        is BarcodeResult.Offline -> ScanResultDialog(
            title = "Couldn't look it up",
            text = "Open Food Facts could not be reached (${r.message}). You can add this food from its label instead.",
            code = r.code,
            onLabel = { onScanLabelInstead(r.code, vm.date, vm.meal) },
            onManual = { onEnterManually(r.code, null, null, vm.date, vm.meal) },
            onRetry = { vm.lookup(r.code) },
            retryLabel = "Try again",
        )
        else -> Unit
    }
}

@Composable
private fun LabelMode(
    vm: ScannerViewModel,
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onLabelDone: (returnToEditor: Boolean, barcode: String?, date: Long, meal: Int) -> Unit,
) {
    val state by vm.label.collectAsStateWithLifecycle()
    val analyzer = remember { ScanEngines.labelAnalyzer { vm.onLabelText(it) } }
    val best = state.best

    Box(Modifier.fillMaxSize()) {
        if (analyzer != null) {
            CameraPreview(analyzer, Size(1920, 1080), torchOn, Modifier.fillMaxSize(), onTorchAvailable)
        }
        StatusCard(Modifier.align(Alignment.BottomCenter)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (best.values.isEmpty()) {
                    Text("Hold the nutrition table flat and fill the screen with it. Values appear here as they are read.")
                } else {
                    Text(
                        if (best.per100) "Per 100 ${best.servingUnit ?: "g"}" else "Per serving" +
                            (best.servingText?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val shown = LabelField.entries.filter { it.isNutrient && best[it] != null }
                    shown.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { field ->
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(field.label, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${Format.amount(best[field] ?: 0.0)} ${field.unit}",
                                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        "Read ${state.readings} times. Keep holding steady to firm up the numbers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::resetLabel, enabled = state.readings > 0) { Text("Start over") }
                    Button(
                        onClick = {
                            vm.finishLabel()
                            onLabelDone(vm.returnToEditor, vm.barcodeArg, vm.date, vm.meal)
                        },
                        enabled = best.nutrientCount >= 2,
                        modifier = Modifier.weight(1f),
                    ) { Text("Use these values") }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ScanResultDialog(
    title: String,
    text: String,
    code: String,
    onLabel: () -> Unit,
    onManual: () -> Unit,
    onRetry: () -> Unit,
    retryLabel: String,
) {
    val labelSupported = ScanEngines.supportsLabelOcr
    AlertDialog(
        onDismissRequest = onRetry,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                Text("Barcode $code", style = MaterialTheme.typography.bodySmall.merge(NumberStyle))
            }
        },
        confirmButton = {
            if (labelSupported) {
                TextButton(onClick = onLabel) { Text("Scan label") }
            } else {
                TextButton(onClick = onManual) { Text("Enter it") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRetry) { Text(retryLabel) }
                if (labelSupported) TextButton(onClick = onManual) { Text("Type it in") }
            }
        },
    )
}

@Composable
private fun TypeBarcodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type the barcode") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter(Char::isDigit).take(14) },
                label = { Text("Digits under the bars") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = code.length >= 8) { Text("Look up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PermissionNeeded(permanentlyDenied: Boolean, onAsk: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Camera access is needed to scan", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Images are processed on your phone and never uploaded. You can also type a barcode with the keyboard button above.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = if (permanentlyDenied) onOpenSettings else onAsk) {
            Text(if (permanentlyDenied) "Open app settings" else "Allow camera")
        }
    }
}

@Composable
private fun LabelUnsupported(onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Label scanning isn't in this build yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "The F-Droid build only uses free software, and label reading needs an open OCR engine we haven't added yet. " +
                "Barcode scanning works. You can type the label values in for now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onManual) { Text("Enter values") }
    }
}
