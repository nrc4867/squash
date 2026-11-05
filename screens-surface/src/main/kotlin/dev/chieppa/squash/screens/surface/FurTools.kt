package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DEFAULT_COMB_REACH
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.Grain
import dev.chieppa.squash.document.GrainComb
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetGrain
import dev.chieppa.squash.document.Ulid
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import kotlin.math.round

/** How often a drag lays a row of strokes, and how far apart across it, as shares of the part's spacing. */
private const val ROW_STEP_SHARE = 0.75f
private const val ACROSS_STEP_SHARE = 0.75f

/** How many strokes either side of the drag's line a row reaches. */
private const val ROW_REACH = 2

/** Pixels of drag between a comb's places. */
private const val COMB_STEP_PIXELS = 18f

/** A comb replaces those already within this share of its reach. */
private const val COMB_REPLACES_WITHIN = 0.5f

/** How far past the surface a comb still takes the nearest place on it, in body units: about the longest tuft the fur rule's Length allows, twice over. */
private const val COMB_REACH_UNITS = 0.35f

/** A comb's drag on the surface smaller than this says nothing about which way, in u and v. */
private const val MIN_COMB_STEP = 1e-4f

/** The width of a fur tool's drag in progress, in density independent pixels. */
private const val LIVE_WIDTH = 2f

/** A surface tool that traces its drag on the skin as it goes. */
abstract class SurfaceDragTool(state: AppState, var ink: Color, var lineWidth: Float) : SurfaceTool(state) {
    protected val live = mutableStateListOf<Vec3>()

    override val takesFreeCamera: Boolean get() = true

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        live.mapNotNull { sight.toPixel(it) }.zipWithNext().forEach { (from, to) -> line(from, to, ink, lineWidth) }
    }

    /** Whether an edit to [part] lands on this side alone. */
    protected fun onThisSide(character: Character, part: PartId): Boolean = state.thisSide && character.part(part)?.pair != null
}

/**
 * Fur stroke: a drag lays rows of strokes on the part under its start, rooted on the surface from any camera, as one step on release.
 * A row falls every so often along the drag at the part's spacing, so the strokes stored are denser than a view shows and any camera
 * finds enough near its silhouette. A drag the pane loses lays nothing.
 */
class FurStrokeTool(state: AppState, ink: Color, lineWidth: Float) : SurfaceDragTool(state, ink, lineWidth) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val part = hit.part
        val character = session.document.value.character
        val spacing = (character.resolved(part)?.surface?.fur ?: FurRule()).spacingPixels
        val step = spacing * ROW_STEP_SHARE
        val laid = arrayListOf(furMark(hit))
        var last = down.position
        live += hit.position
        try {
            val released = followSurface(hit, down, sight) { next ->
                live += next.position
                val pixel = sight.toPixel(next.position) ?: return@followSurface
                while ((pixel - last).getDistance() >= step) {
                    val along = pixel - last
                    val row = last + along / along.getDistance() * step
                    laid += rowAcross(sight, part, last, row, spacing)
                    last = row
                }
            }
            if (released) session.send(AddMarks(part, laid, onThisSide(character, part)))
        } finally {
            live.clear()
        }
    }

    private fun furMark(hit: SurfaceHit) = Mark(MarkId(Ulid.next()), listOf(hit.anchor), kind = MarkKind.FUR)

    /** A row across the drag from [from] to [to], its strokes rooted where their pixels land on [part]. A pixel off it lays nothing. */
    private fun rowAcross(sight: SurfaceView, part: PartId, from: Offset, to: Offset, spacing: Float): List<Mark> {
        val along = to - from
        val across = Offset(-along.y, along.x) / along.getDistance() * (spacing * ACROSS_STEP_SHARE)
        return (-ROW_REACH..ROW_REACH).mapNotNull { step -> hitAt(to + across * step.toFloat(), sight)?.takeIf { it.part == part }?.let(::furMark) }
    }
}

/**
 * Comb: a drag lays the part's grain along it, anchored as vectors in u and v, on the part under its start. A press or drag on the fur past the
 * body, or in the space around it, combs the nearest place on the surface. Fur follows it as the drag goes, as previews of one step, which release
 * commits and a lost drag takes back.
 */
class CombTool(state: AppState, ink: Color, lineWidth: Float) : SurfaceDragTool(state, ink, lineWidth) {
    override fun hitAt(pixel: Offset, sight: SurfaceView): SurfaceHit? =
        state.session.value?.assembly?.output?.value?.let { surfaceHitWithin(it, pixel, sight, COMB_REACH_UNITS) }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val character = session.document.value.character
        val instance = character.part(hit.part) ?: return
        val side = onThisSide(character, hit.part)
        val held = character.editBase(instance, side).surface.grain?.combs.orEmpty()
        val combs = ArrayList<GrainComb>()
        var from = hit
        var pixel = down.position
        var kept = false
        val gesture = session.history.begin()
        live += hit.position
        try {
            val released = followSurface(hit, down, sight) { next ->
                live += next.position
                val at = sight.toPixel(next.position) ?: return@followSurface
                if (next.part != hit.part || (at - pixel).getDistance() < COMB_STEP_PIXELS) return@followSurface
                pixel = at
                val before = from.takeIf { it.patch == next.patch }
                from = next
                val comb = before?.let { combBetween(it, next) } ?: return@followSurface
                combs += comb
                gesture.update(SetGrain(hit.part, Grain(mergedCombs(held, combs)), side))
            }
            kept = released && combs.isNotEmpty()
        } finally {
            live.clear()
            if (kept) gesture.commit() else gesture.cancel()
        }
    }
}

/** The comb a drag from [from] to [to] sets at [to], or null when it barely moved over the surface. */
internal fun combBetween(from: SurfaceHit, to: SurfaceHit): GrainComb? {
    val du = to.u - from.u
    val around = to.v - from.v
    val dv = around - round(around)
    if (du * du + dv * dv < MIN_COMB_STEP * MIN_COMB_STEP) return null
    return GrainComb(to.patch, to.u, to.v, du, dv, DEFAULT_COMB_REACH)
}

/** [held] without any close to one of [added], then [added] in the order drawn, so combing a place again replaces what was there. */
internal fun mergedCombs(held: List<GrainComb>, added: List<GrainComb>): List<GrainComb> =
    held.filter { old -> added.none { new -> old.patch == new.patch && closeTo(old, new) } } + added

private fun closeTo(a: GrainComb, b: GrainComb): Boolean {
    val du = a.u - b.u
    val around = a.v - b.v
    val dv = around - round(around)
    val within = b.reach * COMB_REPLACES_WITHIN
    return du * du + dv * dv < within * within
}

/** The Fur stroke tool for [state], kept across compositions. */
@Composable
fun rememberFurStrokeTool(state: AppState): FurStrokeTool {
    val width = LIVE_WIDTH * LocalDensity.current.density
    return remember(state) { FurStrokeTool(state, PART_INK, width) }.also { it.lineWidth = width }
}

/** The Comb tool for [state], kept across compositions. */
@Composable
fun rememberCombTool(state: AppState): CombTool {
    val width = LIVE_WIDTH * LocalDensity.current.density
    return remember(state) { CombTool(state, PART_INK, width) }.also { it.lineWidth = width }
}
