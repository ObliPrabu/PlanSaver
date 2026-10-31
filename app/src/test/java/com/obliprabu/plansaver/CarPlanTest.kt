package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarPlanTest {
    private val a = CarPlanEntry(
        vehicle = "2022 Honda Civic", price = "$24,000", payHow = 1, mpg = "35",
        liability = "50", full = "100", insFreq = 0, // monthly
        oilCost = "60", oilMonths = "6", tires = "600", tireYears = "4", fuelYear = "1,500", keepYears = "8",
    )
    private val b = CarPlanEntry(vehicle = "2020 Ford F-150", price = "30000", mpg = "20", full = "600", insFreq = 1, fuelYear = "2500", keepYears = "8")

    @Test fun parsesAmountsWithSymbolsAndRejectsJunk() {
        assertEquals(24000.0, parseAmount("$24,000")!!, 0.001)
        assertEquals(12.5, parseAmount(" 12.5 ")!!, 0.001)
        assertNull(parseAmount("")); assertNull(parseAmount("abc")); assertNull(parseAmount("-5"))
    }

    @Test fun insuranceIsScaledByHowOftenItIsPaid() {
        assertEquals(1200.0, a.fullPerYear()!!, 0.001)       // $100 monthly
        assertEquals(600.0, a.liabilityPerYear()!! , 0.001)  // $50 monthly
        assertEquals(1200.0, b.fullPerYear()!!, 0.001)       // $600 every 6 months
        assertEquals(500.0, insuranceYearly("500", 2)!!, 0.001)
    }

    @Test fun oilIsCostTimesChangesPerYear() {
        assertEquals(120.0, a.oilPerYear()!!, 0.001)         // $60 every 6 months
        assertNull(CarPlanEntry(oilCost = "60", oilMonths = "0").oilPerYear())
        assertNull(CarPlanEntry(oilCost = "60").oilPerYear())
    }

    @Test fun tiresAreOneSetUnlessALifespanIsGiven() {
        assertEquals(1200.0, a.tiresOverKeep()!!, 0.001)     // 8 years / 4 years = 2 sets
        assertEquals(600.0, a.copy(tireYears = "").tiresOverKeep()!!, 0.001)
        assertEquals(1800.0, a.copy(tireYears = "3").tiresOverKeep()!!, 0.001) // 8 / 3 rounds up to 3 sets
        assertNull(CarPlanEntry().tiresOverKeep())
    }

    @Test fun totalsAddOnlyWhatWasEntered() {
        // running per year: 1200 insurance + 120 oil + 1500 fuel + 1200/8 tires = 2970
        assertEquals(2970.0, a.runningPerYear()!!, 0.001)
        // total: 24000 + (1200 + 120 + 1500) * 8 + 1200 tires = 47760
        assertEquals(47760.0, a.totalOverKeep()!!, 0.001)
        assertEquals(5970.0, a.costPerYearOfOwning()!!, 0.001)
        assertNull(CarPlanEntry(price = "1000").totalOverKeep()) // needs the years too
        assertNull(CarPlanEntry().runningPerYear())
    }

    @Test fun comparisonMarksTheBetterSideOnlyWhenBothAreEntered() {
        val rows = compareMyCars(a, b).associateBy { it.label }
        assertEquals(0, rows["Cost of the vehicle"]!!.better)
        assertEquals(0, rows["Miles per gallon"]!!.better)             // higher is better
        assertNull(rows["Full coverage per year"]!!.better)             // 1200 vs 1200: tie
        assertEquals(NOT_ENTERED, rows["Oil changes per year"]!!.right)
        assertNull(rows["Oil changes per year"]!!.better)               // b left it blank
        assertEquals("Loan", rows["Paying with"]!!.left)
        assertEquals("Cash", rows["Paying with"]!!.right)
        assertEquals(NOT_ENTERED, rows["Why you picked it"]!!.left)
    }
}
