package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.ROW_NARROW
import dev.chieppa.squash.character.mouth.ROW_SHORTEN
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.SlotSide
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

/** How far into its jaw a tooth's root sinks, a share of its width, so the body hides its root but a long tooth's never leaves the jaw's far side. */
private const val SINK = 0.5f

/** How far in from the lip line a tooth's middle stands, past half its own depth, a share of the mouth's width. */
private const val LIP_INSET = 0.012f

/** How far in from the lip line a tooth flagged over stands, a share of its own depth, so it stands past the lip. */
private const val OVER_INSET = 0.25f

/** How far each row behind a jaw's first steps inward, a share of the mouth's width. */
private const val ROW_STEP = 0.045f

/** How far inside the lip's surface a tooth hidden shut keeps its front, a share of its half-width, and how near its tip may come. */
private const val LIP_CLEAR = 0.6f
private const val TIP_CLEAR = 0.2f

/** How many places along a tooth its clearance from the lip in front of it is read at, root to tip. */
private const val CLEAR_PLACES = 8

/** The furthest a tooth is moved in from its place at the lip, a share of its width. */
private const val CLEAR_MOST = 1f

/** The least of its length a tooth hidden shut keeps where the lip in front of it curves back over its tip. */
private const val LEAST_KEPT = 0.5f

/** How much of the head's depth over a tooth, shut, it may reach into: a tooth hidden shut, and one flagged over, which shows past the lip. */
private const val HIDDEN_ROOM = 0.6f
private const val OVER_ROOM = 0.9f

/**
 * This tooth, hidden shut, kept inside the lip in front of it: moved in where the lip curves back, as along a muzzle's sides, never past
 * the head's middle plane [axis] through [hinge] nor further than [CLEAR_MOST] of its width; then, where the lip still curves back over
 * it, as round a muzzle's tip, cut short to where the lip still covers it. A tooth flagged over keeps its place at the lip.
 */
private fun ToothPlace.inside(room: HeadRoom, over: Boolean, hinge: Vec3, axis: Vec3): ToothPlace {
    if (over) return this
    val half = width / 2f
    val need = half * (1f + LIP_CLEAR)
    fun clearAt(from: Vec3, share: Float) = room.clearFrom(from + along * (length * share), depth * -1f, half / 2f)
    val toMiddle = ((root - hinge) dot axis).let { side -> if ((depth dot axis) * side < 0f) kotlin.math.abs(side) / maxOf(kotlin.math.abs(depth dot axis), 1e-3f) else Float.MAX_VALUE }
    val most = minOf(width * CLEAR_MOST, maxOf(0f, toMiddle - half))
    val short = (0..CLEAR_PLACES).maxOf { k -> clearAt(root, k.toFloat() / CLEAR_PLACES).let { if (it > 0f) need - it else 0f } }
    val at = root + depth * short.coerceIn(0f, most)
    var kept = length
    for (k in 1..CLEAR_PLACES) {
        val share = k.toFloat() / CLEAR_PLACES
        if (clearAt(at, share) < half * TIP_CLEAR) {
            kept = maxOf(length * LEAST_KEPT, length * (k - 1) / CLEAR_PLACES)
            break
        }
    }
    return ToothPlace(at, along, across, depth, width, kept, sink, hook * kept / length, hookWay, inner)
}

/** This tooth held short of the far side of the head shut over it, as [room] reads it. */
private fun ToothPlace.heldTo(room: HeadRoom, over: Boolean): ToothPlace {
    val most = room.depthFrom(root, along, width / 2f) * (if (over) OVER_ROOM else HIDDEN_ROOM)
    if (most <= 0f || length <= most) return this
    return ToothPlace(root, along, across, depth, width, most, sink, hook * most / length, hookWay, inner)
}

/** How far an over tooth reaches past its type's length, a share of that length, so it clears the other jaw's lip shut. */
private const val OVER_REACH = 0.35f

/**
 * Each tooth of [dentition]'s jaws at shaped as its own patch, laid along [path] from the middle outward slot by slot as the painted levels
 * lay them, each side mirrored unless a slot names one: the upper row hanging from the palate along the snout's rim, the lower standing on
 * the jaw's floor along its rim, built turned open with the jaw; back rows step inward, narrower and shorter. Each tooth's root is sunk
 * into its jaw so the body hides it, and a tooth flagged over stands at the lip and reaches past it. With [room], each tooth is held short
 * of the far side of the head shut over it, so shut the rows hide behind the lips and only an over tooth shows. See HLD: look, Teeth.
 */
fun shapedTeeth(dentition: MouthDentition, path: LipPath, room: HeadRoom? = null): List<Patch> = JawName.entries.flatMap { name ->
    val jaw = dentition.jaw(name)
    if (jaw.level != TeethLevel.SHAPED) return@flatMap emptyList()
    jaw.rows.withIndex().flatMap { (r, row) ->
        val middles = dentition.middles(row.slots)
        val narrow = 1f - ROW_NARROW * r
        val shorten = 1f - ROW_SHORTEN * r
        listOf(1, -1).flatMap { sign ->
            row.slots.withIndex().mapNotNull { (s, slot) ->
                val onSide = slot.side == SlotSide.BOTH || (slot.side == SlotSide.LEFT) == (sign > 0)
                val distance = middles[s] * path.width * narrow
                if (!onSide || distance > path.reach(sign)) return@mapNotNull null
                val tooth = dentition.toothOf(slot)
                val width = tooth.width * path.width * narrow
                val length = tooth.length * path.width * shorten * (if (slot.over) 1f + OVER_REACH else 1f)
                val laid = placeOf(path, name, sign, distance, width, length, r, slot.over, tooth.curve) ?: return@mapNotNull null
                val place = room?.let { laid.heldTo(it, slot.over).inside(it, slot.over, path.cut.hinge, path.cut.axis) } ?: laid
                val open = if (name == JawName.LOWER) path.cut::open else { p: Vec3 -> p }
                val (rings, us) = toothRings(tooth.tip, place, open)
                ringPatch(toothPatchName(name, r, s, if (sign > 0) "l" else "r"), rings, us, boneU = 1f)
            }
        }
    }
}

/** Where a tooth [width] wide and [length] long of row [row] on the side [sign] of [jaw] stands, [distance] along [path], as it lies shut. */
private fun placeOf(path: LipPath, jaw: JawName, sign: Int, distance: Float, width: Float, length: Float, row: Int, over: Boolean, curve: Float): ToothPlace? {
    val place = path.placeAt(sign, distance) ?: return null
    val lip = place.point
    val way = place.way
    val turning = path.turning(lip)
    val down = if (jaw == JawName.UPPER) turning * -1f else turning
    val inward = unitOr((place.out * -1f).without(way).without(turning), path.cut.forward * -1f)
    val depth = width / 2f
    val inset = if (over) depth * OVER_INSET else depth + LIP_INSET * path.width + ROW_STEP * path.width * row
    val root = lip + inward * inset
    return ToothPlace(
        root = root,
        along = down,
        across = way,
        depth = inward,
        width = width,
        length = length,
        sink = width * SINK,
        hook = curve * length,
        hookWay = way * -1f,
        inner = -1f,
    )
}
