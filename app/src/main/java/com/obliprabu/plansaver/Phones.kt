package com.obliprabu.plansaver

/** One model line (e.g. "iPhone 16") and the variants sold under it. "" = the base model. */
data class PhoneFamily(val label: String, val variants: List<String>) {
    fun name(variant: String) = label + variant
}

/**
 * Phones offered in the picker, by brand. iPhone 17/18 have no Plus (Apple swapped it for the Air in the 17
 * line; the 18 lineup is unconfirmed), 13 has a mini instead. Android lines are limited to ones I'm sure
 * exist - add the newest Galaxy S / Pixel generations after checking the carriers' current lineups.
 */
object Phones {
    /** Where "iPhone 16" sits in the iPhone list - the picker's starting model. */
    val defaultIphoneIdx: Int get() = brands.getValue("iPhone").indexOfFirst { it.label == "iPhone 16" }

    private val iphoneVariants = listOf("", " Plus", " Pro", " Pro Max")

    val brands: Map<String, List<PhoneFamily>> = linkedMapOf(
        "iPhone" to listOf(
            PhoneFamily("iPhone XS", listOf("", " Max")),
            PhoneFamily("iPhone XR", listOf("")),
            PhoneFamily("iPhone 11", listOf("", " Pro", " Pro Max")),
            PhoneFamily("iPhone 12", listOf("", " mini", " Pro", " Pro Max")),
            PhoneFamily("iPhone 13", listOf("", " mini", " Pro", " Pro Max")),
            PhoneFamily("iPhone 14", iphoneVariants),
            PhoneFamily("iPhone 15", iphoneVariants),
            PhoneFamily("iPhone 16", iphoneVariants),
            PhoneFamily("iPhone 17", listOf("", " Pro", " Pro Max")),
            PhoneFamily("iPhone Air", listOf("")),
            PhoneFamily("iPhone 18", listOf("", " Pro", " Pro Max")),
            PhoneFamily("iPhone Duo", listOf("")), // Apple's first foldable, announced Sep 9 2026
        ),
        "Samsung" to listOf(
            PhoneFamily("Galaxy S20", listOf("", "+", " Ultra", " FE")),
            PhoneFamily("Galaxy S21", listOf("", "+", " Ultra", " FE")),
            PhoneFamily("Galaxy S22", listOf("", "+", " Ultra")),
            PhoneFamily("Galaxy S23", listOf("", "+", " Ultra", " FE")),
            PhoneFamily("Galaxy S24", listOf("", "+", " Ultra", " FE")),
            PhoneFamily("Galaxy S25", listOf("", "+", " Ultra", " FE")),
            PhoneFamily("Galaxy S26", listOf("", "+", " Ultra")),
            PhoneFamily("Galaxy Z Flip", listOf("")),
            PhoneFamily("Galaxy Z Fold", listOf("")),
        ),
        "Google Pixel" to listOf(
            PhoneFamily("Pixel 4", listOf("", " XL", "a")),
            PhoneFamily("Pixel 5", listOf("", "a")),
            PhoneFamily("Pixel 6", listOf("", " Pro", "a")),
            PhoneFamily("Pixel 7", listOf("", " Pro", "a")),
            PhoneFamily("Pixel 8", listOf("", " Pro", "a")),
            PhoneFamily("Pixel 9", listOf("", " Pro", " Pro XL", " Pro Fold", "a")),
            PhoneFamily("Pixel 10", listOf("", " Pro", " Pro XL", " Pro Fold", "a")),
        ),
    )
}
