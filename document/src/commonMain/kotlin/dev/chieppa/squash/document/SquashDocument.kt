package dev.chieppa.squash.document

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

const val DOCUMENT_FORMAT = 5

/**
 * The whole document. [format] numbers its grammar and is always written; only the current format decodes, and [readDocument]
 * brings an older save forward. See HLD: data-model, Adding parts without refactoring.
 */
@Serializable
data class SquashDocument(val character: Character, @Required val format: Int = DOCUMENT_FORMAT) {
    init {
        require(format == DOCUMENT_FORMAT) { "This app reads document format $DOCUMENT_FORMAT, not $format" }
    }
}
