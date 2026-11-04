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
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** The width of a stroke in progress, in density independent pixels. */
private const val LIVE_WIDTH = 2f

private const val STROKE_ID_PREFIX = "stroke-"

/** A mark id [character] holds on none of its parts. */
fun freeMarkId(character: Character): MarkId {
    val held = character.parts.flatMapTo(hashSetOf()) { part -> character.resolved(part).surface.marks.map { it.id.value } }
    return generateSequence(1) { it + 1 }.map { "$STROKE_ID_PREFIX$it" }.first { it !in held }.let(::MarkId)
}

/** The command storing [anchors] as a new stroke on the part the first of them names, or null with too few to make one. */
fun strokeCommand(character: Character, anchors: List<Anchor>, thisSide: Boolean): SetMark? {
    if (anchors.size < MIN_MARK_POINTS) return null
    val id = freeMarkId(character)
    val part = anchors.first().part
    return SetMark(part, id, Mark(id, anchors), thisSide && character.part(part)?.pair != null)
}

/**
 * Surface stroke: a drag across the character from any camera lays a line on its skin, stored through one command as anchors and so
 * held by the surface under every later view and redraw. One the pane loses stores nothing.
 */
class SurfaceStrokeTool(state: AppState, var ink: Color, var lineWidth: Float) : SurfaceTool(state) {
    private val live = mutableStateListOf<Vec3>()

    override val showsPreviews: Boolean get() = false

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val anchors = arrayListOf(hit.anchor)
        live += hit.position
        try {
            val released = followSurface(hit, down, sight) { next ->
                anchors += next.anchor
                live += next.position
            }
            if (released) strokeCommand(session.document.value.character, anchors, state.thisSide)?.let(session::send)
        } finally {
            live.clear()
        }
    }

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
