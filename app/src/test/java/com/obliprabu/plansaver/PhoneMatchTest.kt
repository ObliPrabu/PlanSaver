package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMatchTest {
    @Test fun baseDoesNotMatchProDeal() = assertFalse(dealMatchesPhone("$800 off iPhone 16 Pro", "iPhone 16"))
    @Test fun exactMatches() = assertTrue(dealMatchesPhone("$800 off iPhone 16 Pro", "iPhone 16 Pro"))
    @Test fun bareFamilyCoversPro() = assertTrue(dealMatchesPhone("Free iPhone 16 with trade-in", "iPhone 16 Pro"))
    @Test fun fanEditionIsSeparate() = assertFalse(dealMatchesPhone("Galaxy S25 FE for $0", "Galaxy S25"))
    @Test fun plusAndSymbolAreSame() = assertTrue(dealMatchesPhone("Galaxy S25 Plus deal", "Galaxy S25+"))
    @Test fun foldableMatchesAnyGeneration() = assertTrue(dealMatchesPhone("Free Galaxy Z Flip 7", "Galaxy Z Flip"))
    @Test fun noPhoneNamedAlwaysApplies() = assertTrue(dealMatchesPhone("Save $800 when you switch", "iPhone 16"))
    @Test fun newerIPhoneIsBetter() = assertTrue(dealIsBetterPhone("$1,000 off iPhone 18 Pro", "iPhone 16"))
    @Test fun olderIPhoneIsNot() = assertFalse(dealIsBetterPhone("$100 off iPhone 14", "iPhone 16"))
    @Test fun coverageLinksPerNetwork() = assertEquals(3, coverageLinks("Verizon, AT&T or T-Mobile").size)
    @Test fun airIsNewerThan17() = assertTrue(dealIsBetterPhone("Free iPhone Air", "iPhone 17 Pro"))
    @Test fun airIsNotBetterThan18() = assertFalse(dealIsBetterPhone("Free iPhone Air", "iPhone 18"))
    @Test fun olderPhonesAreNotBetterThanAir() = assertFalse(dealIsBetterPhone("Free iPhone 17 Pro", "iPhone Air"))
    @Test fun xsAndXrSitBelowNumbered() {
        assertTrue(dealIsBetterPhone("iPhone 11 deal", "iPhone XS"))
        assertFalse(dealIsBetterPhone("iPhone XR deal", "iPhone 11"))
        assertFalse(dealIsBetterPhone("iPhone XS Max deal", "iPhone 12"))
    }
    @Test fun eModelIsNotBetterThanSameNumber() {
        assertFalse(dealIsBetterPhone("iPhone 16e for $0", "iPhone 16"))
        assertTrue(dealIsBetterPhone("iPhone 16e for $0", "iPhone 15"))
        assertTrue(dealIsBetterPhone("iPhone 16 for $0", "iPhone 16e"))
    }
    @Test fun duoStaysNewest() = assertTrue(dealIsBetterPhone("iPhone Duo", "iPhone Air"))
}
