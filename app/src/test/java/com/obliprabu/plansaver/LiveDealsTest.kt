package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDealsTest {
    private val json = """{"deals":[
      {"carrier":"T-Mobile","deal":"A","freePhone":true,"requiresPlans":["Experience"],"activationFee":null,"url":null},
      {"carrier":null,"deal":"bad"},
      {"deal":"no carrier"},
      "not an object",
      {"carrier":"Mint Mobile","deal":"Promo","freePhone":true,"expires":"2026-10-22"},
      {"carrier":"Visible","deal":"Plan promo","freePhone":true,"ignoreFreePhone":true,"extras":{"tradein":{"text":"t"},"activation":{"text":"a"}}}
    ]}"""

    @Test fun badEntriesAreSkippedNotFatal() {
        val d = parseLiveDeals(json, null, "2026-10-01")
        assertEquals(listOf("T-Mobile", "Mint Mobile", "Visible"), d.map { it.carrier })
        assertEquals("", d[0].url) // JSON null is blank, never the text "null"
        assertNull(d[0].activationFee)
    }

    @Test fun expiredDealIsSkipped() {
        assertEquals(listOf("T-Mobile", "Visible"), parseLiveDeals(json, null, "2026-10-31").map { it.carrier })
        assertTrue(dealExpired("2026-10-22", "2026-10-23"))
        assertFalse(dealExpired("2026-10-22", "2026-10-22"))
        assertFalse(dealExpired("", "2026-10-31"))
    }

    @Test fun ignoreFreePhoneTurnsTheFlagOff() {
        val v = parseLiveDeals(json, null, "2026-10-01").first { it.carrier == "Visible" }
        assertFalse(v.freePhone)
    }

    @Test fun extrasAreLabeledAndSortedByKey() {
        val v = parseLiveDeals(json, null, "2026-10-01").first { it.carrier == "Visible" }
        assertEquals(listOf("Activation: a", "Trade-in: t"), v.extras)
    }

    private fun plan(carrier: String, name: String) = Result(
        Plan(carrier, name, "x", false, null, mapOf(1 to 10.0), signupUrl = "", deal = "old", freePhoneOffer = true, dealUrl = "u", dealDate = "d"), 10.0, 0.0)

    @Test fun uncoveredPlanLosesItsStaleBundledDeal() {
        val live = listOf(LiveDeal("AT&T", "new", true, "", emptyList(), "", "", "", emptyList(), null, null, listOf("Premium", "Elite")))
        val out = mergeLiveDeals(listOf(plan("AT&T", "Value 2.0"), plan("AT&T", "Premium 2.0"), plan("Cricket", "X")), live)
        assertNull(out[0].plan.deal); assertFalse(out[0].plan.freePhoneOffer); assertEquals("", out[0].plan.dealUrl); assertEquals("", out[0].plan.dealDate)
        assertEquals("new", out[1].plan.deal); assertTrue(out[1].liveDeal)
        assertEquals("old", out[2].plan.deal) // carrier with no live deals keeps its bundled one
    }

    @Test fun offlineEnforcesNothing() {
        assertEquals("old", mergeLiveDeals(listOf(plan("AT&T", "Value 2.0")), null)[0].plan.deal)
    }

    @Test fun emptyNeedsGiveNoMixedResultInsteadOfThrowing() = kotlinx.coroutines.runBlocking {
        val r = BundledPlanRepository().search(SearchRequest(2, null, 0.0, true, lineDataNeeds = emptyList()))
        assertTrue(r.isEmpty())
    }

    @Test fun mixedTiesBreakByCarrierName() = kotlinx.coroutines.runBlocking {
        val r = BundledPlanRepository().search(SearchRequest(1, null, 0.0, true, lineDataNeeds = listOf(null)))
        val sorted = r.sortedWith(compareBy({ it.monthly }, { it.plan.carrier }))
        assertEquals(sorted.map { it.plan.carrier }, r.map { it.plan.carrier })
    }
}
