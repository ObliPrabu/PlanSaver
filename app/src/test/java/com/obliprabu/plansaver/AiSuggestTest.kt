package com.obliprabu.plansaver

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSuggestTest {
    private fun r(carrier: String, monthly: Double, gb: Int? = null, deal: String? = null, other: Boolean = false) =
        Result(Plan(carrier, "Plan $carrier", "X", true, gb, mapOf(1 to monthly), "", "", deal = deal), monthly, 0.0, otherPhone = other)

    @Test fun sendsTheCheapestPlansFirstAndSkipsOtherPhoneDeals() {
        val json = JSONObject(buildAiRequest(SearchRequest(2, 15, 80.0, true, true, "iPhone 16", true, lineDataNeeds = listOf(5, null)), "iPhone 13",
            listOf(r("B", 40.0), r("A", 25.0, 15, "3 months free"), r("Z", 10.0, other = true)))!!)
        val plans = json.getJSONArray("plans")
        assertEquals(2, plans.length())
        assertEquals("A", plans.getJSONObject(0).getString("carrier"))
        assertEquals("3 months free", plans.getJSONObject(0).getString("deal"))
        assertTrue(plans.getJSONObject(1).isNull("dataGb")) // unlimited
        assertEquals(2, json.getInt("lines")); assertEquals(15, json.getInt("dataNeedGb"))
        assertEquals(80.0, json.getDouble("currentMonthly"), 0.001); assertEquals("iPhone 16", json.getString("phone"))
        // every setting of the search is passed along
        assertTrue(json.getBoolean("includePostpaid")); assertTrue(json.getBoolean("freePhoneOnly")); assertTrue(json.getBoolean("includeOtherPhones"))
        assertEquals("iPhone 13", json.getString("tradeInPhone"))
        val needs = json.getJSONArray("lineDataNeeds")
        assertEquals(5, needs.getInt(0)); assertTrue(needs.isNull(1))
    }

    @Test fun capsThePlanCountAndLeavesOutAnUnenteredBill() {
        val many = (1..30).map { r("C$it", it.toDouble()) }
        val json = JSONObject(buildAiRequest(SearchRequest(1, null, 0.0, false), null, many)!!)
        assertEquals(AI_MAX_PLANS, json.getJSONArray("plans").length())
        assertTrue(json.isNull("dataNeedGb"))
        assertFalse(json.has("currentMonthly")); assertFalse(json.has("phone"))
        assertFalse(json.has("lineDataNeeds")); assertFalse(json.has("tradeInPhone"))
        assertFalse(json.getBoolean("includePostpaid")); assertFalse(json.getBoolean("freePhoneOnly"))
    }

    @Test fun nothingToAskAboutMeansNoRequest() {
        assertNull(buildAiRequest(SearchRequest(1, 5, 0.0, true), null, emptyList()))
        assertNull(buildAiRequest(SearchRequest(1, 5, 0.0, true), null, listOf(r("X", 5.0, other = true))))
    }

    @Test fun readsTheReplyTextAndIgnoresErrors() {
        assertEquals("Look at A first.", parseAiText("""{"text":"  Look at A first. "}"""))
        assertNull(parseAiText("""{"error":"AI unavailable.","status":503}"""))
        assertNull(parseAiText("""{"text":"   "}"""))
        assertNull(parseAiText("not json"))
    }
}
