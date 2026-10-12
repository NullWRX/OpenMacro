package io.github.nullwrx.openmacro.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.core.TrendPoint
import io.github.nullwrx.openmacro.core.WeightPoint
import io.github.nullwrx.openmacro.core.WeightTrend
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import io.github.nullwrx.openmacro.ui.AppBottomBar
import io.github.nullwrx.openmacro.ui.DayPickerDialog
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.TopLevel
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.theme.NumberStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

data class WeightState(
    val trend: List<TrendPoint> = emptyList(),
    val weeklyRateKg: Double? = null,
    val unit: WeightUnit = WeightUnit.KG,
)

class WeightViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    val state: StateFlow<WeightState> = combine(c.weights.observeAll(), c.settings.settings) { weights, settings ->
        val trend = WeightTrend.compute(weights.map { WeightPoint(it.date, it.kg) })
        WeightState(trend, WeightTrend.weeklyRateKg(trend), settings.weightUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightState())

    fun log(day: Long, value: Double, unit: WeightUnit) = viewModelScope.launch {
        val kg = Format.toKg(value, unit)
        c.weights.log(day, kg)
        syncPlanningWeight()
    }

    fun delete(day: Long) = viewModelScope.launch {
        c.weights.delete(day)
        syncPlanningWeight()
    }

    /**
     * Keeps the planning weight equal to the newest weigh-in and, when the goal follows weight,
     * recalculates targets so a cut or bulk keeps fitting the body as it changes.
     */
    private suspend fun syncPlanningWeight() {
        val latest = c.weights.latest() ?: return
        c.settings.update { s ->
            if (kotlin.math.abs(s.weightKg - latest.kg) < 0.01) {
                s
            } else {
                val moved = s.copy(weightKg = latest.kg)
                if (s.autoAdjustTargets) moved.withPlannedTargets() else moved
            }
        }
    }
}

private enum class Range(val label: String, val days: Long?) {
    MONTH("1M", 30), QUARTER("3M", 90), YEAR("1Y", 365), ALL("All", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(onNavigate: (TopLevel) -> Unit) {
    val vm = appViewModel { c, h -> WeightViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(Range.QUARTER) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Weight") }) },
        bottomBar = { AppBottomBar(TopLevel.WEIGHT, onNavigate) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Log weight") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        val latest = state.trend.lastOrNull()
                        if (latest == null) {
                            Text("No weigh-ins yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Weigh yourself in the morning, a few times a week. The trend line smooths out daily water swings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Trend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                Format.weight(latest.trendKg, state.unit),
                                style = MaterialTheme.typography.displaySmall.merge(NumberStyle),
                            )
                            val rate = state.weeklyRateKg
                            Text(
                                when {
                                    rate == null -> "Log a few more days to see your weekly rate."
                                    abs(Format.weightValue(rate, state.unit)) < 0.05 -> "Holding steady"
                                    else -> "${if (rate < 0) "Losing" else "Gaining"} " +
                                        "${Format.weight(abs(rate), state.unit)} per week"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Range.entries.forEach { r ->
                                    FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) })
                                }
                            }
                            val lastDay = latest.epochDay
                            val shown = state.trend.filter { p -> range.days?.let { p.epochDay > lastDay - it } ?: true }
                            WeightChart(shown, Modifier.fillMaxWidth().height(180.dp).padding(top = 12.dp))
                        }
                    }
                }
            }
            items(state.trend.asReversed(), key = { it.epochDay }) { p ->
                ListItem(
                    headlineContent = { Text(Format.weight(p.kg, state.unit), style = MaterialTheme.typography.bodyLarge.merge(NumberStyle)) },
                    supportingContent = { Text(Format.dayTitle(LocalDate.ofEpochDay(p.epochDay))) },
                    trailingContent = {
                        IconButton(onClick = { vm.delete(p.epochDay) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete weigh-in")
                        }
                    },
                )
            }
        }
    }

    if (adding) {
        AddWeightDialog(
            unit = state.unit,
            initial = state.trend.lastOrNull()?.kg?.let { Format.amount(Format.weightValue(it, state.unit)) } ?: "",
            onDismiss = { adding = false },
            onSave = { day, value ->
                vm.log(day, value, state.unit)
                adding = false
            },
        )
    }
}

@Composable
private fun AddWeightDialog(unit: WeightUnit, initial: String, onDismiss: () -> Unit, onSave: (Long, Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var day by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var picking by remember { mutableStateOf(false) }
    val value = Format.parse(text)?.takeIf { it in 20.0..700.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Weight", suffix = Format.weightUnit(unit))
                OutlinedButton(onClick = { picking = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Text(Format.dayTitle(LocalDate.ofEpochDay(day)), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onSave(day, it) } }, enabled = value != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) DayPickerDialog(day, onDismiss = { picking = false }, onPick = { day = it })
}

/** Daily weigh-ins as dots with the smoothed trend as a line. */
@Composable
private fun WeightChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val minDay = points.first().epochDay
        val maxDay = points.last().epochDay
        val values = points.flatMap { listOf(it.kg, it.trendKg) }
        val lo = values.min()
        val hi = values.max()
        val pad = ((hi - lo) * 0.15).coerceAtLeast(0.5)
        val yMin = lo - pad
        val yMax = hi + pad
        fun x(day: Long): Float =
            if (maxDay == minDay) size.width / 2 else ((day - minDay).toFloat() / (maxDay - minDay)) * size.width
        fun y(v: Double): Float = (size.height * (1 - (v - yMin) / (yMax - yMin))).toFloat()

        for (i in 0..3) {
            val gy = size.height * i / 3f
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        points.forEach { p -> drawCircle(dotColor, radius = 3.dp.toPx(), center = Offset(x(p.epochDay), y(p.kg))) }
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val o = Offset(x(p.epochDay), y(p.trendKg))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
