package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate

/** How far a part's open ring sinks into its parent, as a multiple of that ring's radius in the template. */
private const val SINK_OF_OPEN_RING = 0.6f

/** How far a part with no open end sinks into its parent, as a multiple of its widest ring's radius in the template. */
private const val SINK_OF_CLOSED_PART = 0.35f

/**
 * How far this template's socket plane sits inside what it is on. It reads the template's build with nothing drawn, never
 * the part's, so a stroke on a part never moves the plane it is drawn from.
 */
internal fun PartTemplate.socketSink(): Float {
    val slices = PartBuilder.build(this).firstNotNullOfOrNull { it.slices } ?: return 0f
    val ring = when (openEnd) {
        End.START -> slices.first()
        End.END -> slices.last()
        null -> return slices.maxOf { maxOf(it.height, it.width) } / 2f * SINK_OF_CLOSED_PART
    }
    return maxOf(ring.height, ring.width) / 2f * SINK_OF_OPEN_RING
}
