package com.obliprabu.plansaver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    private fun mixedSearch(request: SearchRequest, needs: List<Int?>): List<Result> {
        val eligible = PlanData.plans
            .filter { request.includePostpaid || it.prepaid }
            .filter { request.lines >= it.pricePerLine.keys.min() }
        return eligible.groupBy { it.carrier }.mapNotNull { (_, plans) ->
            val picks = needs.take(request.lines).mapIndexed { i, need ->
                val best = plans.filter { it.dataGb == null || (need != null && it.dataGb >= need) }
                    .minByOrNull { it.perLine(request.lines) } ?: return@mapNotNull null
                LineAssignment(i + 1, best, best.perLine(request.lines))
            }
            val total = picks.sumOf { it.price }
            Result(picks.maxBy { it.price }.plan, total, (request.currentMonthly - total) * 12, assignments = picks)
        }.sortedBy { it.monthly }
    }

    override suspend fun search(request: SearchRequest): List<Result> {
        delay(600) // placeholder for the network round trip
        request.lineDataNeeds?.let { return mixedSearch(request, it) }
        return PlanData.plans
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
        val base = bundled.search(request.copy(freePhoneOnly = false))
        val live = if (dealsUrl.isNotEmpty()) fetchDeals(request.phone) else null

        val merged = base.map { r ->
            val carrierDeals = live?.filter { sameCarrier(it.carrier, r.plan.carrier) }.orEmpty()
            val d = carrierDeals.firstOrNull { it.coversPlan(r.plan.name) }
            // The multi-line note is about the carrier, not one deal, so any of its entries may supply it.
            val withLines = carrierDeals.firstOrNull { it.linesNote.isNotEmpty() }
                ?.let { r.copy(plan = r.plan.copy(linesNote = it.linesNote, linesDate = it.linesUpdated)) } ?: r
            val noted = carrierDeals.firstOrNull { it.extras.isNotEmpty() }
                ?.let { withLines.copy(plan = withLines.plan.copy(extraNotes = it.extras)) } ?: withLines
            val withFees = noted.copy(plan = noted.plan.copy(
                activationFee = carrierDeals.firstNotNullOfOrNull { it.activationFee },
                tradeInMax = carrierDeals.firstNotNullOfOrNull { it.tradeInMax }))
            if (d == null) withFees
            else withFees.copy(
                plan = withFees.plan.copy(deal = d.deal, freePhoneOffer = d.freePhone, dealUrl = d.url, dealDate = d.updated),
                liveDeal = true,
            )
        }
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

    /** [plans]: plan-name fragments this deal is for (e.g. "Experience", "6-month"); empty = every plan of the carrier. */
    private class Deal(val carrier: String, val deal: String, val freePhone: Boolean, val url: String,
        val plans: List<String>, val updated: String, val linesNote: String, val linesUpdated: String,
        val extras: List<String>, val activationFee: Double?, val tradeInMax: Double?) {
        fun coversPlan(planName: String) = plans.isEmpty() || plans.any { planName.contains(it, ignoreCase = true) }
    }

    /** "Hotspot: text (as of date)" for each extra the robot found, in file order. */
    private fun extraNotes(o: JSONObject?): List<String> = o?.keys()?.asSequence()?.mapNotNull { k ->
        val n = o.optJSONObject(k) ?: return@mapNotNull null
        val text = n.optString("text")
        if (text.isEmpty()) return@mapNotNull null
        val label = when (k) { "hotspot" -> "Hotspot"; "international" -> "International"; "perks" -> "Perks"; "slowdown" -> "Slowdown"; else -> k }
        val date = n.optString("updated")
        "$label: $text" + if (date.isNotEmpty()) " (as of $date)" else ""
    }?.toList().orEmpty()

    private fun sameCarrier(a: String, b: String): Boolean {
        val x = a.lowercase().replace(" mobile", "").replace("by t-mobile", "").trim()
        val y = b.lowercase().replace(" mobile", "").replace("by t-mobile", "").trim()
        return x == y || x.startsWith(y) || y.startsWith(x)
    }

    /** Deals that apply to [phone]: entries with an empty "phones" list apply to every phone. */
    private suspend fun fetchDeals(phone: String?): List<Deal>? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(dealsUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                useCaches = false // a Search tap should see the latest file
            }
            if (conn.responseCode != 200) return@withContext null
            val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("deals")
            (0 until arr.length()).mapNotNull {
                val o = arr.getJSONObject(it)
                val phones = o.optJSONArray("phones")
                val applies = phones == null || phones.length() == 0 || phone == null ||
                    (0 until phones.length()).any { i -> phones.getString(i).equals(phone, ignoreCase = true) }
                if (!applies) null
                else {
                    val scope = o.optJSONArray("plans")
                    Deal(o.getString("carrier"), o.getString("deal"), o.optBoolean("freePhone"), o.optString("url"),
                        if (scope == null) emptyList() else (0 until scope.length()).map { i -> scope.getString(i) },
                        o.optString("updated"), o.optString("lines"), o.optString("linesUpdated"), extraNotes(o.optJSONObject("extras")),
                        if (o.has("activationFee")) o.getDouble("activationFee") else null,
                        if (o.has("tradeInMax")) o.getDouble("tradeInMax") else null)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
