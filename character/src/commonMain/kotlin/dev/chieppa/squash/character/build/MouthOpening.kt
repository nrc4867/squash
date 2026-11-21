package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.mouth.FlatPiece
import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.character.mouth.mouthRecessOf
import dev.chieppa.squash.character.surface.LayerTint
import dev.chieppa.squash.character.surface.TintTarget
import dev.chieppa.squash.character.mouth.ShapedMouth
import dev.chieppa.squash.character.mouth.mouthMarks
import dev.chieppa.squash.character.mouth.shapeMouth
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2

/** The rank an open mouth's fills paint at: over every region and stroke of its part. */
internal const val RANK_MOUTH = 4

private const val MIN_SHAPE_POINTS = 3

/** How deep into a carved mouth, a share of the way from the lips, its back starts, which shows darker than the walls near the lips. */
private const val BACK_DEPTH = 0.5f

/** How far the back of a carved mouth's inside is taken toward the ink, so it reads darker than the walls near the lips. */
internal val BACK_TINT = LayerTint(TintTarget.Ink, 0.35f)
private const val MIN_LINE_POINTS = 2

/** What a shaped mouth adds to its part: fills in layer order, ink lines, and the lip line marks those lines stand in for. */
data class OpenMouths(val shapes: List<LandedShape>, val lines: List<ResolvedMark>, val replaced: Set<MarkId> = emptySet()) {
    companion object {
        val NONE = OpenMouths(emptyList(), emptyList())
    }
}

/**
 * Every mouth [holder] holds as [MouthShaper][dev.chieppa.squash.character.mouth.MouthShaper] shapes it, each in the style its [work]
 * names for it (plain lips where none): the opening between the lips filled in the inner role's slot, the teeth and tongue in theirs, inked where their role
 * is, the lower lip and a pressed crease inked as the lip line is, and, once the upper lip leaves its drawn line, that line drawn shaped in place
 * of its marks. A mouth that rests as drawn adds nothing. See HLD: look, Mouth.
 */
fun openMouths(holder: PartId, work: ResolvedSurfaceWork, surfaces: Map<PartId, PartSurface>): OpenMouths {
    val surface = surfaces[holder] ?: return OpenMouths.NONE
    val marks = work.marks.associateBy { it.id }
    val bySource = work.marks.groupBy { it.source }
    val shaped = shapedMouths(holder, work, surface).mapNotNull { (group, mouth) ->
        if (mouth.flat.restsAsDrawn) {
            null
        } else {
            val back = mouthRecessOf(holder, group.id, mouth, surface, styleOf(work, group).carve)?.solid?.deeperThan(BACK_DEPTH)
            MouthLanding(holder, group, mouth, surface, marks, bySource, back)
        }
    }
    if (shaped.isEmpty()) return OpenMouths.NONE
    return OpenMouths(shaped.flatMap { it.shapes() }, shaped.flatMap { it.lines() }, shaped.flatMap { it.replaced() }.toSet())
}

/** Every mouth group [holder]'s [work] holds, shaped on its [surface] in the style [work] names for it (plain lips where none). */
internal fun shapedMouths(holder: PartId, work: ResolvedSurfaceWork, surface: PartSurface): List<Pair<MarkGroup, ShapedMouth>> {
    val bySource = work.marks.groupBy { it.source }
    return work.groups.filter { it.isMouth }.mapNotNull { group ->
        val placed = group.mouthMarks { id -> bySource[id].orEmpty().map { mark -> mark.points.map { it.anchor } } }
        shapeMouth(holder, placed, group.controls, styleOf(work, group), surface)?.let { group to it }
    }
}

private fun styleOf(work: ResolvedSurfaceWork, group: MarkGroup): MouthStyle = work.mouthStyles[group.id] ?: MouthStyle.PLAIN

/**
 * The recess each mouth [holder] holds cuts into it where its lips part, as its style carves it; none for a painted style or a mouth at
 * rest. See HLD: look, Mouth.
 */
