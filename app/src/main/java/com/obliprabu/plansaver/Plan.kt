package com.obliprabu.plansaver

/** One carrier plan. Prices are USD/month per line, as researched in Oct 2026. */
data class Plan(
    val carrier: String,
    val name: String,
    val network: String,
    val prepaid: Boolean,
    /** null = unlimited high-speed data. */
    val dataGb: Int?,
    /** Per-line price when buying this many lines; missing counts fall back to the nearest known one. */
    val pricePerLine: Map<Int, Double>,
    val note: String = "",
    val signupUrl: String,
    /** Short perks, e.g. "Hotspot 15GB", "Netflix included". */
    val perks: List<String> = emptyList(),
    /** Current promo text, e.g. "Free iPhone with trade-in". Null when none known. */
    val deal: String? = null,
    /** True when the carrier currently advertises a free or discounted phone for switchers (the "free or discounted phone" checkbox filters on this). */
    val freePhoneOffer: Boolean = false,
    /** Page the live deal came from (empty for bundled data). */
    val dealUrl: String = "",
    /** Date (YYYY-MM-DD) the live deal text was last refreshed; empty for bundled data. */
    val dealDate: String = "",
    /** Multi-line price sentence the robot found on the carrier's pages (unverified; shown, never used to compute prices). */
    val linesNote: String = "",
    val linesDate: String = "",
    /** Hotspot / international / perks / slowdown sentences the robot found for the carrier (unverified), already labeled. */
    val extraNotes: List<String> = emptyList(),
    /** Months paid up front (Mint sells 3/6/12-month blocks, cheaper per month the longer). 1 = month to month. */
    val termMonths: Int = 1,
    /** One-time activation fee per line the robot found for the carrier (unverified); null = not found, 0 = none. */
    val activationFee: Double? = null,
    /** Best-case ("up to $N") trade-in credit the carrier's own text mentions; null = not found. */
    val tradeInMax: Double? = null,
) {
    fun perLine(lines: Int): Double {
        pricePerLine[lines]?.let { return it }
        val known = pricePerLine.keys.sorted()
        val nearest = known.lastOrNull { it <= lines } ?: known.first()
        return pricePerLine.getValue(nearest)
    }

    fun monthly(lines: Int): Double = perLine(lines) * lines
}

data class SearchRequest(
    val lines: Int,
    val dataNeedGb: Int?, // null = unlimited
    val currentMonthly: Double,
    val includePostpaid: Boolean,
    val freePhoneOnly: Boolean = false,
    /** e.g. "iPhone 16 Pro"; only set when freePhoneOnly is on. */
    val phone: String? = null,
    /** Also keep deals that are about a different specific phone (e.g. an iPhone 18 deal when you picked a 16). */
    val includeOtherPhones: Boolean = false,
    /** Data need of each line (null entry = unlimited). When set, every line gets its own cheapest plan of a carrier. */
    val lineDataNeeds: List<Int?>? = null,
)

/** One line's plan in a mixed-plan result. */
data class LineAssignment(val line: Int, val plan: Plan, val price: Double)

/** [liveDeal] = the deal text came from today's server lookup rather than the bundled snapshot. */
data class Result(val plan: Plan, val monthly: Double, val yearlySavings: Double, val liveDeal: Boolean = false,
    /** The deal is for a newer phone than the one picked ("check for better phones as well"). */
    val otherPhone: Boolean = false,
    /** Set when each line got its own plan (customized lines); [plan] is then the carrier's priciest pick. */
    val assignments: List<LineAssignment>? = null)

private val phoneMention = Regex(
    """(?i)\b(iphone\s?duo|iphone\s?air|iphone\s?\d{1,2}e?(?:\s?(?:pro max|pro|plus|mini))?|galaxy\s?(?:s\d{2}(?:\+|\s?ultra\b|\s?plus\b|\s?fe\b|\s?edge\b)?|z\s?(?:flip|fold)\s?\d*)|pixel\s?\d{1,2}(?:\s?pro(?:\s?(?:xl|fold))?)?)"""
)

private fun norm(s: String): String {
    val n = s.lowercase().replace(Regex("[^a-z0-9+]"), "")
    return if (n.startsWith("galaxy")) n.replace("plus", "+") else n // "S25 Plus" and "S25+" are the same phone
}

/**
 * True when a deal's text is about the phone the user picked, or names no specific phone at all (generic offers
 * always apply). A deal that only names other phones (e.g. "iPhone 18 Pro Max") is not about [phone].
 */
