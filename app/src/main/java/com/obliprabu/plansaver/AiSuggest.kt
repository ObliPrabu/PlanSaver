package com.obliprabu.plansaver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The AI half of Search. After the normal search finds plans, the app sends the search and the cheapest plans it found to a
 * small Cloudflare Worker, which asks Google's Gemini to say which to look at first. The Worker holds the Gemini key; this app
 * never does (no key may ever be in the app). Gemini is only told to choose from the plans the app already found, so it
 * cannot add plans or prices. If the call fails or is slow, nothing is shown and the normal results stand on their own.
 * Mirrored in ios/PlanSaver/AiSuggest.swift.
 */
const val AI_URL = "https://plansaver-ai.plansaver-app.workers.dev"
const val AI_MAX_PLANS = 12

/**
 * The JSON the Worker expects, or null when there is nothing to ask about. It carries the whole search exactly as it was made
 * (lines, data, bill, big-carrier plans, free phone, other phones, each line's data, trade-in) plus the cheapest plans found.
 * Only plans for the picked phone are sent (deals about other phones are left out unless "better phones" was on), and a
 * current bill of 0 means "not entered", so it is left out too.
 */
fun buildAiRequest(req: SearchRequest, tradeInPhone: String?, results: List<Result>): String? {
    val picked = results.filter { !it.otherPhone }.sortedBy { it.monthly }.take(AI_MAX_PLANS)
    if (picked.isEmpty()) return null
    val plans = JSONArray()
    picked.forEach {
        plans.put(JSONObject().put("carrier", it.plan.carrier).put("name", it.plan.name).put("monthly", it.monthly)
            .put("dataGb", it.plan.dataGb ?: JSONObject.NULL).put("deal", it.plan.deal ?: JSONObject.NULL))
    }
    val body = JSONObject().put("lines", req.lines).put("dataNeedGb", req.dataNeedGb ?: JSONObject.NULL).put("plans", plans)
        .put("includePostpaid", req.includePostpaid).put("freePhoneOnly", req.freePhoneOnly).put("includeOtherPhones", req.includeOtherPhones)
    if (req.currentMonthly > 0) body.put("currentMonthly", req.currentMonthly)
    if (!req.phone.isNullOrBlank()) body.put("phone", req.phone)
    req.lineDataNeeds?.let { needs -> body.put("lineDataNeeds", JSONArray().apply { needs.forEach { put(it ?: JSONObject.NULL) } }) }
    if (!tradeInPhone.isNullOrBlank()) body.put("tradeInPhone", tradeInPhone)
    return body.toString()
}

/** The suggestion text from the Worker's `{"text": "..."}` reply, or null for an error reply or anything unreadable. */
fun parseAiText(body: String): String? = try {
    JSONObject(body).optString("text", "").trim().ifEmpty { null }
} catch (e: Exception) {
    null
}

/** Asks the Worker; null on any failure (offline, busy, not set up), so the caller simply shows nothing. */
suspend fun fetchAiSuggestion(requestJson: String, url: String = AI_URL): String? = withContext(Dispatchers.IO) {
    try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(requestJson.toByteArray()) }
        if (conn.responseCode == 200) parseAiText(conn.inputStream.bufferedReader().readText()) else null
    } catch (e: Exception) {
        null
    }
}
