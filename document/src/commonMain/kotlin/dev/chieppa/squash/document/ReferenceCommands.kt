package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Adds, replaces or removes the reference image [id]. It keeps its place when replaced; [index] places a new one, last if null.
 * Removing it takes away the traces that show it, which its undo puts back.
 */
@Serializable
@SerialName("setReferenceImage")
data class SetReferenceImage(val id: ImageId, val image: ReferenceImage?, val index: Int? = null, val traces: Map<View, Trace> = emptyMap()) : DocumentCommand {
    init {
        require(image == null || image.id == id) { "Image ${image?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val images = document.references.images
        val old = images.locate { it.id == id }
        val next = images.withItem(image, index) { it.id == id }
        val character = document.character
        val dropped = if (image == null) character.traces.filterValues { it.image == id } else emptyMap()
        val traces = character.traces - dropped.keys + traces
        return Applied(
            document.copy(references = References(next), character = character.copy(traces = traces)),
            SetReferenceImage(id, old.value, old.restoreIndex, dropped),
        )
    }
}

/** Places [trace] in [view], or with null takes the trace there away. Its image is on the References board. */
@Serializable
@SerialName("setTrace")
data class SetTrace(val view: View, val trace: Trace?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        require(trace == null || document.references.image(trace.image) != null) { "No reference image ${trace?.image?.value}" }
        val character = document.character
        val old = character.traces[view]
        val traces = if (trace == null) character.traces - view else character.traces + (view to trace)
        return Applied(document.copy(character = character.copy(traces = traces)), SetTrace(view, old))
    }
}
