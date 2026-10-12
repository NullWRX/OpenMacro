package io.github.nullwrx.openmacro.core.label

import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.Nutrients

/** Values a nutrition label can carry. Milligram fields are stored in mg, the rest in g or kcal. */
enum class LabelField(val label: String, val unit: String) {
    ENERGY_KCAL("Calories", "kcal"),
    FAT("Fat", "g"),
    SATURATED_FAT("Saturated fat", "g"),
    TRANS_FAT("Trans fat", "g"),
    CHOLESTEROL_MG("Cholesterol", "mg"),
    SODIUM_MG("Sodium", "mg"),
    CARBS("Carbohydrate", "g"),
    FIBER("Fiber", "g"),
    SUGARS("Sugars", "g"),
    ADDED_SUGARS("Added sugars", "g"),
    PROTEIN("Protein", "g"),
    SERVING_AMOUNT("Serving size", ""),
    SERVINGS_PER_CONTAINER("Servings per container", ""),
    ;

    val isNutrient: Boolean get() = this != SERVING_AMOUNT && this != SERVINGS_PER_CONTAINER
}

/**
 * What was read from one label.
 *
 * When [per100] is true the nutrient values are per 100 g (or ml), as on EU labels; otherwise
 * they are per serving, and [LabelField.SERVING_AMOUNT] holds the serving weight in [servingUnit].
 */
data class ParsedLabel(
    val values: Map<LabelField, Double> = emptyMap(),
    val servingUnit: String? = null,
    val servingText: String? = null,
    val per100: Boolean = false,
) {
    operator fun get(field: LabelField): Double? = values[field]

    val nutrientCount: Int get() = values.keys.count { it.isNutrient }

    fun toNutrients(): Nutrients = Nutrients(
        energyKcal = values[LabelField.ENERGY_KCAL] ?: 0.0,
        proteinG = values[LabelField.PROTEIN] ?: 0.0,
        carbsG = values[LabelField.CARBS] ?: 0.0,
        fatG = values[LabelField.FAT] ?: 0.0,
        fiberG = values[LabelField.FIBER],
        sugarG = values[LabelField.SUGARS],
        saturatedFatG = values[LabelField.SATURATED_FAT],
        sodiumMg = values[LabelField.SODIUM_MG],
    )
}

/**
 * Turns OCR text from a nutrition label into values. Handles US Nutrition Facts panels and
 * EU-style per-100 g tables (English, with some German and French keywords), common OCR slips
 * such as "1og" for "10g", decimal commas, kJ-only energy and salt instead of sodium.
 *
 * The first value found for each field wins, which matches the top-down order of labels and
 * picks the per-100 g column on two-column EU tables.
 */
object LabelParser {
    private const val NUM = """(\d++(?:\.\d++)?)"""

    /** A number with an optional unit that is not a percentage. Group 1: "<", 2: number, 3: unit. */
    private val VALUE =
        Regex("""(?<![\d.])(<\s*)?$NUM\s*+(mcg|mg|g|kcal|kj|cal|ml)?(?![\d%a-z])(?!\s*%)""")
    private val KCAL = Regex("""(?<![\d.])$NUM\s*+k?cal\b""")
    private val KJ = Regex("""(?<![\d.])$NUM\s*+kj\b""")

    private val ENERGY_KEYWORD = Regex("""calor|energ|brennwert|valeur\s+énerg""")
    private val ENERGY_SKIP = Regex("""daily\s+value|a\s+day|diet|per\s+gram""")
    private val SERVING_KEYWORD = Regex("""serving\s*size|serv\.?\s*size|portion\s*size|per\s+serving|per\s+portion|pro\s+portion|par\s+portion""")
    private val SERVING_SKIP = Regex("""amount\s+per|servings\s+per|calor""")
    private val SERVING_WEIGHT_PAREN = Regex("""\(\s*$NUM\s*(g|ml)\s*\)""")
    private val SERVING_WEIGHT = Regex("""(?<![\d.])$NUM\s*(g|ml)\b""")
    private val PER_100 = Regex("""(per|pro|pour|je)\s*100\s*(g|ml)|^100\s*(g|ml)$|/\s*100\s*(g|ml)""")
    private val SERVINGS_PER_1 =
        Regex("""(?:about\s+)?$NUM\s*servings?\s+per\s+(?:container|package|pack|bag|box)""")
    private val SERVINGS_PER_2 =
        Regex("""servings?\s+per\s+(?:container|package|pack|bag|box)\s*:?\s*(?:about\s+)?$NUM""")
    private val SALT = Regex("""\bsalt\b|\bsalz\b|\bsel\b""")

    private data class Rule(val field: LabelField, val keyword: Regex, val exclude: Regex? = null)

