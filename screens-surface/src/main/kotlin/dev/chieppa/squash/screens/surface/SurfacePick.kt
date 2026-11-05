package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.partColorInput
import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.SurfaceView

/** The place on the character under a pixel: which part and patch, its u and v there, and where it lies in body space. */
data class SurfaceHit(val part: PartId, val patch: String, val u: Float, val v: Float, val position: Vec3) {
    val anchor: Anchor get() = Anchor(part, patch, u, v)
}

private const val PATCH_SEPARATOR = ":"

/** How many halvings find the edge of what shows between a pixel off the surface and one on it. */
private const val EDGE_STEPS = 6

/** The name of the template area [anchor] lies in, the last named there as it overdraws the rest, or null outside them all. */
fun areaAt(character: Character, templates: TemplateLibrary, anchor: Anchor): String? =
    partColorInput(character, anchor.part, templates).areas.lastOrNull { area ->
        val onPatch = anchor.patch == area.patch || anchor.patch.startsWith(area.patch + PATCH_SEPARATOR)
        val around = anchor.v in area.v.start..area.vEnd || anchor.v + 1f in area.v.start..area.vEnd
        onPatch && around && anchor.u in area.u.start..area.u.end
    }?.name

/** The nearest place on any placed part of [character] showing at [pixel], or null where none does. */
fun surfaceHit(character: AssembledCharacter, pixel: Offset, sight: SurfaceView): SurfaceHit? {
    var best: SurfaceHit? = null
    var nearest = Float.POSITIVE_INFINITY
    for (part in character.parts) {
        val id = part.id ?: continue
        for (patch in part.surface.patches) {
            hitOn(part, id, patch, pixel, sight, nearest)?.let { (hit, depth) ->
                best = hit
                nearest = depth
            }
        }
    }
    return best
}

/**
 * The place on [character] showing at [pixel], or, where none does, the one showing nearest it within [reach] body units on screen. It is found along
 * the line from [pixel] to the closest of the surface's points, so it lies on what the viewer sees of the surface.
 */
fun surfaceHitWithin(character: AssembledCharacter, pixel: Offset, sight: SurfaceView, reach: Float): SurfaceHit? {
    surfaceHit(character, pixel, sight)?.let { return it }
    var closest: Offset? = null
    var nearest = Float.POSITIVE_INFINITY
    for (part in character.parts) {
        if (part.id == null) continue
        for (patch in part.surface.patches) {
            for (index in 0 until patch.nu * patch.nv) {
                val point = part.origin + part.surface.shown(patch.point(index))
                val shown = sight.toPixel(point) ?: continue
                val distance = (shown - pixel).getDistance()
                if (distance < nearest && distance <= reach * sight.pixelsPerUnitAt(point)) {
                    nearest = distance
                    closest = shown
                }
            }
        }
    }
    var inside = closest ?: return null
    var hit = surfaceHit(character, inside, sight)
    var outside = pixel
    repeat(EDGE_STEPS) {
        val middle = (inside + outside) / 2f
        val there = surfaceHit(character, middle, sight)
        if (there != null) {
            hit = there
            inside = middle
        } else {
            outside = middle
        }
    }
    return hit
}

/** The nearest place of [patch] at [pixel] nearer than [limit], with its depth. */
private fun hitOn(part: PlacedPart, id: PartId, patch: Patch, pixel: Offset, sight: SurfaceView, limit: Float): Pair<SurfaceHit, Float>? {
    val columns = patch.segments
    val projected = arrayOfNulls<Offset>(patch.nu * patch.nv)
    val placed = arrayOfNulls<Vec3>(patch.nu * patch.nv)
    fun index(ring: Int, column: Int) = patch.index(ring, if (patch.closedV) column % patch.nv else column)
    fun at(index: Int): Vec3 = placed[index] ?: (part.origin + part.surface.shown(patch.point(index))).also { placed[index] = it }
    fun screen(index: Int): Offset? = projected[index] ?: sight.toPixel(at(index))?.also { projected[index] = it }
    var found: Pair<SurfaceHit, Float>? = null
    var nearest = limit
    for (ring in 0 until patch.nu - 1) {
        for (column in 0 until columns) {
            val corners = intArrayOf(index(ring, column), index(ring + 1, column), index(ring + 1, column + 1), index(ring, column + 1))
            val a = screen(corners[0]) ?: continue
            val b = screen(corners[1]) ?: continue
            val c = screen(corners[2]) ?: continue
            val d = screen(corners[3]) ?: continue
            if (pixel.x < minOf(a.x, b.x, c.x, d.x) || pixel.x > maxOf(a.x, b.x, c.x, d.x) || pixel.y < minOf(a.y, b.y, c.y, d.y) || pixel.y > maxOf(a.y, b.y, c.y, d.y)) continue
            for (triangle in CELL_TRIANGLES) {
                val p = corners[triangle[0]]
                val q = corners[triangle[1]]
                val r = corners[triangle[2]]
                val weights = barycentric(pixel, screen(p) ?: continue, screen(q) ?: continue, screen(r) ?: continue) ?: continue
                val depth = sight.depthOf(at(p)) * weights[0] + sight.depthOf(at(q)) * weights[1] + sight.depthOf(at(r)) * weights[2]
                if (depth >= nearest) continue
                nearest = depth
                val gridU = ring + CELL_US[triangle[0]] * weights[0] + CELL_US[triangle[1]] * weights[1] + CELL_US[triangle[2]] * weights[2]
                val gridV = column + CELL_VS[triangle[0]] * weights[0] + CELL_VS[triangle[1]] * weights[1] + CELL_VS[triangle[2]] * weights[2]
                val position = at(p) * weights[0] + at(q) * weights[1] + at(r) * weights[2]
                found = SurfaceHit(id, patch.name, uAt(patch, gridU), (gridV / columns).let { if (it >= 1f && patch.closedV) it - 1f else it }, position) to depth
            }
        }
    }
    return found
}

/** A cell's two triangles, by corner: the rings' low and high and the columns' low and high, in the order [hitOn] lists them. */
private val CELL_TRIANGLES = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3))
private val CELL_US = floatArrayOf(0f, 1f, 1f, 0f)
private val CELL_VS = floatArrayOf(0f, 0f, 1f, 1f)

private fun uAt(patch: Patch, gridU: Float): Float {
    val rings = patch.ringUs
    val ring = gridU.toInt().coerceIn(0, rings.lastIndex - 1)
    return rings[ring] + (gridU - ring).coerceIn(0f, 1f) * (rings[ring + 1] - rings[ring])
}

/** How much of each corner [point] takes in the triangle [a], [b], [c] on screen, or null outside it or for a triangle with no area. */
private fun barycentric(point: Offset, a: Offset, b: Offset, c: Offset): FloatArray? {
    val area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    if (area == 0f) return null
    val second = ((point.x - a.x) * (c.y - a.y) - (point.y - a.y) * (c.x - a.x)) / area
    val third = ((b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)) / area
    val first = 1f - second - third
    return if (first < 0f || second < 0f || third < 0f) null else floatArrayOf(first, second, third)
}
