package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val WEIGHT_ID_PREFIX = "weight-"
private const val RING_SEGMENTS = 48
private const val RING_WIDTH = 2f

/** A line weight id [character] holds on none of its parts. */
fun freeWeightId(character: Character): LineWeightId {
    val held = character.parts.flatMapTo(hashSetOf()) { part -> character.resolved(part).surface.lineWeights.map { it.id.value } }
    return generateSequence(1) { it + 1 }.map { "$WEIGHT_ID_PREFIX$it" }.first { it !in held }.let(::LineWeightId)
}

/**
 * What a ring [size] pixels across covers on the part at [hit] through [view], as a fraction of that part's axis length, or null where
 * the part has no length to measure by.
 */
fun weightRadius(character: AssembledCharacter, hit: SurfaceHit, view: SurfaceView, size: Float): Float? {
    val length = character.part(hit.part)?.surface?.axisLength?.takeIf { it > 0f } ?: return null
    val scale = view.pixelsPerUnitAt(hit.position).takeIf { it > 0f } ?: return null
    return size / 2f / scale / length
}

/**
 * Line width: a ring of Size follows the pointer, and a drag across the surface adds Amount to the lines there, from any camera. The
 * weight lies on the surface, so that stretch stays heavier wherever it turns to the silhouette. The drag goes through one gesture on
 * the history, so the lines show it live and it is one undo step; a drag the pane loses restores them.
 */
class LineWidthTool(state: AppState, var ring: Color, var ringWidth: Float) : SurfaceTool(state) {
    private var spot by mutableStateOf<Pair<Any, Offset>?>(null)


    override fun pointerInput(view: View, camera: LockedCamera): Modifier =
        Modifier.pointerInput(this, view) { followSpot(view) }.then(super.pointerInput(view, camera))

    override fun fullInput(view: FullView): Modifier =
        Modifier.pointerInput(this, FULL_PANE) { followSpot(FULL_PANE) }.then(super.fullInput(view))

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = ringAt(view)

    override fun fullOverlay(view: FullView): ViewportOverlay = ringAt(FULL_PANE)

    private suspend fun PointerInputScope.followSpot(pane: Any) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val at = event.changes.firstOrNull()?.position
                    spot = if (event.type == PointerEventType.Exit || at == null) null else pane to at
                }
            }
        } finally {
            if (spot?.first == pane) spot = null
        }
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val assembled = session.assembly.output.value ?: return
        val radius = weightRadius(assembled, hit, sight, state.lines.size) ?: return
        val character = session.document.value.character
        val id = freeWeightId(character)
        val amount = state.lines.amount
        val thisSide = state.thisSide && character.part(hit.part)?.pair != null
        val anchors = arrayListOf(hit.anchor)
        val gesture = session.history.begin()
        fun write() = gesture.update(SetLineWeight(hit.part, id, LineWeight(id, anchors.toList(), radius, amount), thisSide))
        var released = false
        try {
            write()
            released = followSurface(hit, down, sight) { next ->
                anchors += next.anchor
                write()
            }
        } finally {
            if (released) {
                gesture.commit()
                state.lines.picked = PickedWeight(hit.part, id)
            } else {
                gesture.cancel()
            }
        }
    }

    private fun ringAt(pane: Any): ViewportOverlay {
        val (on, at) = spot ?: return ViewportOverlay.Empty
        if (on != pane) return ViewportOverlay.Empty
        val radius = state.lines.size / 2f
        return buildOverlay {
            val path = List(RING_SEGMENTS + 1) { i ->
                val angle = 2.0 * PI * i / RING_SEGMENTS
                at + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
            }
            path.zipWithNext().forEach { (from, to) -> line(from, to, ring, ringWidth) }
        }
    }

    private companion object {
        val FULL_PANE = Any()
    }
}

@Composable
fun rememberLineWidthTool(state: AppState): LineWidthTool {
    val ring = Stretch.colors.ringSelected
    val width = RING_WIDTH * LocalDensity.current.density
    return remember(state) { LineWidthTool(state, ring, width) }.also {
        it.ring = ring
        it.ringWidth = width
    }
}
