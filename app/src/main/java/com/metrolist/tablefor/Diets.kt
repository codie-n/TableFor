package com.metrolist.tablefor

/** The diet choices' one rule: "None" can't be chosen together with a real diet. */
object Diets {
    const val NONE = "None"

    /**
     * The diets after tapping [diet]'s chip. A selected chip is unselected; otherwise
     * "None" clears the other diets, and a real diet clears "None".
     */
    fun toggle(diets: List<String>, diet: String): List<String> = when {
        diet in diets -> diets - diet
        diet == NONE -> listOf(NONE)
        else -> diets.filter { it != NONE } + diet
    }

    /** A saved list holding both "None" and real diets, from before the rule, keeps the real ones. */
    fun cleaned(diets: List<String>): List<String> =
        if (diets.any { it != NONE }) diets.filter { it != NONE } else diets
}
