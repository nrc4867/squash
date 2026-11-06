package dev.chieppa.squash.screens.lines

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName

const val MIN_WIDTH_SIZE = 4f
const val MAX_WIDTH_SIZE = 60f
const val DEFAULT_WIDTH_SIZE = 24f
const val DEFAULT_WIDTH_AMOUNT = 0.6f

/** A line weight picked in the Marks drawer, on the part that shows it. */
data class PickedWeight(val part: PartId, val id: LineWeightId)

/** A stroke by the part that holds it. */
data class PickedMark(val part: PartId, val id: MarkId)

/**
 * What the line tools remember between strokes: the ring's [size] in pixels, the [amount] it adds, the weight [picked], which Amount edits, the [strokeSlot]
 * a stroke draws in, the ink's if none, and the [pickedMark] that shows on the model.
 */
@Stable
class LineEditState {
    var size by mutableStateOf(DEFAULT_WIDTH_SIZE)
    var amount by mutableStateOf(DEFAULT_WIDTH_AMOUNT)
    var picked by mutableStateOf<PickedWeight?>(null)
    var strokeSlot by mutableStateOf<SlotName?>(null)
    var pickedMark by mutableStateOf<PickedMark?>(null)

    fun clear() {
        picked = null
        pickedMark = null
    }
}
