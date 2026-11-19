package dev.chieppa.squash.character.copies

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.CopyLayout
import dev.chieppa.squash.character.model.CopySpec
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3

/**
 * One copy of a part: its mesh's offsets from the part's origin taken through [turn], a rotation and a scale, then moved to [origin],
 * in body space; [slot] is the palette slot its base shows, or null for the part's own.
 */
class CopyPlace(val origin: Vec3, val turn: Mat3, val slot: SlotName?) {
    /** A point of the part's own mesh, as an offset from its origin, where this copy shows it. */
    fun placed(offset: Vec3): Vec3 = origin + turn * offset
}

/**
 * What a part's copies are placed from, kept so a pose places them again on its parent as posed: the [spec] of the socket it sits on,
 * the part's own [surface] at rest, whose mesh every copy shows, [definition], the part's own axes as its mesh shows them, [mirrored]
 * for a right side, [flipV] where that side reads its parent's v mirrored, [depth], how far its template sinks into a parent, [looped]
 * for a closed ring, whose copies along a path link through each other, the [shades] its copies pick among besides its own base,
 * and over an area the [picks] made at rest.
 */
class CopyRule internal constructor(
    val spec: CopySpec,
    val surface: PartSurface,
    internal val definition: Mat3,
    internal val mirrored: Boolean,
    internal val flipV: Boolean,
    internal val depth: Float,
    internal val looped: Boolean,
    val shades: List<SlotName>,
    internal val picks: List<AreaPick>,
)

/** A part's copies: the [rule] they follow and each one's [places]. */
class PartCopies(val rule: CopyRule, val places: List<CopyPlace>) {
    /** Where every copy reaches, or null with none. */
    val box: BodyBox? by lazy {
        val own = rule.surface.box ?: return@lazy null
        places.map { place ->
            val corners = (0 until CORNERS).map { i ->
                place.placed(Vec3(if (i and 1 == 0) own.low.x else own.high.x, if (i and 2 == 0) own.low.y else own.high.y, if (i and 4 == 0) own.low.z else own.high.z))
            }
            BodyBox(Vec3(corners.minOf { it.x }, corners.minOf { it.y }, corners.minOf { it.z }), Vec3(corners.maxOf { it.x }, corners.maxOf { it.y }, corners.maxOf { it.z }))
        }.reduceOrNull(BodyBox::union)
    }

    /** These copies placed again on [parent], as a pose shows it: the same picks, read on its surface there. */
    fun on(parent: PlacedPart): PartCopies = PartCopies(rule, placesOn(rule, parent))

    private companion object {
        const val CORNERS = 8
    }
}

/**
 * The copies [spec] lays of a part built as [surface], with [axes], on [parent] at rest: over an area picked by its seed, or along its
 * path. See [CopyRule] for the rest.
 */
fun copiesOf(
    spec: CopySpec,
    parent: PlacedPart,
    surface: PartSurface,
    axes: PartAxes,
    mirrored: Boolean,
    flipV: Boolean,
    depth: Float,
    looped: Boolean,
    shades: List<SlotName>,
): PartCopies {
    val shown = if (mirrored) Mat3(axes.side.mirrored, axes.up.mirrored, axes.along.mirrored).let { frameOf(it.z, it.y, true) } else frameOf(axes.along, axes.up, false)
    val picks = if (spec.layout == CopyLayout.AREA) AreaCopies.pick(spec, parent, flipV, shades.size) else emptyList()
    val rule = CopyRule(spec, surface, shown, mirrored, flipV, depth, looped, shades, picks)
    return PartCopies(rule, placesOn(rule, parent))
}

/** Where [rule]'s copies lie on [parent]. */
internal fun placesOn(rule: CopyRule, parent: PlacedPart): List<CopyPlace> = when (rule.spec.layout) {
    CopyLayout.AREA -> AreaCopies.place(rule, parent)
    CopyLayout.PATH -> PathCopies.place(rule, parent)
}

/** The slot of shade [index] among [shades]: 0 the part's own base, null. */
internal fun shadeSlot(shades: List<SlotName>, index: Int): SlotName? = if (index <= 0) null else shades.getOrNull(index - 1)
