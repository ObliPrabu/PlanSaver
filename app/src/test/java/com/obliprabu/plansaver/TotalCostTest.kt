package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Test

class TotalCostTest {
    private fun result(carrier: String, name: String, gb: Int?, monthly: Double) =
        Result(Plan(carrier, name, "X", true, gb, mapOf(1 to monthly), "", ""), monthly, 0.0)

    @Test fun tradeInComesOffThePhoneNotThePlan() {
        val c = totalCost(30.0, 24, 800.0, 200.0)
        assertEquals(720.0, c.plan, 0.001)
        assertEquals(600.0, c.phone, 0.001)
        assertEquals(1320.0, c.total, 0.001)
        assertEquals(1390.0, totalCost(30.0, 24, 800.0, 200.0, 70.0).total, 0.001)
    }

    @Test fun tradeInNeverMakesThePhoneNegativeAndNeedsAPhonePrice() {
        assertEquals(0.0, totalCost(30.0, 12, 100.0, 500.0).phone, 0.001)
        assertEquals(360.0, totalCost(30.0, 12, 0.0, 500.0).total, 0.001)
    }

    @Test fun quizPushesDataUpWithoutWifiOrWithHotspot() {
        assertEquals(0, quizDataIndex(0, homeWifi = true, hotspot = false))
        assertEquals(2, quizDataIndex(1, homeWifi = false, hotspot = false))
        assertEquals(3, quizDataIndex(1, homeWifi = false, hotspot = true))
        assertEquals(3, quizDataIndex(2, homeWifi = false, hotspot = true)) // capped at unlimited
    }

    @Test fun topPicksDedupeTheSamePlan() {
        val list = listOf(result("A", "Cheap", null, 20.0), result("B", "Mid", null, 40.0), result("C", "Small", 5, 15.0))
        val picks = topPicks(list)
        assertEquals(listOf("Cheapest", "Cheapest unlimited", "Premium (priciest unlimited)"), picks.map { it.label.substringBefore(" + ") }.let {
            // Cheapest is C, unlimited cheapest is A, priciest unlimited is B
            listOf(it[0], it[1], it[2])
        })
        assertEquals("C", picks[0].result.plan.carrier)
        assertEquals("A", picks[1].result.plan.carrier)
        assertEquals("B", picks[2].result.plan.carrier)
        // One unlimited plan only: cheapest-unlimited and premium collapse into one entry.
        assertEquals(2, topPicks(listOf(result("C", "Small", 5, 15.0), result("A", "Cheap", null, 20.0))).size)
    }
}
