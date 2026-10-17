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
    private val iphoneVariants = listOf("", " Plus", " Pro", " Pro Max")

    val brands: Map<String, List<PhoneFamily>> = linkedMapOf(
        "iPhone" to listOf(
            PhoneFamily("iPhone 13", listOf("", " mini", " Pro", " Pro Max")),
            PhoneFamily("iPhone 14", iphoneVariants),
            PhoneFamily("iPhone 15", iphoneVariants),
            PhoneFamily("iPhone 16", iphoneVariants),
            PhoneFamily("iPhone 17", listOf("", " Pro", " Pro Max")),
            PhoneFamily("iPhone 18", listOf("", " Pro", " Pro Max")),
        ),
        "Samsung" to listOf(
            PhoneFamily("Galaxy S23", listOf("", "+", " Ultra")),
            PhoneFamily("Galaxy S24", listOf("", "+", " Ultra")),
            PhoneFamily("Galaxy S25", listOf("", "+", " Ultra")),
            PhoneFamily("Galaxy Z Flip", listOf("")),
            PhoneFamily("Galaxy Z Fold", listOf("")),
        ),
        "Google Pixel" to listOf(
            PhoneFamily("Pixel 7", listOf("", " Pro")),
            PhoneFamily("Pixel 8", listOf("", " Pro")),
            PhoneFamily("Pixel 9", listOf("", " Pro", " Pro XL")),
            PhoneFamily("Pixel 10", listOf("", " Pro", " Pro XL")),
        ),
    )
}
