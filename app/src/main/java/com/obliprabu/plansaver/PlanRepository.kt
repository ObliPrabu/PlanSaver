package com.obliprabu.plansaver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Where plan data comes from. The Search button calls [search] on every tap.
 * Today it reads the bundled research; swap in [RemotePlanRepository] later to
 * hit a server (Cloud Function) that does the live web lookup. Never put a
 * search/AI API key in this app - keep it on the server.
 */
interface PlanRepository {
    suspend fun search(request: SearchRequest): List<Result>
}

class BundledPlanRepository : PlanRepository {
    /**
     * Customized lines: for each carrier, every line gets the cheapest of that carrier's plans that covers the line's own
     * data need. The carrier's total is the sum (per-line prices still depend on the total line count). Carriers that
     * cannot cover every line are left out. Whether a carrier lets you mix plans on one account is not checked.
     */
    private fun mixedSearch(request: SearchRequest, needs: List<Int?>, allow: (Plan) -> Boolean): List<Result> {
        val eligible = PlanData.plans
            .filter(allow)
            .filter { request.includePostpaid || it.prepaid }
            .filter { request.lines >= it.pricePerLine.keys.min() }
        return eligible.groupBy { it.carrier }.mapNotNull { (_, plans) ->
            val picks = needs.take(request.lines).mapIndexed { i, need ->
                val best = plans.filter { it.dataGb == null || (need != null && it.dataGb >= need) }
                    .minByOrNull { it.perLine(request.lines) } ?: return@mapNotNull null
                LineAssignment(i + 1, best, best.perLine(request.lines))
            }
            val total = picks.sumOf { it.price }
            // No lines to price (empty needs) means nothing to show for this carrier, instead of throwing.
            val priciest = picks.maxByOrNull { it.price } ?: return@mapNotNull null
            Result(priciest.plan, total, (request.currentMonthly - total) * 12, assignments = picks)
        }.sortedWith(compareBy({ it.monthly }, { it.plan.carrier })) // equal totals: by carrier name, so the order never changes between searches
    }

    override suspend fun search(request: SearchRequest): List<Result> = searchAllowing(request) { true }

    /** Same search, but only plans that [allow] says yes to are considered (used to keep a free-phone search on the plans its deal covers). */
    suspend fun searchAllowing(request: SearchRequest, allow: (Plan) -> Boolean): List<Result> {
        delay(600) // placeholder for the network round trip
        request.lineDataNeeds?.let { return mixedSearch(request, it, allow) }
        return PlanData.plans
            .filter(allow)
            .filter { request.includePostpaid || it.prepaid }
            .filter { !request.freePhoneOnly || it.freePhoneOffer }
            // A plan only priced for N+ lines (e.g. AT&T Extra at 4 lines) must not show up for fewer lines.
            .filter { request.lines >= it.pricePerLine.keys.min() }
            .filter { it.dataGb == null || (request.dataNeedGb != null && it.dataGb >= request.dataNeedGb) }
            .map {
                val m = it.monthly(request.lines)
                Result(it, m, (request.currentMonthly - m) * 12)
            }
            .sortedBy { it.monthly }
    }
}

/**
 * Bundled plans + deals downloaded from [dealsUrl] (a static deals.json on GitHub, refreshed by hand - free, no
 * server, no API key). Fetched on every Search tap; if the URL is empty, offline or bad, falls back to the
 * bundled snapshot in [PlanData].
 */
class RemotePlanRepository(private val dealsUrl: String) : PlanRepository {
    private val bundled = BundledPlanRepository()

