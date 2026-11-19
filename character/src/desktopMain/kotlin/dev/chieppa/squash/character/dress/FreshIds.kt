package dev.chieppa.squash.character.dress

/** The first id from [prefix] and a number that [taken] does not hold, with its number. */
fun freshNumbered(prefix: String, taken: Set<String>): Pair<String, Int> =
    generateSequence(1) { it + 1 }.map { "$prefix$it" to it }.first { it.first !in taken }
