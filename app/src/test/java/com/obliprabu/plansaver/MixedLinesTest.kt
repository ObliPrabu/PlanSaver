package com.obliprabu.plansaver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MixedLinesTest {
    private fun search(vararg needs: Int?) = runBlocking {
        BundledPlanRepository().search(SearchRequest(needs.size, null, 0.0, includePostpaid = false, lineDataNeeds = needs.toList()))
    }

    @Test fun eachLineGetsItsOwnCheapestPlan() {
        val mint = search(5, null).first { it.plan.carrier == "Mint Mobile" }
        assertEquals(listOf("6GB (12-month)", "Unlimited (12-month)"), mint.assignments!!.map { it.plan.name })
        assertEquals(45.0, mint.monthly, 0.001) // 15 + 30, 1-line prices as the fallback
    }

    @Test fun carrierThatCannotCoverALineIsLeftOut() {
        // Consumer Cellular's only capped plan is 1 GB, but its unlimited plan covers a 25 GB line; Cricket likewise.
        assertNotNull(search(25, 25).firstOrNull { it.plan.carrier == "Cricket" })
        assertNull(search(5, 5).firstOrNull { it.plan.carrier == "AT&T" }) // AT&T is priced only for 4 lines
    }

    @Test fun normalSearchHasNoAssignments() {
        val r = runBlocking { BundledPlanRepository().search(SearchRequest(1, null, 0.0, true)) }
        assertNull(r.first().assignments)
    }
}
