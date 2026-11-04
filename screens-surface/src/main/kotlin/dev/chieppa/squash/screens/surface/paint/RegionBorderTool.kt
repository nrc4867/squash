package dev.chieppa.squash.screens.surface.paint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MIN_BORDER_POINTS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.edit.RegionRef
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** How far apart on screen a border's anchors are taken while drawing it, in pixels. */
private const val TAKE_SPACING = 3f

/** How far a kept anchor's curve may stray from what was drawn, in pixels. */
private const val KEEP_WITHIN = 2.5f

/** How far from a border's point a press takes that point, in pixels. */
private const val GRAB_REACH = 10f

private const val REGION_ID_PREFIX = "border-"

/**
 * Draws a closed border on the surface, however many parts it crosses, as a new region held by the part it starts on. The region picked in the
 * Color drawer shows its points, which drag along the surface.
 */
@Stable
class RegionBorderTool(state: AppState, internal var look: SheetLook) : SurfaceTool(state) {
    private val drawn = mutableStateListOf<SurfaceHit>()

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: PaneSight) {
        val session = state.session.value ?: return
        val slot = state.paint.slotIn(session.document.value.character.palette) ?: return
        drawn.clear()
        drawn += hit
        var last = down.position
        var released = false
        try {
            released = followToRelease(down.id, {}) { pixel ->
                if ((pixel - last).getDistance() < TAKE_SPACING) return@followToRelease
                val next = hitAt(pixel, sight) ?: return@followToRelease
                drawn += crossingBetween(drawn.last(), last, next, pixel, sight)
                last = pixel
                drawn += next
            }
            if (!released) return
            val kept = keptAcross(drawn, sight).map { it.anchor }
            if (kept.size < MIN_BORDER_POINTS) return
            val holder = hit.part
            val id = nextRegionId(session.document.value, holder)
            val thisSide = state.thisSide && session.document.value.character.part(holder)?.pair != null
            session.send(SetRegion(holder, id, Region(id, kept, slot, ink = true), thisSide))
            state.paint.selectedRegion = RegionRef(holder, id)
        } finally {
            drawn.clear()
        }
    }

    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: PaneSight): Boolean {
        val session = state.session.value ?: return false
        val picked = state.paint.selectedRegion ?: return false
        val shown = pointsOf(picked) ?: return false
        val grabbed = shown.indices.firstOrNull { i -> sight.toPixel(shown[i].position)?.let { (it - down.position).getDistance() <= GRAB_REACH } == true } ?: return false
        val character = session.document.value.character
        val holder = character.part(picked.part) ?: return false
        val thisSide = state.thisSide && holder.pair != null
        val region = character.editBase(holder, thisSide).surface.regions.firstOrNull { it.id == picked.id } ?: return false
        if (grabbed !in region.border.indices) return false
        down.consume()
        val gesture: History<SquashDocument>.Transaction = session.history.begin()
        var released = false
        try {
            released = followToRelease(down.id, {}) { pixel ->
                val hit = hitAt(pixel, sight) ?: return@followToRelease
                val border = region.border.toMutableList<Anchor>().also { it[grabbed] = hit.anchor }
                gesture.update(SetRegion(picked.part, picked.id, region.copy(border = border), thisSide))
            }
        } finally {
            if (released) gesture.commit() else gesture.cancel()
        }
        return true
    }

    private fun pointsOf(picked: RegionRef) =
        state.session.value?.bodySurfaceWork?.output?.value?.parts?.get(picked.part)?.regions?.firstOrNull { it.id == picked.id }?.border

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(LockedSight(camera))

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(FullSight(view))

    private fun shown(sight: PaneSight): ViewportOverlay = buildOverlay {
        drawn.mapNotNull { sight.toPixel(it.position) }.zipWithNext().forEach { (a, b) -> line(a, b, look.ring, look.ringWidth) }
        val picked = state.paint.selectedRegion ?: return@buildOverlay
        pointsOf(picked).orEmpty().forEach { point ->
            val at = sight.toPixel(point.position) ?: return@forEach
            dot(at, look.dotRadius + look.dotEdge, look.ring)
            dot(at, look.dotRadius, look.dotFill)
        }
    }
}

/** A region id [part] does not hold yet, numbered from one. */
private fun nextRegionId(document: SquashDocument, part: PartId): RegionId {
    val instance = document.character.part(part)
    val held = instance?.let { document.character.resolved(it).surface.regions.map { region -> region.id } }.orEmpty().toSet()
    return generateSequence(1) { it + 1 }.map { RegionId(REGION_ID_PREFIX + it) }.first { it !in held }
}

/** The places of [drawn] a border keeps, each stretch on one surface thinned alone, so the places either side of a crossing stay. */
private fun keptAcross(drawn: List<SurfaceHit>, sight: PaneSight): List<SurfaceHit> {
    val stretches = ArrayList<MutableList<SurfaceHit>>()
    for (hit in drawn) {
        val last = stretches.lastOrNull()?.last()
        if (last != null && last.part == hit.part && last.patch == hit.patch) stretches.last() += hit else stretches += mutableListOf(hit)
    }
    return stretches.flatMap { stretch -> keptAlong(stretch.map { sight.toPixel(it.position) ?: Offset.Zero }, KEEP_WITHIN).map { stretch[it] } }
}

/** The indices of [points] to keep so a line through them strays from the whole by at most [within]; the first and last stay. */
internal fun keptAlong(points: List<Offset>, within: Float): List<Int> {
    if (points.size <= 2) return points.indices.toList()
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val spans = ArrayDeque(listOf(0 to points.lastIndex))
    while (spans.isNotEmpty()) {
        val (from, to) = spans.removeLast()
        if (to - from < 2) continue
        val farthest = (from + 1 until to).maxBy { distanceFromLine(points[it], points[from], points[to]) }
        if (distanceFromLine(points[farthest], points[from], points[to]) <= within) continue
        keep[farthest] = true
        spans += from to farthest
        spans += farthest to to
    }
    return points.indices.filter { keep[it] }
}

private fun distanceFromLine(point: Offset, a: Offset, b: Offset): Float {
    val along = b - a
    val square = along.x * along.x + along.y * along.y
    if (square == 0f) return (point - a).getDistance()
    val t = (((point - a).x * along.x + (point - a).y * along.y) / square).coerceIn(0f, 1f)
    return (a + along * t - point).getDistance()
}

@Composable
fun rememberRegionBorderTool(state: AppState): RegionBorderTool {
    val look = rememberSheetLook()
    return remember(state) { RegionBorderTool(state, look) }.also { it.look = look }
}
