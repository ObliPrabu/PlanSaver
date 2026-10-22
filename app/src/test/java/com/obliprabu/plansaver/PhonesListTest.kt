package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonesListTest {
    private fun labels(brand: String) = Phones.brands.getValue(brand).map { it.label }

    @Test fun listsCoverTheRequestedRanges() {
        assertEquals("iPhone XS", labels("iPhone").first())
        assertEquals("iPhone Duo", labels("iPhone").last())
        assertEquals("Galaxy S20", labels("Samsung").first())
        assertTrue("Galaxy S26" in labels("Samsung"))
        assertEquals(listOf("Pixel 4", "Pixel 5", "Pixel 6", "Pixel 7", "Pixel 8", "Pixel 9", "Pixel 10"), labels("Google Pixel"))
    }

    @Test fun defaultStartsOnIphone16() {
        assertEquals("iPhone 16", Phones.brands.getValue("iPhone")[Phones.defaultIphoneIdx].label)
    }

    @Test fun pixelAVariantStillMatchesFamilyDeals() {
        assertTrue(dealMatchesPhone("Save on Pixel 10 when you switch", "Pixel 10a"))
        assertTrue(dealMatchesPhone("Free Galaxy S26 with trade-in", "Galaxy S26"))
    }
}

class PixelAMatchTest {
    @Test fun aModelsAreTheirOwnPhone() {
        assertTrue(dealMatchesPhone("Get a free Pixel 9a when you switch", "Pixel 9a"))
        org.junit.Assert.assertFalse(dealMatchesPhone("Get a free Pixel 9a when you switch", "Pixel 9"))
        assertTrue(dealMatchesPhone("Save on Pixel 9 when you switch", "Pixel 9a")) // bare family covers the whole family
    }

    @Test fun xsAndXrAreRecognisedAsPhones() {
        org.junit.Assert.assertFalse(dealMatchesPhone("Free iPhone XR with trade-in", "iPhone 16"))
        assertTrue(dealMatchesPhone("Free iPhone XR with trade-in", "iPhone XR"))
    }
}
