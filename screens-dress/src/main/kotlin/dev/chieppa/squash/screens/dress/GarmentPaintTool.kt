package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isSecondaryPress
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.surface.PaintShape
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceTool
import dev.chieppa.squash.screens.surface.outlineOf
import dev.chieppa.squash.screens.surface.stampsAlong
import dev.chieppa.squash.screens.surface.strokeOf
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val RING_POINTS = 48
private const val TWO_PI = 6.2831855f

/**
 * Paint: a drag lays cloth of the picked garment on the body, into its picked area, in the shape its options pick (see [PaintShape]);
 * with Unpaint on, Alt held or the secondary button it takes cloth away instead, and with Shift held it lays a straight line. A brush or
 * a line keeps off the parts above the one it starts on till it clearly goes onto them (see [BrushReach]). The
 * garment's edges show, and the cloth arrives through the graph one stroke at a time, the last sent waiting for the graph to settle.
 */
@Stable
class GarmentPaintTool(state: AppState, private val scope: CoroutineScope, internal var look: SheetLook) : SurfaceTool(state) {
    private var ring by mutableStateOf<Pair<Vec3, Float>?>(null)
    private val outline = mutableStateListOf<Offset>()

    override fun AwaitPointerEventScope.takesPress(down: PointerInputChange): Boolean = isToolPress(down) || isSecondaryPress(down)

    /** How a stroke is laid: unpainting when the Unpaint switch and the [held] modifier or secondary button disagree. */
    fun styleFor(held: Boolean): PaintStyle {
        val character = state.session.value?.document?.value?.character
        val mirrored = if (state.mirror.value && character != null) middlePartsOf(character) else emptySet()
        return PaintStyle(erase = state.dress.unpaint.value != held, clean = state.dress.clean.value, mirrored = mirrored)
    }

    /** The command that lays [stroke] on the picked garment as [style] has it, into the picked area, or null with none picked. */
    internal fun commandFor(picked: PickedGarment, stroke: SurfaceMask, style: PaintStyle): DocumentCommand =
        picked.character.paintCommand(picked.body, picked.garment, state.dress.area.value, stroke, style)

    /** Lays [stroke] on the picked garment as one step, as a drag does with Alt or the secondary button [held] or not. */
    fun lay(stroke: SurfaceMask, held: Boolean = false) {
        val picked = state.pickedGarment() ?: return
        state.session.value?.send(commandFor(picked, stroke, styleFor(held)))
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val picked = state.pickedGarment() ?: return
        val session = state.session.value ?: return
        val secondary = isSecondaryPress(down)
        val keys = currentEvent.keyboardModifiers
        val style = styleFor(secondary || keys.isAltPressed)
        val shape = if (keys.isShiftPressed) PaintShape.LINE else state.dress.shape.value
        val places = chartPlaces(picked.character, picked.body)
        val radius = state.dress.brushSize.value
        val pixelRadius = radius * sight.pixelsPerUnitAt(hit.position)
        val live = LivePaint(session, scope) { stroke -> commandFor(picked, stroke, style) }
        val path = ArrayList<Offset>()
        val reach = BrushReach(places, hit.part)
        var stroke = reach.dab(hit.position, hit.part, radius)
        ring = hit.position to pixelRadius
        if (!shape.outlined) live.update(stroke)
        var released = false
        try {
            released = if (shape == PaintShape.BRUSH) {
                followSurface(hit, down, sight, secondary = secondary) { next ->
                    stroke += reach.dab(next.position, next.part, radius)
                    ring = next.position to pixelRadius
                    live.update(stroke)
                }
            } else {
                followToRelease(down.id, {}, secondary) { pixel ->
                    path += pixel
                    if (shape.outlined) {
                        outline.clear()
                        outline += outlineOf(shape, down.position, path)
                        return@followToRelease
                    }
                    hitAt(pixel, sight)?.let { ring = it.position to pixelRadius }
                    val dabs = stampsAlong(down.position, pixel, pixelRadius).mapNotNull { hitAt(it, sight) }.map { it.position to it.part }
                    stroke = BrushReach(places, hit.part).stroke(dabs, radius)
                    live.update(stroke)
                }
            }
            if (released && shape.outlined) live.update(strokeOf(shape, places, sight, down.position, path, radius, pixelRadius) { hitAt(it, sight)?.position })
        } finally {
            ring = null
            outline.clear()
            live.finish(released)
        }
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        edges(state, look, sight)
        ring?.let { (at, radius) ->
            val center = sight.toPixel(at) ?: return@let
            val path = List(RING_POINTS + 1) { i ->
                val angle = TWO_PI * i / RING_POINTS
                center + Offset(cos(angle), sin(angle)) * radius
            }
            path.zipWithNext().forEach { (a, b) -> line(a, b, look.ring, look.guideWidth) }
        }
        if (outline.size > 1) (outline + outline.first()).zipWithNext().forEach { (a, b) -> line(a, b, look.ring, look.guideWidth) }
    }
}

/** A paint stroke being laid, open on the session's history until [finish]: [command] makes each stroke so far into a step. */
private class LivePaint(private val session: CharacterSession, private val scope: CoroutineScope, private val command: (SurfaceMask) -> DocumentCommand) {
    private val gesture: History<SquashDocument>.Transaction = session.history.begin()
    private var waiting: Job? = null
    private var latest: SurfaceMask? = null
    private var sent: SurfaceMask? = null

    fun update(stroke: SurfaceMask) {
        latest = stroke
        if (waiting != null) return
        send()
        waiting = scope.launch {
            session.graph.settled.first { it }
            waiting = null
            if (latest !== sent) latest?.let(::update)
        }
    }

    private fun send() {
        val stroke = latest ?: return
        sent = stroke
        gesture.update(command(stroke))
    }

    fun finish(keep: Boolean) {
        waiting?.cancel()
        if (!keep || latest == null) {
            gesture.cancel()
            return
        }
        if (latest !== sent) send()
        gesture.commit()
    }
}

/**
 * Hole: a press on a part where it meets the picked garment's cloth rings its root, letting it out, and a second press adds a cuff band
 * while Cuff on second press is on (see [holeCommand]).
 */
@Stable
class GarmentHoleTool(state: AppState, internal var look: SheetLook) : SurfaceTool(state) {
    /** Takes a press at [hit] on the picked garment. */
    fun pressedAt(hit: SurfaceHit) {
        val picked = state.pickedGarment() ?: return
        val command = picked.character.holeCommand(picked.body, picked.garment, hit.anchor, state.dress.cuffOnSecondPress.value) ?: return
        state.session.value?.send(command)
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        if (followToRelease(down.id, {}) {}) pressedAt(hit)
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = buildOverlay { edges(state, look, camera) }

    override fun fullOverlay(view: FullView): ViewportOverlay = buildOverlay { edges(state, look, view) }
}

@Composable
fun rememberGarmentPaintTool(state: AppState): GarmentPaintTool {
    val look = rememberSheetLook()
    val scope = rememberCoroutineScope()
    return remember(state) { GarmentPaintTool(state, scope, look) }.also { it.look = look }
}

@Composable
fun rememberGarmentHoleTool(state: AppState): GarmentHoleTool {
    val look = rememberSheetLook()
    return remember(state) { GarmentHoleTool(state, look) }.also { it.look = look }
}
