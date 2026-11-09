package dev.chieppa.squash.screens.edit

import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetBranchOutline
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.dress.covers

/**
 * What the outline tools draw on: a part's own outlines, on the pair or with [Part.thisSide] on its side alone; the outlines of a
 * garment's branch over a part; or the outlines a garment's Redraw gives a part while it is worn.
 */
sealed interface OutlineTarget {
    /** The part whose panes the outlines are drawn in. */
    val part: PartId

    data class Part(override val part: PartId, val thisSide: Boolean) : OutlineTarget

    data class Branch(val garment: GarmentId, override val part: PartId) : OutlineTarget

    data class Redraw(val garment: GarmentId, override val part: PartId) : OutlineTarget
}

/** The outlines [target] holds in [this] character, as the tools start from them. */
fun Character.outlinesOf(target: OutlineTarget): Map<View, OutlineCurve> = when (target) {
    is OutlineTarget.Part -> part(target.part)?.let { editBase(it, target.thisSide).outlines }.orEmpty()
    is OutlineTarget.Branch -> wardrobe.garment(target.garment)?.branch(target.part)?.outlines.orEmpty()
    is OutlineTarget.Redraw -> (wardrobe.garment(target.garment)?.changes?.get(target.part) as? BodyPartChange.Redraw)?.data?.outlines.orEmpty()
}

/** The command that makes [curve] [target]'s outline in [view], or clears it when null; null when [target] is gone from [this] character. */
fun Character.writeOutline(target: OutlineTarget, view: View, curve: OutlineCurve?): DocumentCommand? = when (target) {
    is OutlineTarget.Part -> SetOutline(target.part, view, curve, target.thisSide)
    is OutlineTarget.Branch -> SetBranchOutline(target.garment, target.part, view, curve)
    is OutlineTarget.Redraw -> {
        val redraw = wardrobe.garment(target.garment)?.changes?.get(target.part) as? BodyPartChange.Redraw
        redraw?.let {
            val outlines = if (curve == null) it.data.outlines - view else it.data.outlines + (view to curve)
            SetBodyPartChange(target.garment, target.part, BodyPartChange.Redraw(it.data.copy(outlines = outlines)))
        }
    }
}

/**
 * The target in Dress for the selected [part] with [garment] picked: the garment's Redraw of it while [redrawing] names that part and
 * the garment holds a Redraw for it, else the garment's branch over it, or null where the garment does not cover it.
 */
fun Character.dressTarget(garment: GarmentId, part: PartId, redrawing: PartId?): OutlineTarget? {
    val picked = wardrobe.garment(garment) ?: return null
    val instance = part(part) ?: return null
    if (redrawing == part && picked.changes[part] is BodyPartChange.Redraw) return OutlineTarget.Redraw(garment, part)
    val branch = covers(picked, instance) ?: return null
    return OutlineTarget.Branch(garment, branch.part)
}
