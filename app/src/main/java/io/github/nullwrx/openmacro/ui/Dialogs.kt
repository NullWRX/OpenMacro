package io.github.nullwrx.openmacro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** Material date picker working in epoch days (the picker itself uses UTC milliseconds). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

/** Pick a day and a meal, for moving or copying diary items. */
@Composable
fun DestinationDialog(
    title: String,
    confirmLabel: String,
    initialDay: Long,
    initialMeal: Int,
    meals: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (day: Long, meal: Int) -> Unit,
) {
    var day by remember { mutableLongStateOf(initialDay) }
    var meal by remember { mutableIntStateOf(initialMeal.coerceIn(0, (meals.size - 1).coerceAtLeast(0))) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Text(
                        Format.dayTitle(LocalDate.ofEpochDay(day)),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                    meals.forEachIndexed { i, name ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = meal == i, onClick = { meal = i }, role = Role.RadioButton)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = meal == i, onClick = null)
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(day, meal)
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        DayPickerDialog(initialDay = day, onDismiss = { picking = false }, onPick = { day = it })
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
