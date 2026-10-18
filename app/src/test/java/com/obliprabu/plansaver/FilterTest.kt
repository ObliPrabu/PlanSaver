package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterTest {
    private fun r(carrier: String, network: String, prepaid: Boolean, gb: Int?, deal: String? = null) =
        Result(Plan(carrier, "p", network, prepaid, gb, mapOf(1 to 30.0), "", "", deal = deal), 30.0, 0.0)

    private val all = listOf(
        r("Visible", "Verizon", true, null, "deal"),
        r("Cricket", "AT&T", true, 10),
        r("T-Mobile", "T-Mobile", false, null),
        r("US Mobile", "Choice of networks", true, null),
    )

    @Test fun noFiltersKeepsEverything() = assertEquals(4, applyFilters(all, ResultFilters()).size)

    @Test fun networksOrTogetherAndOtherFiltersAndTogether() {
        assertEquals(2, applyFilters(all, ResultFilters(networks = setOf("Verizon", "AT&T"))).size)
        assertEquals(1, applyFilters(all, ResultFilters(networks = setOf("Verizon", "AT&T"), unlimitedOnly = true)).size)
    }

    @Test fun typeAndDealFilters() {
        assertEquals(1, applyFilters(all, ResultFilters(type = 2)).size)
        assertEquals(3, applyFilters(all, ResultFilters(type = 1)).size)
        assertEquals("Visible", applyFilters(all, ResultFilters(dealOnly = true)).single().plan.carrier)
    }
}
