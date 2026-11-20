package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.HairOutlineView
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.hairOutlineOn
import kotlin.math.roundToInt

/** How many cells the silhouette is read in along the longer side of an outline's box. */
private const val RESIDUAL_CELLS = 240

/** How far the silhouette is read past the outline each way, as a share of the outline's height. */
private const val RESIDUAL_MARGIN = 0.3f

/** How many places round the outline its distance is read at, per its height. */
private const val PLACES_PER_HEIGHT = 120f

/** The colours a fit's picture draws in, as 0xAARRGGBB. */
private const val PAPER = 0xFFF4F0E8.toInt()
private const val BODY = 0xFFC8C0B4.toInt()
private const val HAIR = 0xFF9A6A48.toInt()
private const val LINE = 0xFFE0287A.toInt()
private const val TIP = 0xFFD01818.toInt()
private const val VALLEY = 0xFF1E5AE6.toInt()

/** How many pixels of the picture a cell of its grid takes. */
private const val PICTURE_SCALE = 3

/** How far one [view]'s outline lies from the hair's silhouette as built, in the head's drawn units: on the whole ([mean]) and at [worst]. */
data class FitResidual(val view: OutlineView, val mean: Float, val worst: Float)

/** A picture [width] by [height] pixels, each 0xAARRGGBB, rows from the top. */
class FitPicture(val view: OutlineView, val width: Int, val height: Int, val pixels: IntArray)

/**
 * How far each view of [head]'s hair outline lies from the silhouette of its hair as [assembled] builds it, locks and all: each view drawn
 * as an orthographic camera sees it, the region where the nearest surface is the head's hair (its lock groups, their bands and parting
 * strip, its inner mass) outlined, and each place round the drawn outline measured to that outline. Empty with no outline.
 */
fun fitResiduals(character: Character, assembled: AssembledCharacter, head: PartId): List<FitResidual> = seen(character, assembled, head).map { seen ->
    val edge = BooleanArray(seen.grid.size) { k -> seen.hair[k] && seen.onEdge(k) }
    val distance = seen.grid.distanceTo(edge)
    val places = seen.outline.resampled(seen.height / PLACES_PER_HEIGHT)
    val far = (seen.grid.width + seen.grid.height) * seen.grid.cell
    val reads = places.map { p -> seen.grid.index(p).let { k -> if (k < 0) seen.grid.beyond(p) + far else distance[k] } }
    FitResidual(seen.view, reads.average().toFloat(), reads.max())
}

/**
 * Each view of [head]'s hair outline drawn over its silhouette as [assembled] builds it, for judging a fit by eye: the hair in brown, the
 * rest of the body in grey, the outline in magenta, its tips red and its valleys blue.
 */
fun fitPictures(character: Character, assembled: AssembledCharacter, head: PartId): List<FitPicture> = seen(character, assembled, head).map { seen ->
    val grid = seen.grid
    val w = grid.width * PICTURE_SCALE
    val h = grid.height * PICTURE_SCALE
    val pixels = IntArray(w * h) { p ->
        val i = (p % w) / PICTURE_SCALE
        val j = grid.height - 1 - (p / w) / PICTURE_SCALE
        val k = j * grid.width + i
        when {
            seen.hair[k] -> HAIR
            seen.raster.tag[k] >= 0 -> BODY
            else -> PAPER
        }
    }
    fun plot(x: Float, y: Float, color: Int, radius: Int) {
        val px = ((x - grid.low.x) / grid.cell * PICTURE_SCALE).roundToInt()
        val py = h - 1 - ((y - grid.low.y) / grid.cell * PICTURE_SCALE).roundToInt()
        for (dy in -radius..radius) for (dx in -radius..radius) {
            val xx = px + dx
            val yy = py + dy
            if (xx in 0 until w && yy in 0 until h) pixels[yy * w + xx] = color
        }
    }
    for (p in seen.outline.resampled(grid.cell / PICTURE_SCALE)) plot(p.x, p.y, LINE, 0)
    for (i in seen.outline.valleys) seen.outline.points[i].let { plot(it.x, it.y, VALLEY, 3) }
    for (i in seen.outline.tips) seen.outline.points[i].let { plot(it.x, it.y, TIP, 3) }
    FitPicture(seen.view, w, h, pixels)
}

/** One view of an outline and what the body shows there: which cells the head's hair is nearest in. */
private class Seen(val view: OutlineView, val outline: FlatOutline, val grid: ViewGrid, val raster: DepthRaster, val hair: BooleanArray) {
    val height: Float = (outline.high.y - outline.low.y).coerceAtLeast(1e-4f)

    /** Whether the cell [k] borders a cell the hair is not nearest in, or the grid's edge. */
    fun onEdge(k: Int): Boolean {
        val i = k % grid.width
        val j = k / grid.width
        return NEIGHBOURS.any { (di, dj) ->
            val ii = i + di
            val jj = j + dj
            ii !in 0 until grid.width || jj !in 0 until grid.height || !hair[jj * grid.width + ii]
        }
    }
}

private fun seen(character: Character, assembled: AssembledCharacter, head: PartId): List<Seen> {
    val outline = character.hairOutlineOn(head) ?: return emptyList()
    val headPart = assembled.part(head) ?: return emptyList()
    val size = HeadView.sizeOf(character, headPart)
    val masses = character.lockGroups.filter { it.on == head }.mapNotNull { it.mass }.toSet()
    val parts = assembled.parts.filter { it.id != null && it.copies == null }
    val isHair = parts.map { isHairOf(it, head, masses) }
    return outline.views.map { drawn -> seenIn(drawn, HeadView.of(headPart, size, drawn.view), parts, isHair) }
}

private fun seenIn(drawn: HairOutlineView, view: HeadView, parts: List<PlacedPart>, isHair: List<Boolean>): Seen {
    val flat = FlatOutline.of(drawn)
    val height = (flat.high.y - flat.low.y).coerceAtLeast(1e-4f)
    val grid = ViewGrid.around(flat.low, flat.high, height * RESIDUAL_MARGIN, RESIDUAL_CELLS)
    val raster = DepthRaster(grid).apply { parts.forEachIndexed { i, part -> draw(part, view, i) } }
    val hair = BooleanArray(grid.size) { raster.tag[it] >= 0 && isHair[raster.tag[it]] }
    return Seen(drawn.view, flat, grid, raster, hair)
}

/** Whether [part] is the hair of [head]: one of its lock groups, a band or parting strip of them, one of its [masses] or other hair on it. */
private fun isHairOf(part: PlacedPart, head: PartId, masses: Set<PartId>): Boolean =
    part.locks?.head == head || part.id in masses || (part.role == Role.HAIR && part.socket?.parent == head)

private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
