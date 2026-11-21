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

/** What Fill colors: what shows under the pointer, down to an area its template names there, or the whole part's base. */
enum class FillReach { UNDER_POINTER, WHOLE_PART }

/** A region by the part that holds it. */
data class RegionRef(val part: PartId, val id: RegionId)

/** What the paint tools remember between gestures. Never saved or undone. */
@Stable
class PaintEditState {
    /** The slot picked to paint with; while this names none the palette holds, the tools take one by [slotIn]. */
    var slot by mutableStateOf<SlotName?>(null)
    var brushRadius by mutableFloatStateOf(DEFAULT_BRUSH_RADIUS)
    var softness by mutableFloatStateOf(0f)
    var fillReach by mutableStateOf(FillReach.UNDER_POINTER)

    /** Whether the slot material's More section is open. */
    var materialMoreOpen by mutableStateOf(false)

    /** Which slots show in the drawer. */
    val shelf = SlotShelf()

    /** The region picked in the inspector, whose points Region shows for dragging; with none, Region draws a new one. */
    var selectedRegion by mutableStateOf<RegionRef?>(null)

    /** The slot the tools paint with in [palette], the picked one or else the first that is not [base], or null when the palette holds none. */
    fun slotIn(palette: Palette, base: SlotName? = null): SlotName? =
        slot?.takeIf { palette.color(it) != null } ?: (palette.slots.firstOrNull { it.name != base } ?: palette.slots.firstOrNull())?.name

    fun clear() {
        slot = null
        brushRadius = DEFAULT_BRUSH_RADIUS
        softness = 0f
        fillReach = FillReach.UNDER_POINTER
        materialMoreOpen = false
        selectedRegion = null
        shelf.clear()
    }
}