    override suspend fun search(request: SearchRequest): List<Result> {
        val live = if (dealsUrl.isNotEmpty()) fetchDeals(request.phone) else null
        // A free-phone search only considers the plans a carrier's free-phone deal covers (e.g. AT&T's trade-in credit needs
        // Premium or Elite), so with custom lines it picks a qualifying plan instead of the cheapest one that gets no deal.
        val base = bundled.searchAllowing(request.copy(freePhoneOnly = false)) { p ->
            !request.freePhoneOnly || live == null || live.any { sameCarrierName(it.carrier, p.carrier) && it.freePhone && it.coversPlan(p.name) }
        }

        val merged = mergeLiveDeals(base, live)
        // Deals about your phone (or no phone) stay. Deals about another phone are hidden, unless "better phones"
        // is ticked and the phone is a newer one of the same brand - those are kept, labeled and listed last.
        val relevant = merged.map { r ->
            val text = r.plan.deal
            when {
                text == null || dealMatchesPhone(text, request.phone) -> r
                request.includeOtherPhones && dealIsBetterPhone(text, request.phone) -> r.copy(otherPhone = true)
                else -> r.copy(plan = r.plan.copy(deal = null, freePhoneOffer = false, dealUrl = "", dealDate = ""), liveDeal = false)
            }
        }.sortedBy { it.otherPhone } // stable: keeps the price order inside each group
        // With downloaded data a carrier with no offer is dropped; without it, keep the bundled flags.
        return if (!request.freePhoneOnly) relevant
        else relevant.filter { it.plan.freePhoneOffer && (live == null || it.liveDeal) }
    }

    /** Deals that apply to [phone]: entries with an empty "phones" list apply to every phone. */
    private suspend fun fetchDeals(phone: String?): List<LiveDeal>? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(dealsUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                useCaches = false // a Search tap should see the latest file
            }
            if (conn.responseCode != 200) return@withContext null
            parseLiveDeals(conn.inputStream.bufferedReader().readText(), phone, LocalDate.now().toString())
        } catch (e: Exception) {
            null
        }
    }
}

/** True when [fragments] is empty (no restriction) or the plan name contains one of them, ignoring case. */
fun nameMatches(planName: String, fragments: List<String>): Boolean =
    fragments.isEmpty() || fragments.any { planName.contains(it, ignoreCase = true) }

/** [plans]: plan-name fragments this deal is for (e.g. "Experience", "6-month"); empty = every plan of the carrier. */
internal class LiveDeal(val carrier: String, val deal: String, val freePhone: Boolean, val url: String,
    val plans: List<String>, val updated: String, val linesNote: String, val linesUpdated: String,
    val extras: List<String>, val activationFee: Double?, val tradeInMax: Double?, val requiresPlans: List<String> = emptyList()) {
    /**
     * [plans] is the robot-managed scope of the deal text; [requiresPlans] is a hand-set rule the robot never edits (the
     * carrier's own terms, e.g. AT&T's trade-in credit needing Premium or Elite). The deal applies only to plans passing both.
     */
    fun coversPlan(planName: String) = nameMatches(planName, plans) && nameMatches(planName, requiresPlans)
}

internal fun sameCarrierName(a: String, b: String): Boolean {
    val x = a.lowercase().replace(" mobile", "").replace("by t-mobile", "").trim()
    val y = b.lowercase().replace(" mobile", "").replace("by t-mobile", "").trim()
    return x == y || x.startsWith(y) || y.startsWith(x)
}

/** True when a hand-set `expires` date (YYYY-MM-DD) is before [today] (same format, so plain string order is date order). Blank = never expires. */
fun dealExpired(expires: String, today: String): Boolean = expires.isNotBlank() && expires < today

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)
private fun JSONObject.num(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
private fun JSONObject.strList(key: String): List<String> {
    val a = optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).mapNotNull { i -> if (a.isNull(i)) null else a.optString(i) }
}

/** "Hotspot: text (as of date)" for each extra the robot found, sorted by key (the same order on iOS). */
private fun extraNotes(o: JSONObject?): List<String> = o?.keys()?.asSequence()?.sorted()?.mapNotNull { k ->
    val n = o.optJSONObject(k) ?: return@mapNotNull null
    val text = n.str("text")
    if (text.isEmpty()) return@mapNotNull null
    val label = when (k) {
        "hotspot" -> "Hotspot"; "international" -> "International"; "perks" -> "Perks"; "slowdown" -> "Slowdown"
        "activation" -> "Activation"; "tradein" -> "Trade-in"; else -> k
    }
    val date = n.str("updated")
    "$label: $text" + if (date.isNotEmpty()) " (as of $date)" else ""
}?.toList().orEmpty()

