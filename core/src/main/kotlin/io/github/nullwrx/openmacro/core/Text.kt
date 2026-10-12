package io.github.nullwrx.openmacro.core

/** Default meal for a time of day, for the standard Breakfast, Lunch, Dinner, Snacks order. */
object MealDefaults {
    val DEFAULT_MEALS = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

    /**
     * Index of the meal that fits [hour] (0 to 23). With the default four meals: breakfast
     * 4:00 to 10:59, lunch 11:00 to 14:59, snacks 15:00 to 16:59, dinner 17:00 to 21:59,
     * snacks otherwise. Other meal lists fall back to proportional slots.
     */
    fun mealForHour(hour: Int, mealCount: Int): Int {
        require(mealCount > 0) { "At least one meal is needed" }
        if (mealCount == 1) return 0
        if (mealCount == 4) {
            return when (hour) {
                in 4..10 -> 0
                in 11..14 -> 1
                in 15..16 -> 3
                in 17..21 -> 2
                else -> 3
            }
        }
        // Spread the waking day (5:00 to 22:59) evenly over the meals.
        val clamped = hour.coerceIn(5, 22)
        return ((clamped - 5) * mealCount / 18).coerceIn(0, mealCount - 1)
    }
}

/** Builds SQLite FTS4 MATCH expressions from what the user typed. */
object FtsQuery {
    /** "Chicken bre" becomes "chicken* bre*"; returns null when nothing searchable remains. */
    fun build(input: String): String? {
        val tokens = input
            .lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.isNotBlank() }
            .take(8)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}

object Barcodes {
    /** Keeps digits only. */
    fun normalize(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Codes that may refer to the same product: UPC-A (12 digits) and its EAN-13 form with a
     * leading zero are the same item.
     */
    fun variants(raw: String): List<String> {
        val code = normalize(raw)
        if (code.isEmpty()) return emptyList()
        val out = linkedSetOf(code)
        if (code.length == 12) out += "0$code"
        if (code.length == 13 && code.startsWith("0")) out += code.substring(1)
        return out.toList()
    }

    /**
     * Expands an 8-digit UPC-E code (number system, six digits, check digit) to its 12-digit
     * UPC-A form, which is how food databases store the product. Returns null for invalid input.
     */
    fun upcEToUpcA(raw: String): String? {
        val code = normalize(raw)
        if (code.length != 8 || code[0] !in "01") return null
        val d = code.substring(1, 7)
        val body = when (d[5]) {
            '0', '1', '2' -> d.substring(0, 2) + d[5] + "0000" + d.substring(2, 5)
            '3' -> d.substring(0, 3) + "00000" + d.substring(3, 5)
            '4' -> d.substring(0, 4) + "00000" + d[4]
            else -> d.substring(0, 5) + "0000" + d[5]
        }
        val upcA = code[0] + body + code[7]
        return if (isValidGtin(upcA)) upcA else null
    }

    /** Checks the GS1 check digit of an EAN-8, UPC-A, EAN-13 or GTIN-14 code. */
    fun isValidGtin(raw: String): Boolean {
        val code = normalize(raw)
        if (code.length !in setOf(8, 12, 13, 14) || code.length != raw.length) return false
        val digits = code.map { it - '0' }
        val body = digits.dropLast(1).reversed()
        val sum = body.withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        val check = (10 - sum % 10) % 10
        return check == digits.last()
    }
}

object Csv {
    fun escape(value: Any?): String {
        val s = value?.toString() ?: ""
        val needsQuotes = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            s.startsWith(" ") || s.endsWith(" ")
        return if (needsQuotes) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun row(vararg cells: Any?): String = cells.joinToString(",") { escape(it) }
}
