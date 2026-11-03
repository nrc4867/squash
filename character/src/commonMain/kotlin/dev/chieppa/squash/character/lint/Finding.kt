package dev.chieppa.squash.character.lint

/** A semantic problem in a template or plan, at a JSON [path] such as `$.blocks[2].u`. */
data class Finding(val path: String, val message: String)
