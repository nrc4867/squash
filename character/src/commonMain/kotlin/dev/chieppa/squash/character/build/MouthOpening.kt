package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.MouthLine
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.mouth.FlatPiece
import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.character.mouth.mouthRecessOf
import dev.chieppa.squash.character.surface.LayerTint
import dev.chieppa.squash.character.surface.TintTarget
import dev.chieppa.squash.character.mouth.ShapedMouth
import dev.chieppa.squash.character.mouth.mouthMarks
import dev.chieppa.squash.character.mouth.shapeMouth
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import kotlin.math.max

/** The rank an open mouth's fills paint at: over every region and stroke of its part. */
internal const val RANK_MOUTH = 4

private const val MIN_SHAPE_POINTS = 3

/** How deep into a carved mouth, a share of the way from the lips, its back starts, which shows darker than the walls near the lips. */
private const val BACK_DEPTH = 0.5f

/** How far the back of a carved mouth's inside is taken toward the ink, so it reads darker than the walls near the lips. */
internal val BACK_TINT = LayerTint(TintTarget.Ink, 0.35f)
private const val MIN_LINE_POINTS = 2

/**
 * What a shaped mouth adds to its part: fills in layer order, ink lines, the lip line marks those lines stand in for, and the lip regions
 * its moved lips' fills stand in for.
 */
data class OpenMouths(
    val shapes: List<LandedShape>,
    val lines: List<ResolvedMark>,
    val replaced: Set<MarkId> = emptySet(),
    val regions: Set<RegionId> = emptySet(),
) {
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
            val back = recessOf(holder, work, group, mouth, surface)?.solid?.deeperThan(BACK_DEPTH)
            MouthLanding(holder, group, mouth, surface, marks, bySource, back, lipRegionsOf(work, group))
        }
    }
    if (shaped.isEmpty()) return OpenMouths.NONE
    return OpenMouths(
        shaped.flatMap { it.shapes() },
        shaped.flatMap { it.lines() },
        shaped.flatMap { it.replaced() }.toSet(),
        shaped.flatMap { it.replacedRegions() }.toSet(),
    )
}

/**
 * Every mouth group [holder]'s [work] holds, shaped on its [surface] in the style [work] names for it (plain lips where none); one an open
 * jaw splits along parted at least to its slit by its lips alone, any inner outline drawn left out, with no teeth.
 */
internal fun shapedMouths(holder: PartId, work: ResolvedSurfaceWork, surface: PartSurface): List<Pair<MarkGroup, ShapedMouth>> {
    val bySource = work.marks.groupBy { it.source }
    return work.groups.filter { it.isMouth }.mapNotNull { group ->
        val regions = lipRegionsOf(work, group).map { region -> region.border.map { it.anchor } }
        val drawn = group.mouthMarks({ id -> bySource[id].orEmpty().map { mark -> mark.points.map { it.anchor } } }, regions)
        val style = styleOf(work, group)
        val jaw = group.id in work.jaws
        val placed = if (jaw) drawn.withoutInner() else drawn
        val dentition = if (jaw) MouthDentition.NONE else work.dentitions[group.id] ?: MouthDentition.NONE
        fun shaped(controls: MarkControls) = shapeMouth(holder, placed, controls, style, surface, dentition)
        val mouth = shaped(group.controls) ?: return@mapNotNull null
        if (!jaw) return@mapNotNull group to mouth
        val slit = slitOpen(holder, mouth, style, surface)
        val open = if (lipsPart(style)) slit else max(slit, PAINTED_OPEN * work.jaws.getValue(group.id))
        if (open <= group.controls.open) group to mouth else group to (shaped(group.controls.copy(open = open)) ?: mouth)
    }
}

/** How open a jaw at its limit paints a mouth whose lips it does not part on the surface. */
private const val PAINTED_OPEN = 0.85f

/**
 * Whether a jaw parts a mouth in [style] on the surface: a muzzle's, whose line runs back along the jaw; not lips across the front of a
 * face, whose opening is painted and whose jaw takes the face below them.
 */
private fun lipsPart(style: MouthStyle) = style.line != MouthLine.FRONT

/** The recess [group] of [holder]'s [work], shaped as [mouth], cuts into [surface], or a jaw's pocket where an open jaw splits along it. */
private fun recessOf(holder: PartId, work: ResolvedSurfaceWork, group: MarkGroup, mouth: ShapedMouth, surface: PartSurface): MouthRecess? {
    val style = styleOf(work, group)
    return mouthRecessOf(holder, group.id, mouth, surface, style.carve, group.id in work.jaws, lipsPart(style))
}

/**
 * The open that parts [mouth] to its jaw's slit: its lips [SLIT_DROP] of the straight distance between its corners apart at its middle,
 * whatever length of line its style lays that over.
 */
