package dev.chieppa.squash.character.catalog

private val WORD_BREAK = Regex("(?<=[a-z0-9])(?=[A-Z])|[-_]")

/** A name from a template or plan file as people read it: `front-leg` and `frontLeg` are both "Front leg". */
fun displayName(name: String): String =
    name.split(WORD_BREAK).joinToString(" ") { it.lowercase() }.replaceFirstChar { it.uppercase() }
