package com.obliprabu.plansaver

import java.util.Locale
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
    val sets = if (setYears != null && setYears > 0 && keep != null) maxOf(1.0, ceil(keep / setYears)) else 1.0
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

private fun amount(v: Double?): String = v?.let { String.format(Locale.US, "$%,.0f", it) } ?: NOT_ENTERED

private fun text(s: String): String = s.trim().ifEmpty { NOT_ENTERED }

/** Side-by-side rows for the two vehicles; a row only marks a better side when both numbers were entered and differ. */
fun compareMyCars(a: CarPlanEntry, b: CarPlanEntry): List<CompareRow> {
    val aMpg = parseAmount(a.mpg)
    val bMpg = parseAmount(b.mpg)
    val aKeep = a.keepYearsOrNull()
    val bKeep = b.keepYearsOrNull()
    return listOf(
        CompareRow("Vehicle", text(a.vehicle), text(b.vehicle), null),
        CompareRow("Cost of the vehicle", amount(parseAmount(a.price)), amount(parseAmount(b.price)), winner(parseAmount(a.price), parseAmount(b.price), lowerIsBetter = true)),
        CompareRow("Paying with", PAY_OPTIONS[a.payHow.coerceIn(0, 2)], PAY_OPTIONS[b.payHow.coerceIn(0, 2)], null),
        CompareRow("Miles per gallon", aMpg?.let { String.format(Locale.US, "%.0f MPG", it) } ?: NOT_ENTERED,
            bMpg?.let { String.format(Locale.US, "%.0f MPG", it) } ?: NOT_ENTERED, winner(aMpg, bMpg, lowerIsBetter = false)),
        CompareRow("Liability insurance per year", amount(a.liabilityPerYear()), amount(b.liabilityPerYear()), winner(a.liabilityPerYear(), b.liabilityPerYear(), lowerIsBetter = true)),
        CompareRow("Full coverage per year", amount(a.fullPerYear()), amount(b.fullPerYear()), winner(a.fullPerYear(), b.fullPerYear(), lowerIsBetter = true)),
        CompareRow("Oil changes per year", amount(a.oilPerYear()), amount(b.oilPerYear()), winner(a.oilPerYear(), b.oilPerYear(), lowerIsBetter = true)),
        CompareRow("A set of four tires", amount(parseAmount(a.tires)), amount(parseAmount(b.tires)), winner(parseAmount(a.tires), parseAmount(b.tires), lowerIsBetter = true)),
        CompareRow("Fuel per year", amount(parseAmount(a.fuelYear)), amount(parseAmount(b.fuelYear)), winner(parseAmount(a.fuelYear), parseAmount(b.fuelYear), lowerIsBetter = true)),
        CompareRow("Years you plan to keep it", aKeep?.let { String.format(Locale.US, "%.0f", it) } ?: NOT_ENTERED,
            bKeep?.let { String.format(Locale.US, "%.0f", it) } ?: NOT_ENTERED, null),
        CompareRow("Yearly running cost (full coverage)", amount(a.runningPerYear()), amount(b.runningPerYear()), winner(a.runningPerYear(), b.runningPerYear(), lowerIsBetter = true)),
        CompareRow("Total cost over the years you keep it", amount(a.totalOverKeep()), amount(b.totalOverKeep()), winner(a.totalOverKeep(), b.totalOverKeep(), lowerIsBetter = true)),
        CompareRow("Cost per year of owning it", amount(a.costPerYearOfOwning()), amount(b.costPerYearOfOwning()), winner(a.costPerYearOfOwning(), b.costPerYearOfOwning(), lowerIsBetter = true)),
        CompareRow("Why you picked it", text(a.why), text(b.why), null),
        CompareRow("Safety features", text(a.safety), text(b.safety), null),
    )
}