    // Order matters: more specific rules first ("saturated fat" before "fat").
    private val RULES = listOf(
        Rule(LabelField.SATURATED_FAT, Regex("""saturat|sat\.?\s*fat|gesättigt|saturés"""), Regex("""unsatur|mono|poly""")),
        Rule(LabelField.TRANS_FAT, Regex("""\btrans\b""")),
        Rule(LabelField.ADDED_SUGARS, Regex("""added\s+sugar""")),
        Rule(LabelField.SUGARS, Regex("""sugar|zucker|sucres"""), Regex("""alcohol""")),
        Rule(LabelField.FIBER, Regex("""fib(?:er|re)|ballaststoff""")),
        Rule(LabelField.CARBS, Regex("""carbohydrate|\bcarbs?\b|total\s+carb|kohlenhydrat|glucides""")),
        Rule(LabelField.PROTEIN, Regex("""protein|eiwei(?:ß|ss)|protéines""")),
        Rule(LabelField.CHOLESTEROL_MG, Regex("""cholest""")),
        Rule(LabelField.SODIUM_MG, Regex("""sodium|natrium""")),
        Rule(
            LabelField.FAT,
            Regex("""\bfat\b|\bfett\b|matières\s+grasses|lipid"""),
            Regex("""calories\s+from\s+fat|fat\s+cal|unsatur|mono|poly|saturat|trans"""),
        ),
    )

    fun parse(text: String): ParsedLabel {
        val lines = text.lines().map(::normalize).filter { it.isNotBlank() }
        val values = LinkedHashMap<LabelField, Double>()
        var servingText: String? = null
        var servingUnit: String? = null
        var per100 = false

        for ((index, line) in lines.withIndex()) {
            val next = lines.getOrNull(index + 1)
            val isServingLine = SERVING_KEYWORD.containsMatchIn(line)

            if (PER_100.containsMatchIn(line)) per100 = true

            // Servings per container.
            if (LabelField.SERVINGS_PER_CONTAINER !in values) {
                val m = SERVINGS_PER_1.find(line) ?: SERVINGS_PER_2.find(line)
                m?.groupValues?.lastOrNull { it.isNotEmpty() }?.toDoubleOrNull()?.let {
                    values[LabelField.SERVINGS_PER_CONTAINER] = it
                }
            }

            // Serving size.
            if (isServingLine && servingText == null && !SERVING_SKIP.containsMatchIn(line)) {
                val keyword = SERVING_KEYWORD.find(line)!!
                var rest = line.substring(keyword.range.last + 1).trim(' ', ':', '-')
                if (rest.isEmpty() && next != null && !isKeywordLine(next)) rest = next.trim()
                if (rest.startsWith("(") && rest.endsWith(")")) rest = rest.substring(1, rest.length - 1).trim()
                if (rest.isNotEmpty()) {
                    servingText = rest
                    val weight = SERVING_WEIGHT_PAREN.find(rest) ?: SERVING_WEIGHT.find(rest)
                    if (weight != null) {
                        values[LabelField.SERVING_AMOUNT] = weight.groupValues[1].toDouble()
                        servingUnit = weight.groupValues[2]
                    }
                }
            }

            // Energy.
            if (LabelField.ENERGY_KCAL !in values && !ENERGY_SKIP.containsMatchIn(line)) {
                // Old US labels put "Calories from Fat 70" on the calories line; ignore that part.
                val energyLine = line.substringBefore("calories from fat").substringBefore("fat cal")
                energyFrom(energyLine, next)?.let { values[LabelField.ENERGY_KCAL] = it }
            }

            // Nutrients.
            val rule = RULES.firstOrNull { r ->
                r.keyword.containsMatchIn(line) && (r.exclude == null || !r.exclude.containsMatchIn(line))
            }
            if (rule != null && rule.field !in values) {
                val start = rule.keyword.find(line)!!.range.last + 1
                // Usually the value follows the name; "Includes 10g Added Sugars" puts it first.
                val found = valueAfter(line, start)
                    ?: next?.takeIf { !isKeywordLine(it) }?.let { valueAfter(it, 0, leading = true) }
                    ?: valueAfter(line, 0)
                found?.let { (v, unit) -> convert(rule.field, v, unit)?.let { values[rule.field] = it } }
            } else if (rule == null && LabelField.SODIUM_MG !in values && SALT.containsMatchIn(line)) {
                val start = SALT.find(line)!!.range.last + 1
                val found = valueAfter(line, start) ?: next?.takeIf { !isKeywordLine(it) }?.let { valueAfter(it, 0, leading = true) }
                found?.let { (v, unit) ->
                    val saltG = when (unit) { "mg" -> v / 1000.0; null, "g" -> v; else -> null }
                    if (saltG != null) values[LabelField.SODIUM_MG] = saltG * 400.0
                }
            }
        }
        return ParsedLabel(values, servingUnit, servingText, per100)
    }

