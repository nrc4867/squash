package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.lines.PickedMark
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** How near a stroke's point a press takes it, in density independent pixels. */
private const val GRAB_REACH = 10f

/** Half a point's handle, in density independent pixels. */
private const val HANDLE_SIZE = 3f

/** Where [anchor] lies on [character], or null where its part or patch is not there. */
internal fun AssembledCharacter.positionOf(anchor: Anchor): Vec3? {
    val part = part(anchor.part) ?: return null
    val patch = part.surface.patches.firstOrNull { it.name == anchor.patch } ?: return null
    return part.origin + part.surface.shown(patch.positionAt(anchor.u, anchor.v))
}

/**
 * Lines' Edit curves: the picked stroke's points show as handles, and a press near a point of any stroke picks that stroke and drags the point over
 * the surface, as one undo step. A floating stroke shows the point it leaves the skin from.
 */
class LineEditTool(state: AppState, private val reach: Float, var color: Color, private val size: Float) : SurfaceTool(state) {
    override val showsWhole: Boolean get() = true

    private fun points(stroke: StrokeRow): List<Vec3?> {
        val character = state.session.value?.assembly?.output?.value ?: return emptyList()
        return stroke.mark.points.map(character::positionOf)
    }

    private fun strokes(): List<StrokeRow> {
        val session = state.session.value ?: return emptyList()
        return markRows(session.document.value.character, state.templates, session.templates).strokes
    }

    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val session = state.session.value ?: return false
        val rows = strokes()
        val picked = state.lines.pickedMark
        val ordered = rows.sortedByDescending { PickedMark(it.part, it.mark.id) == picked }
        val (row, index) = ordered.firstNotNullOfOrNull { row ->
            points(row).withIndex()
                .mapNotNull { (i, at) -> at?.let(sight::toPixel)?.let { i to (it - down.position).getDistance() } }
                .filter { it.second <= reach }
                .minByOrNull { it.second }
                ?.let { row to it.first }
        } ?: return false
        down.consume()
        state.lines.pickedMark = PickedMark(row.part, row.mark.id)
        val character = session.document.value.character
        val part = character.part(row.part) ?: return true
        val thisSide = state.thisSide && part.pair != null
        val base = character.editBase(part, thisSide).surface.marks.firstOrNull { it.id == row.mark.id } ?: row.mark
        val gesture = session.history.begin()
        var moved = false
        val released = followToRelease(down.id, {}) { pixel ->
            val hit = hitAt(pixel, sight) ?: return@followToRelease
            val points = base.points.toMutableList().also { it[index] = hit.anchor }
            gesture.update(SetMark(row.part, row.mark.id, base.copy(points = points), thisSide))
            moved = true
        }
        if (released && moved) gesture.commit() else gesture.cancel()
        return true
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        val picked = state.lines.pickedMark ?: return@buildOverlay
        val row = strokes().firstOrNull { PickedMark(it.part, it.mark.id) == picked } ?: return@buildOverlay
        points(row).mapNotNull { it?.let(sight::toPixel) }.forEach { at -> line(at + Offset(-size, 0f), at + Offset(size, 0f), color, size) }
    }
}

@Composable
fun rememberLineEditTool(state: AppState): LineEditTool {
    val density = LocalDensity.current.density
    val accent = Stretch.colors.accent
    return remember(state, density) { LineEditTool(state, GRAB_REACH * density, accent, HANDLE_SIZE * density) }.also { it.color = accent }
}
