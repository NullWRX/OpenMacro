package io.github.nullwrx.openmacro.data.off

import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A product as read from Open Food Facts, nutrients per 100 g or ml. */
data class OffProduct(
    val code: String,
    val name: String,
    val brand: String?,
    val baseUnit: BaseUnit,
    val per100: Nutrients,
    val hasNutrition: Boolean,
    val servingLabel: String?,
    val servingQuantity: Double?,
    val nutriScore: String?,
    val novaGroup: Int?,
)

sealed interface OffLookup {
    data class Found(val product: OffProduct) : OffLookup
    data object NotFound : OffLookup
    data class Failed(val error: Throwable) : OffLookup
}

/**
 * Minimal Open Food Facts client. Respects the published limits (15 product reads and
 * 10 searches per minute per user) by spacing searches; product reads happen one per scan.
 * Search is only run when the user asks, never as they type, as Open Food Facts requires.
 */
class OpenFoodFactsClient(private val userAgent: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val searchLock = Mutex()
    private var lastSearchAt = 0L

    suspend fun product(barcode: String): OffLookup = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/api/v2/product/${URLEncoder.encode(barcode, "UTF-8")}.json?fields=$FIELDS"
            val (status, body) = get(url)
            when {
                status == 404 -> OffLookup.NotFound
                status !in 200..299 -> OffLookup.Failed(IOException("Open Food Facts returned HTTP $status"))
                else -> {
                    val root = json.parseToJsonElement(body) as? JsonObject
                    val product = root?.get("product") as? JsonObject
                    val parsed = product?.let { parseProduct(it, barcode) }
                    if (parsed == null) OffLookup.NotFound else OffLookup.Found(parsed)
                }
            }
        } catch (e: Exception) {
            OffLookup.Failed(e)
        }
    }

    suspend fun search(query: String): Result<List<OffProduct>> = withContext(Dispatchers.IO) {
        searchLock.withLock {
            val wait = MIN_SEARCH_GAP_MS - (System.currentTimeMillis() - lastSearchAt)
            if (wait > 0) delay(wait)
            lastSearchAt = System.currentTimeMillis()
        }
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=25&fields=$FIELDS"
            val (status, body) = get(url)
            if (status !in 200..299) throw IOException("Open Food Facts returned HTTP $status")
            val root = json.parseToJsonElement(body) as? JsonObject
            val products = root?.get("products") as? JsonArray ?: JsonArray(emptyList())
            products.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val code = obj.str("code") ?: return@mapNotNull null
                parseProduct(obj, code)?.takeIf { it.hasNutrition }
            }
        }
    }

    private fun get(url: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return status to body
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseProduct(p: JsonObject, fallbackCode: String): OffProduct? {
        val code = p.str("code") ?: fallbackCode
        val name = listOf("product_name", "product_name_en", "generic_name")
            .firstNotNullOfOrNull { p.str(it)?.trim()?.takeIf(String::isNotEmpty) } ?: return null
        val brand = p.str("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val unitHints = listOfNotNull(p.str("product_quantity_unit"), p.str("serving_quantity_unit"))
        val quantity = p.str("quantity")?.lowercase().orEmpty()
        val liquid = unitHints.any { it.equals("ml", true) } ||
            Regex("""\d\s*(ml|cl|l)\b""").containsMatchIn(quantity)
        val baseUnit = if (liquid) BaseUnit.MILLILITER else BaseUnit.GRAM

        val n = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        val servingQty = p.num("serving_quantity")?.takeIf { it > 0 }

        fun per100(key: String): Double? = n.num("${key}_100g")
            ?: n.num("${key}_serving")?.let { v -> servingQty?.let { v * 100.0 / it } }

        val kcal = per100("energy-kcal") ?: per100("energy")?.let { EnergyMath.kjToKcal(it) }
        val sodiumMg = per100("sodium")?.times(1000.0) ?: per100("salt")?.times(400.0)
        val protein = per100("proteins")
        val carbs = per100("carbohydrates")
        val fat = per100("fat")
        val hasNutrition = kcal != null && (protein != null || carbs != null || fat != null)

        val servingLabel = p.str("serving_size")?.trim()?.takeIf { it.isNotEmpty() }
        return OffProduct(
            code = code,
            name = name,
            brand = brand,
            baseUnit = baseUnit,
            per100 = Nutrients(
                energyKcal = kcal ?: 0.0,
                proteinG = protein ?: 0.0,
                carbsG = carbs ?: 0.0,
                fatG = fat ?: 0.0,
                fiberG = per100("fiber"),
                sugarG = per100("sugars"),
                saturatedFatG = per100("saturated-fat"),
                sodiumMg = sodiumMg,
            ),
            hasNutrition = hasNutrition,
            servingLabel = servingLabel,
            servingQuantity = servingQty,
            nutriScore = p.str("nutriscore_grade")?.lowercase()?.takeIf { it in setOf("a", "b", "c", "d", "e") },
            novaGroup = (p["nova_group"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
                ?.takeIf { it in 1..4 },
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.num(key: String): Double? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.doubleOrNull ?: prim.contentOrNull?.replace(',', '.')?.trim()?.toDoubleOrNull()
    }

    companion object {
        const val BASE = "https://world.openfoodfacts.org"
        private const val MIN_SEARCH_GAP_MS = 6_500L
        private const val FIELDS =
            "code,product_name,product_name_en,generic_name,brands,quantity,product_quantity_unit," +
                "serving_size,serving_quantity,serving_quantity_unit,nutriments,nutriscore_grade,nova_group"
    }
}
