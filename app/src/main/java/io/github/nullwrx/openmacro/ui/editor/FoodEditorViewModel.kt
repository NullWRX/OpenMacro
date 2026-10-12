package io.github.nullwrx.openmacro.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.core.Portions
import io.github.nullwrx.openmacro.core.label.LabelField
import io.github.nullwrx.openmacro.core.label.ParsedLabel
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.db.NutrientColumns
import io.github.nullwrx.openmacro.data.db.ServingColumn
import io.github.nullwrx.openmacro.ui.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Basis { PER_SERVING, PER_100 }

data class EditorForm(
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val basis: Basis = Basis.PER_SERVING,
    /** GRAM or MILLILITER: the unit of the serving weight and of "per 100". */
    val unit: BaseUnit = BaseUnit.GRAM,
    val servingDesc: String = "",
    val servingWeight: String = "",
    val servingsPerPackage: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val saturatedFat: String = "",
    val sodium: String = "",
)

data class EditorState(
    val form: EditorForm = EditorForm(),
    val existing: FoodEntity? = null,
    val loading: Boolean = true,
    val filledFromLabel: Boolean = false,
    val showErrors: Boolean = false,
) {
    val nameError: Boolean get() = form.name.isBlank()
    val kcalError: Boolean get() = Format.parse(form.kcal)?.let { it < 0 } ?: true
    val weightError: Boolean get() =
        form.servingWeight.isNotBlank() && (Format.parse(form.servingWeight)?.let { it <= 0 } ?: true) ||
            form.basis == Basis.PER_100 && form.servingWeight.isBlank() && form.servingDesc.isNotBlank()
    val valid: Boolean get() = !nameError && !kcalError && !weightError

    /** Calories implied by the macros when they disagree with the stated calories. */
    val suggestedKcal: Int?
        get() {
            val kcal = Format.parse(form.kcal) ?: return null
            val p = Format.parse(form.protein) ?: 0.0
            val c = Format.parse(form.carbs) ?: 0.0
            val f = Format.parse(form.fat) ?: 0.0
            if (p + c + f == 0.0) return null
            val n = Nutrients(kcal, p, c, f)
            return if (EnergyMath.isEnergyConsistent(n)) null else EnergyMath.atwaterKcal(p, c, f).roundToInt()
        }
}

class FoodEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val foodId: Long = handle["foodId"] ?: -1L
    val date: Long = handle["date"] ?: -1L
    val meal: Int = handle["meal"] ?: -1

    private val _state = MutableStateFlow(
        EditorState(
            form = EditorForm(
                barcode = handle.get<String>("barcode").orEmpty(),
                name = handle.get<String>("name").orEmpty(),
                brand = handle.get<String>("brand").orEmpty(),
            ),
        ),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val food = if (foodId >= 0) c.foods.get(foodId) else null
            _state.update { s ->
                if (food != null) s.copy(form = formFrom(food), existing = food, loading = false) else s.copy(loading = false)
            }
        }
        // Results from the label scanner arrive here, even while this screen is in the back stack.
        viewModelScope.launch {
            c.pendingLabelScan.filterNotNull().collect { scan ->
                c.pendingLabelScan.value = null
                _state.update { s ->
                    val form = applyLabel(s.form, scan.label)
                    s.copy(
                        form = if (form.barcode.isBlank() && scan.barcode != null) form.copy(barcode = scan.barcode) else form,
                        filledFromLabel = true,
                    )
                }
            }
        }
    }

    fun edit(transform: (EditorForm) -> EditorForm) = _state.update { it.copy(form = transform(it.form)) }

    fun useSuggestedKcal() {
        val k = _state.value.suggestedKcal ?: return
        edit { it.copy(kcal = k.toString()) }
    }

    fun save(onSaved: (foodId: Long) -> Unit) {
        val s = _state.value
        if (!s.valid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            val id = c.foods.saveCustom(buildEntity(s.form, s.existing))
            onSaved(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        val existing = _state.value.existing ?: return
        viewModelScope.launch {
            c.foods.hide(existing.id)
            onDone()
        }
    }

    private fun buildEntity(f: EditorForm, existing: FoodEntity?): FoodEntity {
        val entered = Nutrients(
            energyKcal = Format.parse(f.kcal) ?: 0.0,
            proteinG = Format.parse(f.protein) ?: 0.0,
            carbsG = Format.parse(f.carbs) ?: 0.0,
            fatG = Format.parse(f.fat) ?: 0.0,
            fiberG = Format.parse(f.fiber),
            sugarG = Format.parse(f.sugar),
            saturatedFatG = Format.parse(f.saturatedFat),
            sodiumMg = Format.parse(f.sodium),
        )
        val weight = Format.parse(f.servingWeight)?.takeIf { it > 0 }
        val perPackage = Format.parse(f.servingsPerPackage)?.takeIf { it > 0 }
        val desc = f.servingDesc.trim()

        val baseUnit: BaseUnit
        val perBasis: Nutrients
        val servings = mutableListOf<ServingColumn>()
        when {
            f.basis == Basis.PER_100 -> {
                baseUnit = f.unit
                perBasis = entered
                if (weight != null) servings += ServingColumn(desc.ifEmpty { "1 serving" }, weight)
                if (weight != null && perPackage != null) servings += ServingColumn(PACKAGE, weight * perPackage)
            }
            weight != null -> {
                baseUnit = f.unit
                perBasis = Portions.perBasisFromServing(entered, weight, f.unit)
                servings += ServingColumn(desc.ifEmpty { "1 serving" }, weight)
                if (perPackage != null) servings += ServingColumn(PACKAGE, weight * perPackage)
            }
            else -> {
                baseUnit = BaseUnit.SERVING
                perBasis = entered
                servings += ServingColumn(desc.ifEmpty { "serving" }, 1.0)
                if (perPackage != null) servings += ServingColumn(PACKAGE, perPackage)
            }
        }
        val base = existing ?: FoodEntity(source = FoodSource.CUSTOM, name = "", baseUnit = baseUnit, nutrients = NutrientColumns())
        return base.copy(
            source = FoodSource.CUSTOM,
            name = f.name.trim(),
            brand = f.brand.trim().ifEmpty { null },
            barcode = f.barcode.filter { it.isDigit() }.ifEmpty { null },
            baseUnit = baseUnit,
            nutrients = NutrientColumns.from(perBasis),
            servings = servings,
            hidden = false,
        )
    }

    private fun formFrom(food: FoodEntity): EditorForm {
        val n = food.nutrients
        val first = food.servings.firstOrNull { it.label != PACKAGE }
        val pkg = food.servings.firstOrNull { it.label == PACKAGE }
        val perServing = food.baseUnit == BaseUnit.SERVING
        return EditorForm(
            name = food.name,
            brand = food.brand.orEmpty(),
            barcode = food.barcode.orEmpty(),
            basis = if (perServing) Basis.PER_SERVING else Basis.PER_100,
            unit = if (food.baseUnit == BaseUnit.MILLILITER) BaseUnit.MILLILITER else BaseUnit.GRAM,
            servingDesc = first?.label?.takeIf { !(perServing && it == "serving") }.orEmpty(),
            servingWeight = if (perServing) "" else first?.baseAmount?.let(Format::amount).orEmpty(),
            servingsPerPackage = pkg?.let { p ->
                val per = if (perServing) 1.0 else first?.baseAmount
                per?.takeIf { it > 0 }?.let { Format.amount(p.baseAmount / it) }
            }.orEmpty(),
            kcal = Format.amount(n.energyKcal),
            protein = Format.amount(n.proteinG),
            carbs = Format.amount(n.carbsG),
            fat = Format.amount(n.fatG),
            fiber = n.fiberG?.let(Format::amount).orEmpty(),
            sugar = n.sugarG?.let(Format::amount).orEmpty(),
            saturatedFat = n.saturatedFatG?.let(Format::amount).orEmpty(),
            sodium = n.sodiumMg?.let(Format::amount).orEmpty(),
        )
    }

    private fun applyLabel(form: EditorForm, label: ParsedLabel): EditorForm {
        fun v(field: LabelField): String? = label[field]?.let(Format::amount)
        val unit = if (label.servingUnit == "ml") BaseUnit.MILLILITER else BaseUnit.GRAM
        val servingText = label.servingText
            ?.takeIf { text -> !text.matches(Regex("""[\d.]+\s*(g|ml)""")) }
        return form.copy(
            basis = if (label.per100) Basis.PER_100 else Basis.PER_SERVING,
            unit = unit,
            servingDesc = servingText ?: form.servingDesc,
            servingWeight = v(LabelField.SERVING_AMOUNT) ?: form.servingWeight,
            servingsPerPackage = v(LabelField.SERVINGS_PER_CONTAINER) ?: form.servingsPerPackage,
            kcal = v(LabelField.ENERGY_KCAL) ?: form.kcal,
            protein = v(LabelField.PROTEIN) ?: form.protein,
            carbs = v(LabelField.CARBS) ?: form.carbs,
            fat = v(LabelField.FAT) ?: form.fat,
            fiber = v(LabelField.FIBER) ?: form.fiber,
            sugar = v(LabelField.SUGARS) ?: form.sugar,
            saturatedFat = v(LabelField.SATURATED_FAT) ?: form.saturatedFat,
            sodium = v(LabelField.SODIUM_MG) ?: form.sodium,
        )
    }

    companion object {
        const val PACKAGE = "1 package"
    }
}
