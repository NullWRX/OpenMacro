package io.github.nullwrx.openmacro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.OpenMacroApp
import io.github.nullwrx.openmacro.ui.theme.LocalMacroColors
import io.github.nullwrx.openmacro.ui.theme.NumberStyle

/** Creates a ViewModel with the app's dependencies and its navigation arguments. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as OpenMacroApp).container
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as OpenMacroApp).container

/** A text field for numbers that keeps what the person typed, including a trailing ".". */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (text.length <= 9 && text.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(text) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        textStyle = MaterialTheme.typography.bodyLarge.merge(NumberStyle),
        modifier = modifier,
    )
}

/** A read-only dropdown for choosing one of [options]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A compact text button that opens a menu, used for picking a meal in toolbars. */
@Composable
fun MealPicker(meals: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(meals.getOrElse(selected) { "Meal" })
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose meal")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        meals.forEachIndexed { i, name ->
            DropdownMenuItem(text = { Text(name) }, onClick = {
                onSelect(i)
                open = false
            })
        }
    }
}

/** "Protein 82 / 120 g" with a progress bar in the macro's color. */
@Composable
fun MacroProgress(label: String, eaten: Double, target: Int, color: Color, modifier: Modifier = Modifier, unit: String = "g") {
    val fraction = if (target > 0) (eaten / target).toFloat() else 0f
    val over = target > 0 && eaten > target * 1.05
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${Format.gramsValue(eaten)} / $target $unit",
                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = if (over) LocalMacroColors.current.over else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}

/** A small "label: value" row used in nutrition panels. */
@Composable
fun NutrientRow(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false, indent: Boolean = false) {
    Row(
        modifier.fillMaxWidth().padding(start = if (indent) 16.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (indent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            value,
            style = (if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).merge(NumberStyle),
        )
    }
}

enum class TopLevel(val route: String, val label: String) {
    DIARY(Routes.DIARY, "Diary"),
    WEIGHT(Routes.WEIGHT, "Weight"),
    SETTINGS(Routes.SETTINGS, "Settings"),
}

@Composable
fun AppBottomBar(current: TopLevel, onNavigate: (TopLevel) -> Unit) {
    NavigationBar {
        TopLevel.entries.forEach { item ->
            NavigationBarItem(
                selected = item == current,
                onClick = { if (item != current) onNavigate(item) },
                icon = {
                    Icon(
                        when (item) {
                            TopLevel.DIARY -> Icons.Outlined.Book
                            TopLevel.WEIGHT -> Icons.Outlined.MonitorWeight
                            TopLevel.SETTINGS -> Icons.Outlined.Settings
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}
