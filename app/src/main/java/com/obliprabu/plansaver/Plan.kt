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
    /** Months paid up front (Mint sells 3/6/12-month blocks, cheaper per month the longer). 1 = month to month. */
    val termMonths: Int = 1,
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
)

/** [liveDeal] = the deal text came from today's server lookup rather than the bundled snapshot. */
data class Result(val plan: Plan, val monthly: Double, val yearlySavings: Double, val liveDeal: Boolean = false,
    /** The deal is for a newer phone than the one picked ("check for better phones as well"). */
    val otherPhone: Boolean = false)

private val phoneMention = Regex(
    """(?i)\b(iphone\s?\d{1,2}(?:\s?(?:pro max|pro|plus|mini))?|galaxy\s?(?:s\d{2}(?:\+|\s?ultra|\s?plus)?|z\s?(?:flip|fold)\s?\d*)|pixel\s?\d{1,2}(?:\s?pro(?:\s?xl)?)?)"""
)

private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9+]"), "")

/**
 * True when a deal's text is about the phone the user picked, or names no specific phone at all (generic offers
 * always apply). A deal that only names other phones (e.g. "iPhone 18 Pro Max") is not about [phone].
 */
fun dealMatchesPhone(text: String, phone: String?): Boolean {
    if (phone == null) return true
    val mentions = phoneMention.findAll(text).map { norm(it.value) }.toList()
    if (mentions.isEmpty()) return true
    val want = norm(phone)
    return mentions.any { it.startsWith(want) || want.startsWith(it) }
}

private val brandAndGen = Regex("^(iphone|galaxys|galaxyz(?:flip|fold)|pixel)(\\d*)")

/** (brand, generation) of a normalized phone name; generation 0 when the name has no number. */
private fun brandGen(n: String): Pair<String, Int>? {
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
