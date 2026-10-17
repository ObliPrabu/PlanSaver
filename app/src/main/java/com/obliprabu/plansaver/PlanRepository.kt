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
    override suspend fun search(request: SearchRequest): List<Result> {
        delay(600) // placeholder for the network round trip
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
            val d = live?.firstOrNull { sameCarrier(it.carrier, r.plan.carrier) }
            if (d == null) r
            else r.copy(
                plan = r.plan.copy(deal = d.deal, freePhoneOffer = d.freePhone, dealUrl = d.url),
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
                else -> r.copy(plan = r.plan.copy(deal = null, freePhoneOffer = false, dealUrl = ""), liveDeal = false)
            }
        }.sortedBy { it.otherPhone } // stable: keeps the price order inside each group
        // With downloaded data a carrier with no offer is dropped; without it, keep the bundled flags.
        return if (!request.freePhoneOnly) relevant
        else relevant.filter { it.plan.freePhoneOffer && (live == null || it.liveDeal) }
    }

    private class Deal(val carrier: String, val deal: String, val freePhone: Boolean, val url: String)

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
                else Deal(o.getString("carrier"), o.getString("deal"), o.optBoolean("freePhone"), o.optString("url"))
            }
        } catch (e: Exception) {
            null
        }
    }
}