/**
 * Reads deals.json text. Tolerant per entry (like iOS): an entry that is malformed, missing carrier/deal, about another
 * phone, or past its hand-set `expires` date is skipped on its own; a bad file gives an exception for the caller to treat as offline.
 * Hand-set keys the robot never edits: `requiresPlans`, `expires`, and `ignoreFreePhone` (treat the entry as not a free-phone
 * offer even though the robot's text check set freePhone, e.g. a plan-price promo).
 */
internal fun parseLiveDeals(json: String, phone: String?, today: String): List<LiveDeal> {
    val arr = JSONObject(json).getJSONArray("deals")
    return (0 until arr.length()).mapNotNull { idx ->
        try {
            val o = arr.getJSONObject(idx)
            if (o.isNull("carrier") || o.isNull("deal")) return@mapNotNull null
            if (dealExpired(o.str("expires"), today)) return@mapNotNull null
            val phones = o.strList("phones")
            val applies = phones.isEmpty() || phone == null || phones.any { it.equals(phone, ignoreCase = true) }
            if (!applies) return@mapNotNull null
            LiveDeal(o.getString("carrier"), o.getString("deal"), o.optBoolean("freePhone") && !o.optBoolean("ignoreFreePhone"),
                o.str("url"), o.strList("plans"), o.str("updated"), o.str("lines"), o.str("linesUpdated"),
                extraNotes(o.optJSONObject("extras")), o.num("activationFee"), o.num("tradeInMax"), o.strList("requiresPlans"))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Lays the live deals over the searched plans. When [live] is null (offline / bad URL) nothing is enforced beyond the bundled
 * data: bundled deal text and flags stay as they are, because there is nothing newer to judge them against. When live deals
 * exist for a plan's carrier but none covers the plan (e.g. AT&T Value 2.0 under requiresPlans), the plan's bundled deal,
 * free-phone flag and link are cleared so a stale "phone deal" is not shown.
 */
internal fun mergeLiveDeals(base: List<Result>, live: List<LiveDeal>?): List<Result> = base.map { r ->
    val carrierDeals = live?.filter { sameCarrierName(it.carrier, r.plan.carrier) }.orEmpty()
    val d = carrierDeals.firstOrNull { it.coversPlan(r.plan.name) }
    // The multi-line note is about the carrier, not one deal, so any of its entries may supply it.
    val withLines = carrierDeals.firstOrNull { it.linesNote.isNotEmpty() }
        ?.let { r.copy(plan = r.plan.copy(linesNote = it.linesNote, linesDate = it.linesUpdated)) } ?: r
    val noted = carrierDeals.firstOrNull { it.extras.isNotEmpty() }
        ?.let { withLines.copy(plan = withLines.plan.copy(extraNotes = it.extras)) } ?: withLines
    // Fees are about the carrier, like the multi-line and extras notes, so they ignore the per-plan `plans` scope.
    val withFees = noted.copy(plan = noted.plan.copy(
        activationFee = carrierDeals.firstNotNullOfOrNull { it.activationFee },
        tradeInMax = carrierDeals.firstNotNullOfOrNull { it.tradeInMax }))
    when {
        d != null -> withFees.copy(
            plan = withFees.plan.copy(deal = d.deal, freePhoneOffer = d.freePhone, dealUrl = d.url, dealDate = d.updated),
            liveDeal = true,
        )
        carrierDeals.isNotEmpty() -> withFees.copy(plan = withFees.plan.copy(deal = null, freePhoneOffer = false, dealUrl = "", dealDate = ""))
        else -> withFees
    }
}