fun dealMatchesPhone(text: String, phone: String?): Boolean {
    if (phone == null) return true
    val mentions = phoneMention.findAll(text).map { norm(it.value) }.toList()
    if (mentions.isEmpty()) return true
    val want = norm(phone)
    // Exact match: picking the base "iPhone 16" must not match a deal for the "iPhone 16 Pro", nor "S25" a "S25 FE".
    // Two exceptions: a bare family mention ("iPhone 16", "Pixel 9") covers the whole family, so it matches the Pro
    // you picked; and foldables can't be picked by generation, so "Galaxy Z Flip 7" matches the picker's "Galaxy Z Flip".
    return mentions.any { it == want || (bareFamily.matches(it) && want.startsWith(it)) || foldableFamily(it) == want }
}

private val bareFamily = Regex("^(iphone\\d+|galaxys\\d+|pixel\\d+)$")
private fun foldableFamily(n: String) = if (n.startsWith("galaxyz")) n.trimEnd { it.isDigit() } else n

private val brandAndGen = Regex("^(iphone|galaxys|galaxyz(?:flip|fold)|pixel)(\\d*)")

/** (brand, generation) of a normalized phone name; generation 0 when the name has no number. */
private fun brandGen(n: String): Pair<String, Int>? {
    if (n.startsWith("iphoneduo")) return "iphone" to 99 // newest iPhone, newer than any numbered one
    val m = brandAndGen.find(n) ?: return null
    return m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: 0)
}

/** True when the deal names a phone of the same brand as [phone] and a generation at least as new (a "better" phone). */
fun dealIsBetterPhone(text: String, phone: String?): Boolean {
    if (phone == null) return false
    val want = brandGen(norm(phone)) ?: return false
    return phoneMention.findAll(text).any { m ->
        val got = brandGen(norm(m.value))
        got != null && got.first == want.first && got.second >= want.second
    }
}

/** Official coverage-map pages for the networks a plan runs on (a plan on several networks gets several links). */
fun coverageLinks(network: String): List<Pair<String, String>> = buildList {
    if (network.contains("Verizon")) add("Verizon" to "https://www.verizon.com/coverage-map/")
    if (network.contains("AT&T")) add("AT&T" to "https://www.att.com/maps/wireless-coverage.html")
    if (network.contains("T-Mobile")) add("T-Mobile" to "https://www.t-mobile.com/coverage/coverage-map")
}

/**
 * "Not sure how much data?" quiz: index into the data choices (0 = 1-5 GB, 1 = 10, 2 = 25, 3 = unlimited). [usage] is
 * 0 = texting/reading, 1 = music/social, 2 = video streaming. No home Wi-Fi or sharing a hotspot each push it one step
 * up. A rough guess, not a measurement.
 */
fun quizDataIndex(usage: Int, homeWifi: Boolean, hotspot: Boolean): Int =
    (usage + (if (homeWifi) 0 else 1) + (if (hotspot) 1 else 0)).coerceIn(0, 3)

/** A short label plus the result it points at, for the "Top picks" box. */
data class TopPick(val label: String, val result: Result)

/**
 * Up to three picks from the plans shown: the cheapest, the cheapest unlimited, and the priciest unlimited (postpaid
 * plans usually carry the most perks, but that is not checked). Same plan twice gets one entry with both labels.
 * Mixed-plan (customized lines) results are skipped. The result list is what the user searched, so prices are theirs.
 */
fun topPicks(list: List<Result>): List<TopPick> {
    val plain = list.filter { !it.otherPhone && it.assignments == null }
    val unlimited = plain.filter { it.plan.dataGb == null }
    val wanted = listOfNotNull(
        plain.minByOrNull { it.monthly }?.let { "Cheapest" to it },
        unlimited.minByOrNull { it.monthly }?.let { "Cheapest unlimited" to it },
        unlimited.maxByOrNull { it.monthly }?.let { "Premium (priciest unlimited)" to it },
    )
    return wanted.groupBy { it.second.plan.carrier + it.second.plan.name }.values
        .map { g -> TopPick(g.joinToString(" + ") { it.first }, g.first().second) }
}

/** What the user pays over [months]: the plan, plus the phone paid in full minus any trade-in value (never below 0). */
data class TotalCost(val months: Int, val plan: Double, val phone: Double, val activation: Double, val total: Double)

/** [tradeIn] is the credit taken off the phone; [activation] the one-time fee for all lines. */
fun totalCost(monthly: Double, months: Int, phonePrice: Double, tradeIn: Double, activation: Double = 0.0): TotalCost {
    val phone = if (phonePrice > 0) (phonePrice - tradeIn).coerceAtLeast(0.0) else 0.0
    return TotalCost(months, monthly * months, phone, activation, monthly * months + phone + activation)
}
