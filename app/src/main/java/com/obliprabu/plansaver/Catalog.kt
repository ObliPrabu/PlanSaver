package com.obliprabu.plansaver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Phones, cars and car insurers for the Insurance / Phones / Cars tabs. deals-data/catalog.json holds only names; the
 * catalog robot (tools/refresh_catalog.py) fills every number and records the page each came from, so any field can be
 * null ("not found yet"). Nothing in the app invents a figure.
 */
data class CatalogPhone(
    val id: String, val name: String, val brand: String, val url: String,
    val price: Double?, val display: Double?, val batteryMah: Int?, val chip: String?, val storage: String?,
    val updated: String, val sources: Map<String, String>,
)

data class CatalogCar(
    val id: String, val name: String, val make: String, val url: String,
    val price: Double?, val mpgCombined: Int?, val mpgCity: Int?, val mpgHighway: Int?, val mpgUnit: String,
    val fuelType: String?, val bodyType: String?, val annualFuelCost: Int?, val rangeMiles: Int?, val modelYear: Int?,
    val updated: String, val sources: Map<String, String>,
)

data class CatalogInsurer(
    val id: String, val name: String, val url: String,
    val fullMonthly: Double?, val minMonthly: Double?, val updated: String, val sources: Map<String, String>,
)

data class Catalog(val phones: List<CatalogPhone>, val cars: List<CatalogCar>, val insurers: List<CatalogInsurer>) {
    companion object {
        val EMPTY = Catalog(emptyList(), emptyList(), emptyList())

        private fun JSONObject.num(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null
        private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key).ifEmpty { null } else null
        private fun JSONObject.srcs(): Map<String, String> =
            optJSONObject("sources")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()

        fun parse(text: String): Catalog {
            val root = JSONObject(text)
            fun <T> list(key: String, f: (JSONObject) -> T): List<T> =
                root.optJSONArray(key)?.let { a -> (0 until a.length()).map { f(a.getJSONObject(it)) } }.orEmpty()
            return Catalog(
                phones = list("phones") { o ->
                    CatalogPhone(o.getString("id"), o.getString("name"), o.optString("brand"), o.optString("url"),
                        o.num("price"), o.num("display"), o.num("batteryMah")?.toInt(), o.str("chip"), o.str("storage"),
                        o.optString("updated"), o.srcs())
                },
                cars = list("cars") { o ->
                    CatalogCar(o.getString("id"), o.getString("name"), o.optString("make"), o.optString("url"),
                        o.num("price"), o.num("mpgCombined")?.toInt(), o.num("mpgCity")?.toInt(), o.num("mpgHighway")?.toInt(),
                        o.optString("mpgUnit", "MPG"), o.str("fuelType"), o.str("bodyType"), o.num("annualFuelCost")?.toInt(),
                        o.num("rangeMiles")?.toInt(), o.num("modelYear")?.toInt(), o.optString("updated"), o.srcs())
                },
                insurers = list("insurers") { o ->
                    CatalogInsurer(o.getString("id"), o.getString("name"), o.optString("url"),
                        o.num("fullMonthly"), o.num("minMonthly"), o.optString("updated"), o.srcs())
                },
            )
        }
    }
}

/** Downloads the hosted catalog on each open; falls back to the copy bundled in the app when offline or private. */
class CatalogRepository(private val url: String, private val context: Context) {
    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        val live = try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000; useCaches = false
            }
            if (conn.responseCode == 200) Catalog.parse(conn.inputStream.bufferedReader().readText()) else null
        } catch (e: Exception) {
            null
        }
        live ?: try {
            Catalog.parse(context.assets.open("catalog.json").bufferedReader().readText())
        } catch (e: Exception) {
            Catalog.EMPTY
        }
    }
}

/** One row of a side-by-side comparison. [better] is 0 (left), 1 (right) or null when neither is clearly better. */
data class CompareRow(val label: String, val left: String, val right: String, val better: Int?)

