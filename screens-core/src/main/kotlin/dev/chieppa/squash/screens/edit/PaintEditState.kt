package dev.chieppa.squash.screens.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SlotName

/** The Brush's radius at the start, as a share of the axis of the part it lands on. */
const val DEFAULT_BRUSH_RADIUS = 0.05f

/** What Fill colors under the pointer: what shows there, or down to an area its template names that nothing has filled. */
enum class FillReach { PART, AREA }

/** A region by the part that holds it. */
data class RegionRef(val part: PartId, val id: RegionId)

/** What the paint tools remember between gestures. Never saved or undone. */
@Stable
class PaintEditState {
    /** The slot the tools paint with; the first of the palette while this names none it holds. */
    var slot by mutableStateOf<SlotName?>(null)
    var brushRadius by mutableFloatStateOf(DEFAULT_BRUSH_RADIUS)
    var softness by mutableFloatStateOf(0f)
    var fillReach by mutableStateOf(FillReach.PART)

    /** Which slots show in the drawer. */
    val shelf = SlotShelf()

    /** The border being edited, whose points show for dragging; with none, Region border draws a new one. */
    var selectedRegion by mutableStateOf<RegionRef?>(null)

    /** The slot the tools paint with in [palette], or null when it holds none. */
    fun slotIn(palette: Palette): SlotName? = slot?.takeIf { palette.color(it) != null } ?: palette.slots.firstOrNull()?.name

    fun clear() {
        slot = null
        brushRadius = DEFAULT_BRUSH_RADIUS
        softness = 0f
        fillReach = FillReach.PART
        selectedRegion = null
        shelf.clear()
    }
}
