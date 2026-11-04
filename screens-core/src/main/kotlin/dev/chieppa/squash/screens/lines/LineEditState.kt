package dev.chieppa.squash.screens.lines

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.PartId

const val MIN_WIDTH_SIZE = 4f
const val MAX_WIDTH_SIZE = 60f
const val DEFAULT_WIDTH_SIZE = 24f
const val DEFAULT_WIDTH_AMOUNT = 0.6f

/** A line weight picked in the Marks drawer, on the part that shows it. */
data class PickedWeight(val part: PartId, val id: LineWeightId)

/** What the Line width tool remembers between strokes: the ring's [size] in pixels, the [amount] it adds, and the weight [picked], which Amount edits. */
@Stable
class LineEditState {
    var size by mutableStateOf(DEFAULT_WIDTH_SIZE)
    var amount by mutableStateOf(DEFAULT_WIDTH_AMOUNT)
    var picked by mutableStateOf<PickedWeight?>(null)

    fun clear() {
        picked = null
    }
}
