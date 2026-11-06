package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MIN_MARK_POINTS
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkFlight
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.character.build.frameAt
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.penPressure
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** The width of a stroke in progress, in density independent pixels. */
private const val LIVE_WIDTH = 2f

private const val STROKE_ID_PREFIX = "stroke-"

/** How far the pointer moves on screen before a floating stroke takes another place, in pixels. Its ribbon curves between them. */
private const val FLIGHT_SPACING = 8f

/** A mark id [character] holds on none of its parts. */
fun freeMarkId(character: Character): MarkId {
    val held = character.parts.flatMapTo(hashSetOf()) { part -> character.resolved(part).surface.marks.map { it.id.value } }
    return generateSequence(1) { it + 1 }.map { "$STROKE_ID_PREFIX$it" }.first { it !in held }.let(::MarkId)
}

/** How a stroke drawn now looks: its [width], whether it is [mirrored] across the body's middle, and its [slot], the ink's if none. */
data class StrokeLook(val width: MarkWidth = MarkWidth(), val mirrored: Boolean = false, val slot: SlotName? = null)

/** The command storing [anchors] as a new stroke on the part the first of them names, or null with too few to make one. */
fun strokeCommand(character: Character, anchors: List<Anchor>, thisSide: Boolean, look: StrokeLook = StrokeLook()): SetMark? {
    if (anchors.size < MIN_MARK_POINTS) return null
    val id = freeMarkId(character)
    val part = anchors.first().part
    return SetMark(part, id, Mark(id, anchors, look.width, mirrored = look.mirrored, slot = look.slot), thisSide && character.part(part)?.pair != null)
}

/** The command storing a new floating stroke from [root] through [flight], or null when it goes nowhere. */
fun floatingCommand(character: Character, root: Anchor, flight: List<MarkFlight>, thisSide: Boolean, look: StrokeLook = StrokeLook()): SetMark? {
    if (flight.isEmpty()) return null
    val id = freeMarkId(character)
    val mark = Mark(id, listOf(root), look.width, kind = MarkKind.FLOATING, flight = flight, mirrored = look.mirrored, slot = look.slot)
    return SetMark(root.part, id, mark, thisSide && character.part(root.part)?.pair != null)
}

/**
 * Stroke: a drag across the character from any camera lays a line on its skin, stored through one command as anchors and so held by the surface
 * under every later view and redraw. With [dev.chieppa.squash.screens.lines.LineEditState.floating] on, it draws a stroke that starts on the skin and
 * leaves it, such as a whisker, on the plane through its start that faces the viewer, the line held from there as the surface turns. A pen's pressure
 * sets its width. One the pane loses stores nothing.
 */
class SurfaceStrokeTool(state: AppState, var ink: Color, var lineWidth: Float) : SurfaceTool(state) {
    private val live = mutableStateListOf<Vec3>()

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val character = session.document.value.character
        var pressure = penPressure(down)
        val pressures = arrayListOf(pressure)
        live += hit.position
        try {
            if (state.lines.floating) {
                val frame = session.assembly.output.value?.part(hit.part)?.surface?.frameAt(hit.anchor) ?: return
                var last = down.position
                val flight = ArrayList<MarkFlight>()
                val released = followToRelease(down.id, { pressure = penPressure(it) }) { pixel ->
                    if ((pixel - last).getDistance() < FLIGHT_SPACING) return@followToRelease
                    val point = pointFacingViewer(hit.position, pixel, sight) ?: return@followToRelease
                    last = pixel
                    live += point
                    pressures += pressure
                    flight += frame.flightTo(hit.position, point)
                }
                if (released) floatingCommand(character, hit.anchor, flight, state.thisSide, lookOf(character, hit, pressures))?.let(session::send)
            } else {
                val anchors = arrayListOf(hit.anchor)
                val released = followSurface(hit, down, sight, { pressure = penPressure(it) }) { next ->
                    anchors += next.anchor
                    live += next.position
                    pressures += pressure
                }
                if (released) strokeCommand(character, anchors, state.thisSide, lookOf(character, hit, pressures))?.let(session::send)
            }
        } finally {
            live.clear()
        }
    }

    private fun lookOf(character: Character, hit: SurfaceHit, pressures: List<Float?>) =
        StrokeLook(pressureWidth(pressures), mirrorsOn(character, hit.part), state.lines.strokeSlot)

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        live.mapNotNull { sight.toPixel(it) }.zipWithNext().forEach { (from, to) -> line(from, to, ink, lineWidth) }
    }
}

@Composable
fun rememberSurfaceStrokeTool(state: AppState): SurfaceStrokeTool {
    val width = LIVE_WIDTH * LocalDensity.current.density
    return remember(state) { SurfaceStrokeTool(state, PART_INK, width) }.also { it.lineWidth = width }
}
