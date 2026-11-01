package com.obliprabu.plansaver

import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

/**
 * The "My cars" questionnaire on the Cars tab: the owner types their own numbers for two vehicles and the app compares
 * them side by side. Nothing here is looked up or guessed - every figure is exactly what was entered, so there is no
 * source to cite. Mirrored 1:1 in ios/PlanSaver/CarPlan.swift; keep the two in sync (tests: CarPlanTest.kt and
 * ios/LogicTests/main.swift).
 *
 * Every field is kept as text so a half-filled form never loses what was typed; the functions below turn the text into
 * numbers and return null when a figure was left blank or is not a number.
 */
data class CarPlanEntry(
    val vehicle: String = "",      // make, model and year
    val price: String = "",        // cost of the vehicle
    val payHow: Int = 0,           // index into PAY_OPTIONS
    val mpg: String = "",
    val liability: String = "",    // liability insurance, paid as insFreq says
    val full: String = "",         // full coverage, paid as insFreq says
    val insFreq: Int = 0,          // index into INSURANCE_FREQUENCIES
    val oilCost: String = "",      // one oil change
    val oilMonths: String = "",    // months between oil changes
    val tires: String = "",        // a set of four
    val tireYears: String = "",    // optional: years one set lasts
    val fuelYear: String = "",     // fuel cost for a year
    val keepYears: String = "",    // estimated life of the car / how long it will be kept
    val why: String = "",
    val safety: String = "",
)

val PAY_OPTIONS = listOf("Cash", "Loan", "Lease")
val INSURANCE_FREQUENCIES = listOf("Monthly", "Every 6 months", "Yearly")
private val PERIODS_PER_YEAR = listOf(12.0, 2.0, 1.0)

const val NOT_ENTERED = "not entered"

/** "$1,200" or "1200.50" -> number; blank, negative or non-numeric text -> null. */
fun parseAmount(text: String): Double? = text.replace("$", "").replace(",", "").trim().toDoubleOrNull()?.takeIf { it >= 0 }

/** What an insurance payment adds up to over a year, given how often it is paid. */
fun insuranceYearly(amount: String, freq: Int): Double? = parseAmount(amount)?.let { it * PERIODS_PER_YEAR.getOrElse(freq) { 12.0 } }

fun CarPlanEntry.liabilityPerYear(): Double? = insuranceYearly(liability, insFreq)
fun CarPlanEntry.fullPerYear(): Double? = insuranceYearly(full, insFreq)

/** Oil changes per year: the cost of one change times 12 / months between changes. */
fun CarPlanEntry.oilPerYear(): Double? {
    val cost = parseAmount(oilCost) ?: return null
    val months = parseAmount(oilMonths) ?: return null
    return if (months > 0) cost * 12 / months else null
}

fun CarPlanEntry.keepYearsOrNull(): Double? = parseAmount(keepYears)?.takeIf { it > 0 }

/**
 * Tires over the years the car is kept. With the optional "years a set lasts" and the years kept, that is as many sets as
 * it takes (rounded up, at least one); otherwise one set, because the app does not guess how long tires last.
 */
fun CarPlanEntry.tiresOverKeep(): Double? {
    val cost = parseAmount(tires) ?: return null
    val setYears = parseAmount(tireYears)
    val keep = keepYearsOrNull()
    val sets = if (setYears != null && setYears > 0 && keep != null) maxOf(1.0, ceil(keep / setYears - 1e-9)) else 1.0
    return cost * sets
}

/** Yearly running cost with full coverage: the insurance, oil, tires (spread over the years kept) and fuel that were entered. */
fun CarPlanEntry.runningPerYear(): Double? {
    val keep = keepYearsOrNull()
    val parts = listOfNotNull(fullPerYear(), oilPerYear(), parseAmount(fuelYear), tiresOverKeep()?.let { t -> keep?.let { t / it } })
    return if (parts.isEmpty()) null else parts.sum()
}

/** Price plus everything the car costs to run for the years it is kept; needs the price and the years. */
fun CarPlanEntry.totalOverKeep(): Double? {
    val keep = keepYearsOrNull() ?: return null
    val price = parseAmount(price) ?: return null
    val yearly = listOfNotNull(fullPerYear(), oilPerYear(), parseAmount(fuelYear)).sum()
    return price + yearly * keep + (tiresOverKeep() ?: 0.0)
}

fun CarPlanEntry.costPerYearOfOwning(): Double? = totalOverKeep()?.let { total -> keepYearsOrNull()?.let { total / it } }

/** Whole dollars, grouped with commas, rounded half up, always US formatting (24.5 -> 25), whatever the device language. */
private fun amount(v: Double?): String = v?.let {
    String.format(Locale.US, "$%,d", BigDecimal(it.toString()).setScale(0, RoundingMode.HALF_UP).toLong())
} ?: NOT_ENTERED

