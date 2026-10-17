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
}