fun mouthRecesses(holder: PartId, work: ResolvedSurfaceWork, surface: PartSurface): List<MouthRecess> =
    shapedMouths(holder, work, surface).mapNotNull { (group, mouth) -> mouthRecessOf(holder, group.id, mouth, surface, styleOf(work, group).carve) }

/** One shaped mouth laid on its part: its pieces as color shapes on the lip line's patch and its lines as marks. */
private class MouthLanding(
    private val holder: PartId,
    private val group: MarkGroup,
    private val mouth: ShapedMouth,
    private val surface: PartSurface,
    private val marks: Map<MarkId, ResolvedMark>,
    private val bySource: Map<MarkId, List<ResolvedMark>>,
    private val back: List<SurfaceUv>?,
) {
    private val role = group.roles.associateBy { it.role }
    private val flat = mouth.flat
    private val lipMarks = role[MarkRole.LIP_LINE]?.marks.orEmpty()
    private val lipMark = lipMarks.firstNotNullOfOrNull(marks::get)

    fun shapes(): List<LandedShape> {
        val inner = flat.opening?.let { fill(role[MarkRole.INNER]?.fill, it, flat.openingCorners) }
        val slot = role[MarkRole.INNER]?.fill
        val back = back?.takeIf { slot != null && it.size >= MIN_SHAPE_POINTS }?.let {
            LandedShape(RANK_MOUTH, checkNotNull(slot), holder, mouth.patch, ColorShape.Border(it, curved = true), tint = BACK_TINT)
        }
        val within = flat.tongue.filterNot { it.over }.mapNotNull { fill(role[MarkRole.TONGUE]?.fill, it.loop, emptySet()) } +
            flat.teeth.filterNot { it.over }.mapNotNull { tooth(it) }
        val over = flat.tongue.filter { it.over }.mapNotNull { fill(role[MarkRole.TONGUE]?.fill, it.loop, emptySet()) } +
            flat.teeth.filter { it.over }.mapNotNull { tooth(it) }
        val lips = flat.lips.mapNotNull { fill(role[MarkRole.LIP_LINE]?.fill, it, emptySet()) }
        return lips + listOfNotNull(inner, back) + within + over
    }

    private fun tooth(piece: FlatPiece) = fill(role[MarkRole.TEETH]?.fill, piece.loop, piece.loop.indices.toSet())

    private fun fill(slot: SlotName?, loop: List<Vec2>, corners: Set<Int>): LandedShape? {
        if (slot == null || loop.size < MIN_SHAPE_POINTS) return null
        return LandedShape(RANK_MOUTH, slot, holder, mouth.patch, ColorShape.Border(mouth.uv(loop), corners = corners, curved = true))
    }

    fun lines(): List<ResolvedMark> {
        val id = group.id.value
        val upper = if (flat.lipMoved) flat.upper.mapIndexedNotNull { i, piece -> line("$id-upper-lip-$i", piece) } else emptyList()
        val lower = flat.lower.mapIndexedNotNull { i, piece -> line("$id-lower-lip${if (i == 0) "" else "-$i"}", piece) }
        val crease = flat.crease?.let { line("$id-crease", it) }
        fun inked(role: MarkRole, pieces: List<FlatPiece>) =
            if (this.role[role]?.ink == true) pieces.mapIndexedNotNull { i, p -> line("$id-${role.name.lowercase()}-$i", p.loop + p.loop.take(1)) } else emptyList()
        return upper + lower + listOfNotNull(crease) + inked(MarkRole.TONGUE, flat.tongue) + inked(MarkRole.TEETH, flat.teeth)
    }

    fun replaced(): List<MarkId> = if (flat.lipMoved) lipMarks.flatMap { id -> bySource[id].orEmpty().map { it.id } } else emptyList()

    private fun line(id: String, places: List<Vec2>): ResolvedMark? {
        val points = mouth.uv(places).mapNotNull { surface.pointAt(Anchor(holder, mouth.patch, it.u, it.v)) }
        if (points.size < MIN_LINE_POINTS) return null
        return ResolvedMark(MarkId(id), points, lipMark?.width ?: MarkWidth(), ink = true, cameraLocked = false, versions = emptyMap(), slot = lipMark?.slot)
    }
}