const val NOT_FOUND = "not found yet"

private fun <T : Comparable<T>> winner(a: T?, b: T?, lowerIsBetter: Boolean): Int? {
    if (a == null || b == null || a == b) return null
    return if ((a < b) == lowerIsBetter) 0 else 1
}

private fun money(v: Double?): String = v?.let { String.format(Locale.US, "$%,.0f", it) } ?: NOT_FOUND

fun comparePhones(a: CatalogPhone, b: CatalogPhone): List<CompareRow> = listOf(
    CompareRow("Starting price", money(a.price), money(b.price), winner(a.price, b.price, lowerIsBetter = true)),
    CompareRow("Display", a.display?.let { "$it in" } ?: NOT_FOUND, b.display?.let { "$it in" } ?: NOT_FOUND, null),
    CompareRow("Battery", a.batteryMah?.let { "$it mAh" } ?: NOT_FOUND, b.batteryMah?.let { "$it mAh" } ?: NOT_FOUND,
        winner(a.batteryMah, b.batteryMah, lowerIsBetter = false)),
    CompareRow("Chip", a.chip ?: NOT_FOUND, b.chip ?: NOT_FOUND, null),
    CompareRow("Storage", a.storage ?: NOT_FOUND, b.storage ?: NOT_FOUND, null),
)

fun compareCars(a: CatalogCar, b: CatalogCar): List<CompareRow> {
    fun mpg(c: CatalogCar) = c.mpgCombined?.let { "$it ${c.mpgUnit}" } ?: NOT_FOUND
    val sameUnit = a.mpgUnit == b.mpgUnit // MPG and MPGe are not the same thing, so no winner across them
    return listOf(
        CompareRow("Starting price", money(a.price), money(b.price), winner(a.price, b.price, lowerIsBetter = true)),
        CompareRow("Fuel economy (combined)", mpg(a), mpg(b), if (sameUnit) winner(a.mpgCombined, b.mpgCombined, lowerIsBetter = false) else null),
        CompareRow("City / highway", listOfNotNull(a.mpgCity, a.mpgHighway).joinToString(" / ").ifEmpty { NOT_FOUND },
            listOfNotNull(b.mpgCity, b.mpgHighway).joinToString(" / ").ifEmpty { NOT_FOUND }, null),
        CompareRow("Fuel cost per year (EPA estimate)", money(a.annualFuelCost?.toDouble()), money(b.annualFuelCost?.toDouble()),
            winner(a.annualFuelCost, b.annualFuelCost, lowerIsBetter = true)),
        CompareRow("Electric range", a.rangeMiles?.let { "$it mi" } ?: "-", b.rangeMiles?.let { "$it mi" } ?: "-",
            winner(a.rangeMiles, b.rangeMiles, lowerIsBetter = false)),
        CompareRow("Body type", a.bodyType ?: NOT_FOUND, b.bodyType ?: NOT_FOUND, null),
        CompareRow("Fuel", a.fuelType ?: NOT_FOUND, b.fuelType ?: NOT_FOUND, null),
    )
}

/** Monthly average for the chosen coverage, or null when the robot has not found one. */
fun CatalogInsurer.monthly(full: Boolean): Double? = if (full) fullMonthly else minMonthly

/** Insurers with a figure first, cheapest first; those with none follow in catalog order. */
fun sortInsurers(list: List<CatalogInsurer>, full: Boolean): List<CatalogInsurer> =
    list.sortedWith(compareBy<CatalogInsurer> { it.monthly(full) == null }.thenBy { it.monthly(full) ?: 0.0 })

/** Where a number came from, shown next to it: the page's domain, e.g. "nerdwallet.com". */
fun sourceDomain(url: String?): String? =
    url?.let { Regex("^https?://(?:www\\.)?([^/]+)").find(it)?.groupValues?.get(1) }