/** MPG and years: whole numbers stay whole ("25"), a value with a fractional part shows one decimal, half up ("24.5"). */
fun plainNumber(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "%d", v.toLong())
    else BigDecimal(v.toString()).setScale(1, RoundingMode.HALF_UP).toPlainString()

/**
 * Cleans what was typed into a number field: ',' becomes '.', only ASCII digits 0-9 and at most one '.' are kept, and the
 * result is cut to [maxLen] characters.
 */
fun cleanNumberInput(text: String, maxLen: Int): String {
    val sb = StringBuilder()
    var dot = false
    for (c in text.replace(',', '.')) {
        if (c in '0'..'9') sb.append(c)
        else if (c == '.' && !dot) { dot = true; sb.append(c) }
    }
    return sb.toString().take(maxLen)
}

/** Which cost parts a car has entered: insurance, oil, fuel, tires. Aggregate rows only compare cars with the same parts. */
private fun CarPlanEntry.components(): List<Boolean> =
    listOf(fullPerYear() != null, oilPerYear() != null, parseAmount(fuelYear) != null, tiresOverKeep() != null)

private fun text(s: String): String = s.trim().ifEmpty { NOT_ENTERED }

/** Side-by-side rows for the two vehicles; a row only marks a better side when both numbers were entered and differ. */
fun compareMyCars(a: CarPlanEntry, b: CarPlanEntry): List<CompareRow> {
    val aMpg = parseAmount(a.mpg)
    val bMpg = parseAmount(b.mpg)
    val aKeep = a.keepYearsOrNull()
    val bKeep = b.keepYearsOrNull()
    val samePartsEntered = a.components() == b.components()
    fun agg(label: String, av: Double?, bv: Double?): CompareRow {
        fun show(v: Double?) = amount(v).let { if (!samePartsEntered && v != null) "$it (partial)" else it }
        return CompareRow(label, show(av), show(bv), if (samePartsEntered) winner(av, bv, lowerIsBetter = true) else null)
    }
    return listOf(
        CompareRow("Vehicle", text(a.vehicle), text(b.vehicle), null),
        CompareRow("Cost of the vehicle", amount(parseAmount(a.price)), amount(parseAmount(b.price)), winner(parseAmount(a.price), parseAmount(b.price), lowerIsBetter = true)),
        CompareRow("Paying with", PAY_OPTIONS[a.payHow.coerceIn(0, 2)], PAY_OPTIONS[b.payHow.coerceIn(0, 2)], null),
        CompareRow("Miles per gallon", aMpg?.let { plainNumber(it) + " MPG" } ?: NOT_ENTERED,
            bMpg?.let { plainNumber(it) + " MPG" } ?: NOT_ENTERED, winner(aMpg, bMpg, lowerIsBetter = false)),
        CompareRow("Liability insurance per year", amount(a.liabilityPerYear()), amount(b.liabilityPerYear()), winner(a.liabilityPerYear(), b.liabilityPerYear(), lowerIsBetter = true)),
        CompareRow("Full coverage per year", amount(a.fullPerYear()), amount(b.fullPerYear()), winner(a.fullPerYear(), b.fullPerYear(), lowerIsBetter = true)),
        CompareRow("Oil changes per year", amount(a.oilPerYear()), amount(b.oilPerYear()), winner(a.oilPerYear(), b.oilPerYear(), lowerIsBetter = true)),
        CompareRow("A set of four tires", amount(parseAmount(a.tires)), amount(parseAmount(b.tires)), winner(parseAmount(a.tires), parseAmount(b.tires), lowerIsBetter = true)),
        CompareRow("Fuel per year", amount(parseAmount(a.fuelYear)), amount(parseAmount(b.fuelYear)), winner(parseAmount(a.fuelYear), parseAmount(b.fuelYear), lowerIsBetter = true)),
        CompareRow("Years you plan to keep it", aKeep?.let { plainNumber(it) } ?: NOT_ENTERED,
            bKeep?.let { plainNumber(it) } ?: NOT_ENTERED, null),
        agg("Yearly running cost (full coverage)", a.runningPerYear(), b.runningPerYear()),
        agg("Total cost over the years you keep it (full coverage)", a.totalOverKeep(), b.totalOverKeep()),
        agg("Cost per year of owning it (full coverage)", a.costPerYearOfOwning(), b.costPerYearOfOwning()),
        CompareRow("Why you picked it", text(a.why), text(b.why), null),
        CompareRow("Safety features", text(a.safety), text(b.safety), null),
    )
}
