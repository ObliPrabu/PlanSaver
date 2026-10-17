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
    /** True when the carrier is currently advertising a free/heavily discounted phone for switchers. */
    val freePhoneOffer: Boolean = false,
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
)

data class Result(val plan: Plan, val monthly: Double, val yearlySavings: Double)