private fun slitOpen(holder: PartId, mouth: ShapedMouth, style: MouthStyle, surface: PartSurface): Float {
    val line = mouth.flat.upper.flatten()
    if (line.size < MIN_LINE_POINTS) return 0f
    val first = line.minBy { it.x }
    val last = line.maxBy { it.x }
    val width = last.x - first.x
    val ends = mouth.uv(listOf(first, last)).mapNotNull { surface.pointAt(Anchor(holder, mouth.patch, it.u, it.v))?.position }
    if (ends.size < 2 || width <= 0f) return 0f
    return (SLIT_DROP * (ends[1] - ends[0]).length / (style.depth * width)).coerceIn(0f, 1f)
}

/** How far apart a jaw's slit parts the lips at their middle, as a share of the straight distance between the mouth's corners. */
private const val SLIT_DROP = 0.25f

/** The regions [work] paints in [group]'s lip line fill: its lips' color as drawn. */
fun lipRegionsOf(work: ResolvedSurfaceWork, group: MarkGroup): List<ResolvedRegion> {
    val slot = group.roles.firstOrNull { it.role == MarkRole.LIP_LINE }?.fill ?: return emptyList()
    return work.regions.filter { it.slot == slot }
}

private fun styleOf(work: ResolvedSurfaceWork, group: MarkGroup): MouthStyle = work.mouthStyles[group.id] ?: MouthStyle.PLAIN

/**
 * The recess each mouth [holder] holds cuts into it where its lips part, as its style carves it, or as a jaw's pocket for a mouth its
 * open jaw splits along; none for a painted style or a mouth at rest. See HLD: look, Mouth.
 */
fun mouthRecesses(holder: PartId, work: ResolvedSurfaceWork, surface: PartSurface): List<MouthRecess> =
    shapedMouths(holder, work, surface).mapNotNull { (group, mouth) -> recessOf(holder, work, group, mouth, surface) }

/** One shaped mouth laid on its part: its pieces as color shapes on the lip line's patch and its lines as marks. */
private class MouthLanding(
    private val holder: PartId,
    private val group: MarkGroup,
    private val mouth: ShapedMouth,
    private val surface: PartSurface,
    private val marks: Map<MarkId, ResolvedMark>,
    private val bySource: Map<MarkId, List<ResolvedMark>>,
    private val back: List<SurfaceUv>?,
    private val lipRegions: List<ResolvedRegion>,
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
        val lipSlot = role[MarkRole.LIP_LINE]?.fill
        val lips = if (flat.lipsDrawn) {
            flat.lips.mapIndexedNotNull { i, loop -> lipRegions.getOrNull(i)?.let { fill(lipSlot, loop, it.corners, it.curved) } }
        } else {
            flat.lips.mapNotNull { fill(lipSlot, it, emptySet()) }
        }
        return lips + listOfNotNull(inner, back) + within + over
    }

    /** The lip regions the moved lips' fills stand in for. */
    fun replacedRegions(): List<RegionId> =
        if (flat.lipsDrawn) lipRegions.filterIndexed { i, _ -> flat.lips.getOrNull(i).orEmpty().size >= MIN_SHAPE_POINTS }.map { it.id } else emptyList()

    private fun tooth(piece: FlatPiece) = fill(role[MarkRole.TEETH]?.fill, piece.loop, piece.loop.indices.toSet())

    private fun fill(slot: SlotName?, loop: List<Vec2>, corners: Set<Int>, curved: Boolean = true): LandedShape? {
        if (slot == null || loop.size < MIN_SHAPE_POINTS) return null
        return LandedShape(RANK_MOUTH, slot, holder, mouth.patch, ColorShape.Border(mouth.uv(loop), corners = corners, curved = curved))
    }

    fun lines(): List<ResolvedMark> {
        val id = group.id.value
        val upper = if (flat.lipMoved) mouth.upperUv().mapIndexedNotNull { i, piece -> lineOn("$id-upper-lip-$i", piece) } else emptyList()
        val lower = flat.lower.mapIndexedNotNull { i, piece -> line("$id-lower-lip${if (i == 0) "" else "-$i"}", piece) }
        val crease = flat.crease?.let { line("$id-crease", it) }
        fun inked(role: MarkRole, pieces: List<FlatPiece>) =
            if (this.role[role]?.let { it.ink && it.fill != null } == true) pieces.mapIndexedNotNull { i, p -> line("$id-${role.name.lowercase()}-$i", p.loop + p.loop.take(1)) } else emptyList()
        return upper + lower + listOfNotNull(crease) + inked(MarkRole.TONGUE, flat.tongue) + inked(MarkRole.TEETH, flat.teeth)
    }

    fun replaced(): List<MarkId> = if (flat.lipMoved) lipMarks.flatMap { id -> bySource[id].orEmpty().map { it.id } } else emptyList()

    private fun line(id: String, places: List<Vec2>): ResolvedMark? = lineOn(id, mouth.uv(places))

    private fun lineOn(id: String, places: List<SurfaceUv>): ResolvedMark? {
        val points = places.mapNotNull { surface.pointAt(Anchor(holder, mouth.patch, it.u, it.v)) }
        if (points.size < MIN_LINE_POINTS) return null
        return ResolvedMark(MarkId(id), points, lipMark?.width ?: MarkWidth(), ink = true, cameraLocked = false, versions = emptyMap(), slot = lipMark?.slot)
    }
}
