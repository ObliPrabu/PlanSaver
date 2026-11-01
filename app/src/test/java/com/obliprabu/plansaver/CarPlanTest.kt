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

    @Test fun partialComponentsSuppressWinnerAndMarkValues() {
        val rows = compareMyCars(a, b).associateBy { it.label }
        val r = rows["Yearly running cost (full coverage)"]!!
        assertNull(r.better)
        assertEquals(true, r.left.endsWith("(partial)")); assertEquals(true, r.right.endsWith("(partial)"))
        assertNull(rows["Total cost over the years you keep it (full coverage)"]!!.better)
        assertEquals(true, rows["Cost per year of owning it (full coverage)"]!!.left.endsWith("(partial)"))
        // same parts entered on both: winner and plain values as before
        val full = compareMyCars(a, a.copy(fuelYear = "3000")).associateBy { it.label }["Yearly running cost (full coverage)"]!!
        assertEquals(0, full.better); assertEquals(false, full.left.contains("partial"))
        // a car with nothing entered shows "not entered" without "(partial)"
        assertEquals(NOT_ENTERED, compareMyCars(a, CarPlanEntry()).associateBy { it.label }["Total cost over the years you keep it (full coverage)"]!!.right)
    }

    @Test fun tireSetsAreNotOvercountedByFloatingPoint() {
        assertEquals(300.0, CarPlanEntry(tires = "100", tireYears = "0.1", keepYears = "0.3").tiresOverKeep()!!, 0.001) // 0.3/0.1 = 3.0000000000000004
        assertEquals(100.0, CarPlanEntry(tires = "100", tireYears = "10", keepYears = "3").tiresOverKeep()!!, 0.001)
    }

    @Test fun roundingAndDecimalDisplay() {
        val rows = compareMyCars(CarPlanEntry(price = "1000.5", mpg = "24.5", keepYears = "7.25"), CarPlanEntry(price = "1000.4", mpg = "25", keepYears = "8")).associateBy { it.label }
        assertEquals("$1,001", rows["Cost of the vehicle"]!!.left); assertEquals("$1,000", rows["Cost of the vehicle"]!!.right)
        assertEquals("24.5 MPG", rows["Miles per gallon"]!!.left); assertEquals("25 MPG", rows["Miles per gallon"]!!.right)
        assertEquals(1, rows["Miles per gallon"]!!.better)
        assertEquals("7.3", rows["Years you plan to keep it"]!!.left); assertEquals("8", rows["Years you plan to keep it"]!!.right)
    }

    @Test fun cleansNumberInput() {
        assertEquals("12.50", cleanNumberInput("12,50", 9))
        assertEquals("1.23", cleanNumberInput("1.2.3", 9))
        assertEquals("123", cleanNumberInput("\u0661\u0662\u0663 1a2b3", 9).takeLast(3))
        assertEquals("1234567", cleanNumberInput("12345678901", 7))
        assertEquals("", cleanNumberInput("abc", 9))
    }
}