    private fun energyFrom(line: String, next: String?): Double? {
        KCAL.find(line)?.let { return it.groupValues[1].toDouble() }
        val keyword = ENERGY_KEYWORD.find(line) ?: return null
        val after = valueAfter(line, keyword.range.last + 1)
        if (after != null) {
            val (v, unit) = after
            return when (unit) {
                null, "kcal", "cal" -> v
                "kj" -> next?.let { KCAL.find(it) }?.groupValues?.get(1)?.toDouble()
                    ?: EnergyMath.kjToKcal(v)
                else -> null
            }
        }
        if (next == null) return null
        KCAL.find(next)?.let { return it.groupValues[1].toDouble() }
        val lead = valueAfter(next, 0, leading = true) ?: return null
        return when (lead.second) {
            null, "kcal", "cal" -> lead.first
            "kj" -> EnergyMath.kjToKcal(lead.first)
            else -> null
        }
    }

    private fun isKeywordLine(line: String): Boolean =
        RULES.any { it.keyword.containsMatchIn(line) } || ENERGY_KEYWORD.containsMatchIn(line) ||
            SERVING_KEYWORD.containsMatchIn(line) || SALT.containsMatchIn(line)

    /** First value at or after [start]; with [leading], only a value that starts the line. */
    private fun valueAfter(line: String, start: Int, leading: Boolean = false): Pair<Double, String?>? {
        val m = VALUE.find(line, start.coerceAtMost(line.length)) ?: return null
        if (leading && line.substring(0, m.range.first).isNotBlank()) return null
        val number = m.groupValues[2].toDoubleOrNull() ?: return null
        val lessThan = m.groupValues[1].isNotEmpty()
        val unit = m.groupValues[3].ifEmpty { null }
        return (if (lessThan) number / 2.0 else number) to unit
    }

    private fun convert(field: LabelField, value: Double, unit: String?): Double? {
        val milligramField = field == LabelField.SODIUM_MG || field == LabelField.CHOLESTEROL_MG
        return when (unit) {
            "kcal", "kj", "cal", "ml" -> null
            "mcg" -> if (milligramField) value / 1000.0 else value / 1_000_000.0
            "mg" -> if (milligramField) value else value / 1000.0
            "g" -> if (milligramField) value * 1000.0 else value
            else -> value
        }
    }

    internal fun normalize(raw: String): String {
        var s = raw.lowercase()
            .replace(' ', ' ')
            .replace("µg", "mcg")
            .replace("μg", "mcg")
        // OCR often reads 0 as o: "1og" -> "10g", "og" -> "0g". Leave "oz" alone.
        s = s.replace(Regex("""(?<=\d)o(?!z)"""), "0")
        s = s.replace(Regex("""(?<![a-z])o(?=\s?(?:g|mg|mcg|kcal)\b)"""), "0")
        // "2,000" -> "2000", then decimal commas "8,5" -> "8.5".
        s = s.replace(Regex("""(\d),(\d{3})(?!\d)"""), "$1$2")
        s = s.replace(Regex("""(\d),(\d{1,2})(?!\d)"""), "$1.$2")
        return s.replace(Regex("""\s+"""), " ").trim()
    }
}

/**
 * Combines many noisy readings of the same label, one per camera frame, by voting: each field
 * takes the value seen most often (ties go to the earliest reading).
 */
class LabelAccumulator {
    private val votes = LinkedHashMap<LabelField, LinkedHashMap<Double, Int>>()
    private val servingTextVotes = LinkedHashMap<String, Int>()
    private val unitVotes = LinkedHashMap<String, Int>()
    private var per100Votes = 0
    var readings: Int = 0
        private set

    fun add(label: ParsedLabel) {
        if (label.values.isEmpty()) return
        readings++
        for ((field, value) in label.values) {
            val key = Math.round(value * 10.0) / 10.0
            val counts = votes.getOrPut(field) { LinkedHashMap() }
            counts[key] = (counts[key] ?: 0) + 1
        }
        label.servingText?.let { servingTextVotes[it] = (servingTextVotes[it] ?: 0) + 1 }
        label.servingUnit?.let { unitVotes[it] = (unitVotes[it] ?: 0) + 1 }
        if (label.per100) per100Votes++
    }

    fun best(): ParsedLabel = ParsedLabel(
        values = votes.mapValues { (_, counts) -> counts.entries.maxBy { it.value }.key },
        servingUnit = unitVotes.entries.maxByOrNull { it.value }?.key,
        servingText = servingTextVotes.entries.maxByOrNull { it.value }?.key,
        per100 = per100Votes > 0,
    )

    fun reset() {
        votes.clear()
        servingTextVotes.clear()
        unitVotes.clear()
        per100Votes = 0
        readings = 0
    }
}
