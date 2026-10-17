package com.obliprabu.plansaver

/** iPhone generations and the variants offered for each (base, Plus, Pro, Pro Max). */
object Phones {
    // 17 and 18 have no "Plus" here: Apple replaced it with the Air in the 17 line, and the 18 lineup
    // is unconfirmed. Re-check against Apple's current lineup before release.
    val lineup: Map<Int, List<String>> = mapOf(
        15 to listOf("", " Plus", " Pro", " Pro Max"),
        16 to listOf("", " Plus", " Pro", " Pro Max"),
        17 to listOf("", " Pro", " Pro Max"),
        18 to listOf("", " Pro", " Pro Max"),
    )

    fun name(gen: Int, variant: String) = "iPhone $gen$variant"
}
