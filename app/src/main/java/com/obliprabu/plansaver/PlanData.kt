package com.obliprabu.plansaver

/**
 * Deals and free-phone offers change weekly and normally need a trade-in and a bill-credit
 * commitment (cancel early = lose the credits) - show that fine print in the UI.
 * Researched Oct 2026 from third-party roundups (US Mobile blog, NerdWallet,
 * US News, PhoneArena) - NOT yet verified against each carrier's own site.
 * Verify and fill the missing multi-line prices before publishing.
 */
object PlanData {
    val plans = listOf(
        Plan("Mint Mobile", "6GB (12-month)", "T-Mobile", true, 6, mapOf(1 to 15.0), "Paid up front for 12 months", "https://www.mintmobile.com", termMonths = 12),
        Plan("Mint Mobile", "17GB (12-month)", "T-Mobile", true, 17, mapOf(1 to 20.0), "Paid up front for 12 months", "https://www.mintmobile.com", termMonths = 12),
        Plan("Mint Mobile", "23GB (12-month)", "T-Mobile", true, 23, mapOf(1 to 25.0), "Paid up front for 12 months", "https://www.mintmobile.com", termMonths = 12),
        Plan("Mint Mobile", "Unlimited (12-month)", "T-Mobile", true, null, mapOf(1 to 30.0), "Paid up front for 12 months", "https://www.mintmobile.com", termMonths = 12),
        Plan("Mint Mobile", "6GB (3-month)", "T-Mobile", true, 6, mapOf(1 to 25.0), "Paid up front for 3 months", "https://www.mintmobile.com", termMonths = 3),
        Plan("Mint Mobile", "17GB (3-month)", "T-Mobile", true, 17, mapOf(1 to 35.0), "Paid up front for 3 months", "https://www.mintmobile.com", termMonths = 3),
        Plan("Mint Mobile", "23GB (3-month)", "T-Mobile", true, 23, mapOf(1 to 45.0), "Paid up front for 3 months", "https://www.mintmobile.com", termMonths = 3),
        Plan("Mint Mobile", "Unlimited (3-month)", "T-Mobile", true, null, mapOf(1 to 40.0), "Paid up front for 3 months", "https://www.mintmobile.com", termMonths = 3),
        Plan("Mint Mobile", "Unlimited (6-month)", "T-Mobile", true, null, mapOf(1 to 35.0), "Paid up front for 6 months", "https://www.mintmobile.com", termMonths = 6),
        Plan("Visible", "Visible", "Verizon", true, null, mapOf(1 to 25.0), "Taxes and fees included", "https://www.visible.com"),
        Plan("Visible", "Visible+", "Verizon", true, null, mapOf(1 to 35.0), "", "https://www.visible.com"),
        Plan("Visible", "Visible+ Pro", "Verizon", true, null, mapOf(1 to 45.0), "", "https://www.visible.com"),
        Plan("US Mobile", "Flex", "Choice of networks", true, 5, mapOf(1 to 17.5), "Pay-per-use style", "https://www.usmobile.com"),
        Plan("US Mobile", "Starter", "Choice of networks", true, null, mapOf(1 to 25.0), "", "https://www.usmobile.com"),
        Plan("US Mobile", "Premium", "Choice of networks", true, null, mapOf(1 to 44.0), "", "https://www.usmobile.com"),
        Plan("Cricket", "Sensible", "AT&T", true, 10, mapOf(1 to 35.0), "", "https://www.cricketwireless.com"),
        Plan("Cricket", "Supreme Unlimited", "AT&T", true, null, mapOf(1 to 60.0), "", "https://www.cricketwireless.com"),
        Plan("Metro by T-Mobile", "Basic", "T-Mobile", true, 10, mapOf(1 to 30.0), "", "https://www.metrobyt-mobile.com"),
        Plan("Metro by T-Mobile", "Flex Unlimited Plus", "T-Mobile", true, null, mapOf(1 to 65.0), "", "https://www.metrobyt-mobile.com"),
        Plan("T-Mobile", "Essentials", "T-Mobile", false, null, mapOf(1 to 55.0), "", "https://www.t-mobile.com"),
        Plan("T-Mobile", "Experience More", "T-Mobile", false, null, mapOf(1 to 90.0, 4 to 55.0), "", "https://www.t-mobile.com",
            deal = "Keep and Switch: up to \$800 to pay off your old phone; free phones with 24-month agreement and trade-in", freePhoneOffer = true),
        Plan("Verizon", "Unlimited Plus", "Verizon", false, null, mapOf(1 to 90.0, 4 to 45.0), "With autopay", "https://www.verizon.com",
            deal = "Free phones for new lines on select unlimited plans (credits over 36 months)", freePhoneOffer = true),
        Plan("AT&T", "Value 2.0", "AT&T", false, null, mapOf(1 to 50.0), "Starting price", "https://www.att.com",
            deal = "Switcher deals with up to \$800 device payoff; trade-in bill credits over 36 months", freePhoneOffer = true),
        Plan("AT&T", "Extra 2.0", "AT&T", false, null, mapOf(4 to 40.0), "4-line price", "https://www.att.com"),
        Plan("Total Wireless", "Total STARTER", "Verizon", true, null, mapOf(1 to 35.0), "Autopay; 5-year price lock for new customers", "https://www.totalwireless.com"),
        Plan("Total Wireless", "Total MAX 5G", "Verizon", true, null, mapOf(1 to 50.0), "Autopay; unlimited hotspot at 5 Mbps", "https://www.totalwireless.com"),
        Plan("Total Wireless", "Total ALL ACCESS", "Verizon", true, null, mapOf(1 to 60.0), "Autopay; hotspot at 10 Mbps", "https://www.totalwireless.com"),
        Plan("Boost Mobile", "Unlimited", "AT&T", true, null, mapOf(1 to 30.0), "\$25 with autopay", "https://www.boostmobile.com"),
        Plan("Boost Mobile", "Unlimited Premium", "AT&T", true, null, mapOf(1 to 65.0), "\$60 with autopay", "https://www.boostmobile.com"),
        Plan("Straight Talk", "Silver Unlimited", "Verizon, AT&T or T-Mobile", true, null, mapOf(1 to 45.0), "", "https://www.straighttalk.com"),
        Plan("Straight Talk", "Platinum Unlimited", "Verizon, AT&T or T-Mobile", true, null, mapOf(1 to 65.0), "", "https://www.straighttalk.com"),
        Plan("Consumer Cellular", "Basic", "AT&T", true, 1, mapOf(1 to 20.0), "", "https://www.consumercellular.com"),
        Plan("Consumer Cellular", "Unlimited 50+", "AT&T", true, null, mapOf(1 to 35.0), "For ages 50 and up", "https://www.consumercellular.com"),
        Plan("Google Fi", "Flexible", "T-Mobile", true, 1, mapOf(1 to 20.0), "Base price; extra data billed per GB", "https://fi.google.com"),
        Plan("Google Fi", "Unlimited Premium", "T-Mobile", true, null, mapOf(1 to 65.0), "Data and texting in 200+ countries", "https://fi.google.com"),
        Plan("Xfinity Mobile", "Mobile Select", "Verizon", false, null, mapOf(1 to 30.0), "Requires Xfinity internet", "https://www.xfinity.com/mobile"),
        Plan("Xfinity Mobile", "Mobile Plus", "Verizon", false, null, mapOf(1 to 45.0), "Requires Xfinity internet", "https://www.xfinity.com/mobile"),
        Plan("Spectrum Mobile", "Unlimited", "Verizon", false, null, mapOf(1 to 30.0), "Requires Spectrum internet", "https://www.spectrum.com/mobile"),
        Plan("Spectrum Mobile", "Unlimited Plus", "Verizon", false, null, mapOf(1 to 40.0), "Requires Spectrum internet", "https://www.spectrum.com/mobile"),
    )
}
