package dev.chieppa.squash.screens.surface.paint

import dev.chieppa.squash.screens.surface.areaAt
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.character.build.LandedCut
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.screens.edit.FillReach
import kotlin.math.max
import kotlin.math.min

/** What Fill colors whole: a part's base, a template's area or colored block, by the name a fill holds it under, or a closed region. */
sealed interface Section {
    data class Base(val part: PartId) : Section

    data class Named(val holder: PartId, val name: String) : Section

    data class Border(val source: ShapeSource.Border) : Section
}

/** The cuts lying on the patch under [hit], from every part that holds work on it, in the order they overdraw, last on top. */
private fun CharacterSession.cutsUnder(hit: SurfaceHit): List<LandedCut> {
    val parts = document.value.character.parts.map { it.id }
    return parts.flatMap { heldColor(it).output.value?.cuts.orEmpty() }
        .filter { it.part == hit.part && it.cut.patch == hit.patch }
        .sortedBy { it.rank }
}

/** Whether the place at [u] and [v] lies in this cut, and at some opacity where it fades. */
internal fun PatchCut.holds(u: Float, v: Float): Boolean {
    val indices = mesh.indices
    var at = 0
    while (at + 2 < indices.size) {
        val a = indices[at]
        val b = indices[at + 1]
        val c = indices[at + 2]
        at += 3
        if (alphas != null && alphas!![a] <= 0f && alphas!![b] <= 0f && alphas!![c] <= 0f) continue
        if (triangleHolds(u, v, us[a], vs[a], us[b], vs[b], us[c], vs[c])) return true
    }
    return false
}

/** Whether a triangle in u and v holds a place; one crossing the seam is read with its low v a turn on, and the place both ways. */
private fun triangleHolds(u: Float, v: Float, au: Float, av: Float, bu: Float, bv: Float, cu: Float, cv: Float): Boolean {
    val crosses = max(av, max(bv, cv)) - min(av, min(bv, cv)) > HALF_TURN
    val a = if (crosses && av < HALF_TURN) av + 1f else av
    val b = if (crosses && bv < HALF_TURN) bv + 1f else bv
    val c = if (crosses && cv < HALF_TURN) cv + 1f else cv
    fun holds(place: Float): Boolean {
        val first = (bu - au) * (place - a) - (b - a) * (u - au)
        val second = (cu - bu) * (place - b) - (c - b) * (u - bu)
        val third = (au - cu) * (place - c) - (a - c) * (u - cu)
        return (first >= 0f && second >= 0f && third >= 0f) || (first <= 0f && second <= 0f && third <= 0f)
    }
    return holds(v) || (crosses && holds(v + 1f))
}

private const val HALF_TURN = 0.5f

/** The slot showing at [hit]: the topmost layer's there, or the part's base. */
fun CharacterSession.slotAt(hit: SurfaceHit): SlotName? {
    val top = cutsUnder(hit).lastOrNull { it.cut.holds(hit.u, hit.v) }
    return top?.slot ?: heldColor(hit.part).output.value?.baseSlot
}

/**
 * The section Fill colors at [hit]: what shows there, a brush stroke being no section, so a region, a filled area or a colored block, and
 * otherwise the part. With [FillReach.AREA] an area the template names there counts though nothing has filled it.
 */
fun CharacterSession.sectionAt(hit: SurfaceHit, reach: FillReach): Section {
    val top = cutsUnder(hit).lastOrNull { it.source !is ShapeSource.Stroke && it.cut.holds(hit.u, hit.v) }
    when (val source = top?.source) {
        is ShapeSource.Border -> return Section.Border(source)
        is ShapeSource.Area -> return Section.Named(source.holder, source.name)
        is ShapeSource.TemplateBlock -> return Section.Named(source.holder, source.block)
        else -> Unit
    }
    if (reach == FillReach.AREA) {
        areaAt(document.value.character, templates, hit.anchor)?.let { return Section.Named(hit.part, it) }
    }
    return Section.Base(hit.part)
}

/** The command coloring [section] with [slot], writing this side alone with [thisSide], or null when the document holds no such section. */
fun CharacterSession.fillCommand(section: Section, slot: SlotName, thisSide: Boolean): DocumentCommand? {
    val character = document.value.character
    fun side(part: PartId) = thisSide && character.part(part)?.pair != null
    return when (section) {
        is Section.Base -> SetBaseSlot(section.part, slot, side(section.part))
        is Section.Named -> SetAreaFill(section.holder, section.name, slot, side(section.holder))
        is Section.Border -> {
            val holder = character.part(section.source.holder) ?: return null
            val region = character.editBase(holder, side(holder.id)).surface.regions.firstOrNull { it.id == section.source.id } ?: return null
            SetRegion(holder.id, region.id, region.copy(slot = slot), side(holder.id))
        }
    }
}
