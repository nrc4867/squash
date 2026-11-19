package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.character.build.GarmentView
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.surface.PaintShape
import kotlinx.coroutines.flow.MutableStateFlow

/** A new brush's size, in body units, and how much Clean edges smooths at first. */
const val DEFAULT_BRUSH_SIZE = 0.12f
const val DEFAULT_CLEAN = 0.6f

/**
 * What the Dress stage remembers: the garment picked, the garments left out of view, how the garments
 * show in Dress and in every other stage, and the Loose end tool's options. Never saved or undone.
 */
class DressEditState {
    /** The garment the inspector and the garment tools work on. */
    val garment = MutableStateFlow<GarmentId?>(null)

    /** The picked garment's edge picked in the inspector or by Edit curves, by its place in the garment's edges, whose points show. */
    val edge = MutableStateFlow<Int?>(null)

    /** The picked garment's area Paint lays strokes in, or null for its first. */
    val area = MutableStateFlow<GarmentAreaId?>(null)

    /** Paint's options: unpainting rather than painting, the brush's size in body units, Clean edges, the shape a drag lays. */
    val unpaint = MutableStateFlow(false)
    val brushSize = MutableStateFlow(DEFAULT_BRUSH_SIZE)
    val clean = MutableStateFlow(DEFAULT_CLEAN)
    val shape = MutableStateFlow(PaintShape.BRUSH)

    /** Whether Edit curves shows every edge's points, not just the picked edge's. */
    val handles = MutableStateFlow(false)

    /** Whether a second Hole press on a part adds a cuff band. */
    val cuffOnSecondPress = MutableStateFlow(true)

    /** The part whose Redraw by the picked garment the outline tools draw on, or null for the garment's branches. */
    val redrawing = MutableStateFlow<dev.chieppa.squash.document.PartId?>(null)

    /** The garments left out of view by the layers list's Show buttons. */
    val hidden = MutableStateFlow<Set<GarmentId>>(emptySet())

    /** How the garments show in the Dress stage. */
    val lookInDress = MutableStateFlow(GarmentLook.SHOWN)

    /** How the garments show in every stage but Dress. */
    val lookElsewhere = MutableStateFlow(GarmentLook.GHOSTED)

    /** How many bones the Loose end tool gives a new loose end. */
    val looseBones = MutableStateFlow(LooseEnd.DEFAULT_LOOSE_BONES)

    /** Whether the Loose end tool starts a new loose end at the hem. */
    val looseFromHem = MutableStateFlow(true)

    /** The setting the garments show by in Dress when [dressing], else everywhere else. */
    fun look(dressing: Boolean): MutableStateFlow<GarmentLook> = if (dressing) lookInDress else lookElsewhere

    /** What the session is asked to show of the garments, in Dress when [dressing]. */
    fun view(dressing: Boolean): GarmentView = GarmentView(look(dressing).value, hidden.value)

    /** Shows or leaves out [id]. */
    fun toggleShown(id: GarmentId) {
        hidden.value = if (id in hidden.value) hidden.value - id else hidden.value + id
    }

    /** Keeps the picked garment to what [document] holds, picking the first worn garment when none is. */
    fun follow(document: SquashDocument) {
        val wardrobe = document.character.wardrobe
        if (garment.value?.let(wardrobe::garment) == null) garment.value = wardrobe.worn.lastOrNull()?.id ?: wardrobe.garments.firstOrNull()?.id
        hidden.value = hidden.value.filterTo(mutableSetOf()) { wardrobe.garment(it) != null }
    }

    fun clear() {
        garment.value = null
        edge.value = null
        area.value = null
        redrawing.value = null
        hidden.value = emptySet()
    }
}
