package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.densified
import dev.chieppa.squash.character.shape.strokeCurve
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentEdge
import dev.chieppa.squash.document.SurfaceCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceTool
import dev.chieppa.squash.screens.surface.keptAcross
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** How far apart on screen a stroke's places are taken while drawing it, in pixels. */
private const val TAKE_SPACING = 3f

/** How far a kept place's line may stray from what was drawn, in pixels, with no smoothing; full smoothing quadruples it. */
private const val KEEP_WITHIN = 2f
private const val SMOOTH_REACH = 3f

/** How much of the way to the pointer a stroke's next place goes at full stabilizer. */
private const val STABILIZE = 0.8f

/** How far the pointer may travel, in pixels, for a gesture to count as a press rather than a stroke. */
private const val PRESS_TRAVEL = 6f

/** How near its start, in pixels, a stroke must end to close where it ends. */
private const val MEETS = 18f

/**
 * A Dress tool that draws on the body's surface for the picked garment: a drag lays a stroke, a press does what [pressedAt] says, and
 * the garment's edges show, the picked one's points dragging along the surface.
 */
abstract class GarmentEdgeTool(state: AppState, internal var look: SheetLook) : SurfaceTool(state) {
    private val drawn = mutableStateListOf<SurfaceHit>()

    /** Takes a press at [hit] on [garment] worn over [body]. */
    abstract fun pressedAt(hit: SurfaceHit, garment: Garment, character: Character, body: AssembledCharacter)

    /** Takes a stroke through [points] on [garment], closing where it began when [meets]. */
    abstract fun stroked(points: List<Anchor>, meets: Boolean, garment: Garment, character: Character, body: AssembledCharacter)

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val character = session.document.value.character
        val garment = state.dress.garment.value?.let(character.wardrobe::garment) ?: return
        val body = session.assembly.output.value ?: return
        drawn.clear()
        drawn += hit
        var held = down.position
        var travel = 0f
        val stabilizer = state.curves.stabilizer
        try {
            val released = followToRelease(down.id, {}) { pixel ->
                travel = maxOf(travel, (pixel - down.position).getDistance())
                val next = held + (pixel - held) * (1f - stabilizer * STABILIZE)
                if ((next - held).getDistance() < TAKE_SPACING) return@followToRelease
                val there = hitAt(next, sight) ?: return@followToRelease
                drawn += crossingBetween(drawn.last(), held, there, next, sight)
                held = next
                drawn += there
            }
            if (!released) return
            if (travel < PRESS_TRAVEL) {
                pressedAt(hit, garment, character, body)
                return
            }
            val within = KEEP_WITHIN * (1f + state.curves.smooth * SMOOTH_REACH)
            val points = keptAcross(drawn, sight, within).map { it.anchor }
            if (points.size < 2) return
            stroked(points, (held - down.position).getDistance() < MEETS, garment, character, body)
        } finally {
            drawn.clear()
        }
    }

    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean =
        dragEdgePoint(state, down, sight, pickedOnly = true)

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        drawn.mapNotNull { sight.toPixel(it.position) }.zipWithNext().forEach { (a, b) -> line(a, b, look.ring, look.ringWidth) }
        edges(state, look, sight)
    }
}

/**
 * Edge: a drag across the body lays an edge of the picked garment on its surface, closing where it ends where it began or round the
 * far side of a limb it runs across, on both sides of the middle with Mirror; a press fills the side of the edges pressed, or flips
 * it (see [GarmentShape.pressed]).
 */
@Stable
class EdgeTool(state: AppState, look: SheetLook) : GarmentEdgeTool(state, look) {
    override fun pressedAt(hit: SurfaceHit, garment: Garment, character: Character, body: AssembledCharacter) {
        state.session.value?.send(character.fillCommand(body, garment, hit.anchor))
    }

    override fun stroked(points: List<Anchor>, meets: Boolean, garment: Garment, character: Character, body: AssembledCharacter) {
        state.session.value?.send(character.edgeCommand(body, garment, strokeCurve(points, meets, mirrorsOn(character, points.first().part))))
        state.dress.edge.value = garment.edges.size
    }
}

/**
 * Hole: a press on a part where it meets the garment draws a closed edge round its root, and a second press a cuff band beside it,
 * the band's cloth filled; a loop drawn anywhere cuts a hole of its shape. The cloth's fill is kept as places, out of each new hole.
 */
@Stable
class HoleTool(state: AppState, look: SheetLook) : GarmentEdgeTool(state, look) {
    override fun pressedAt(hit: SurfaceHit, garment: Garment, character: Character, body: AssembledCharacter) {
        val command = character.rootHoleCommand(body, garment, hit.anchor) ?: return
        state.session.value?.send(command)
        state.dress.edge.value = garment.edges.size
    }

    override fun stroked(points: List<Anchor>, meets: Boolean, garment: Garment, character: Character, body: AssembledCharacter) {
        val command = character.holeCommand(body, garment, GarmentEdge(densified(SurfaceCurve(points, closed = true)), hole = true)) ?: return
        state.session.value?.send(command)
        state.dress.edge.value = garment.edges.size
    }
}

@Composable
fun rememberEdgeTool(state: AppState): EdgeTool {
    val look = rememberSheetLook()
    return remember(state) { EdgeTool(state, look) }.also { it.look = look }
}

@Composable
fun rememberHoleTool(state: AppState): HoleTool {
    val look = rememberSheetLook()
    return remember(state) { HoleTool(state, look) }.also { it.look = look }
}
