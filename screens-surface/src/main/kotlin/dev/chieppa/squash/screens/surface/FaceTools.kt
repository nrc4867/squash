package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.bodyThrough
import dev.chieppa.squash.screens.surface.eyes.PaintedEyeHandles
import dev.chieppa.squash.screens.surface.eyes.paintsEyes
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** How close a press takes a mark's place or line, in density independent pixels. */
private const val REACH = 10f

private const val LIVE_WIDTH = 2f
private const val HANDLE_SIZE = 4f
private const val TARGET_RADIUS = 9f
private const val TARGET_ARM = 13f
private const val RING_STEPS = 24

/**
 * A tool working on the picked mark group and role, on the head. Every face tool names in each pane the version a stroke there writes; while Face
 * works on painted eyes it shows the picked eye's box and handles instead, in [selected] on [paper], and takes presses on them first.
 */
abstract class FaceTool(state: AppState, var accent: Color, protected val density: Float) : SurfaceTool(state) {
    var selected: Color = Color.Black
    var paper: Color = Color.White
    private val eyeHandles = PaintedEyeHandles(state)

    private val paintsEyes: Boolean get() = state.session.value != null && state.paintsEyes()

    /** Takes a press near what the tool itself shows, ahead of the surface under it; true when it took it. */
    protected open suspend fun AwaitPointerEventScope.pressedOwn(down: PointerInputChange, sight: SurfaceView): Boolean = false

    final override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean =
        with(eyeHandles) { pressed(down, sight, density) { hitAt(it, sight)?.anchor } } || pressedOwn(down, sight)
    protected val picked: PickedGroup? get() = state.face.group
    protected val role: MarkRole? get() = state.face.role

    /** The placed head as [sight]'s pane shows it, posed or at rest: the part holding the picked group, else the part the Face stage works on. */
    protected fun head(sight: SurfaceView): PlacedPart? = headIn(state.bodyThrough(sight))

    private fun headIn(body: AssembledCharacter?): PlacedPart? {
        val session = state.session.value ?: return null
        val part = picked?.part ?: session.document.value.character.faceHead(session.templates) ?: return null
        return body?.part(part)
    }

    /** The version the tool works in through [sight]: the one picked, else the one the view shows. */
    protected fun binThrough(sight: SurfaceView): CameraBin? = state.face.version ?: head(sight)?.let { part ->
        FACE_BINS.binFor(headAngle(part, sight.toViewer(part.box?.center ?: part.origin)))
    }

    /** Where the places of what the role draws in [bin] show in body space, on the head as [sight]'s pane shows it. */
    protected fun placesIn(bin: CameraBin, sight: SurfaceView): List<Vec3> {
        val session = state.session.value ?: return emptyList()
        val character = session.document.value.character
        val group = picked?.let(character::groupOf) ?: return emptyList()
        val at = picked?.part ?: return emptyList()
        val part = head(sight) ?: return emptyList()
        val mark = role?.let { character.markIn(group, it, at) } ?: return emptyList()
        return (mark.versions[bin]?.points ?: mark.points).mapNotNull { anchor ->
            part.surface.patches.firstOrNull { it.name == anchor.patch }?.let { part.origin + part.surface.shown(it.positionAt(anchor.u, anchor.v)) }
        }
    }

    protected fun SurfaceView.pixelsOf(places: List<Vec3>): List<Offset?> = places.map(::toPixel)

    /** What the tool itself draws through [sight]. */
    protected open fun OverlayBuilder.drawn(sight: SurfaceView) = Unit

    final override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    final override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        drawn(sight)
        with(eyeHandles) { drawn(sight, selected, paper, density) }
        if (state.face.hairOutline) hairOutline(sight)
    }

    /** The face's head's hair outline, over a pane looking along one of its views. */
    private fun OverlayBuilder.hairOutline(sight: SurfaceView) {
        val session = state.session.value ?: return
        val character = session.document.value.character
        val id = character.faceHead(session.templates) ?: return
        val head = state.bodyThrough(sight)?.part(id) ?: return
        hairOutlineDrawn(sight, character, head, density, accent, accent, selected)
    }

    override val paneNote: @Composable (SurfaceView) -> Unit = { sight -> if (!paintsEyes) binThrough(sight)?.let { FaceVersionChip(it) } }
}

/** Draw mark: a drag across the picked group's part draws what the picked role draws, in the version shown, in the expression shown. */
class DrawMarkTool(state: AppState, var ink: Color, accent: Color, density: Float) : FaceTool(state, accent, density) {
    private val live = mutableStateListOf<Vec3>()

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val group = picked ?: return
        val drawing = role ?: return
        if (hit.part != group.part) return
        val bin = binThrough(sight) ?: return
        val anchors = arrayListOf(hit.anchor)
        live += hit.position
        try {
            val released = followSurface(hit, down, sight) { next ->
                if (next.part == group.part && next.anchor.patch == hit.anchor.patch) {
                    anchors += next.anchor
                    live += next.position
                }
            }
            if (released) drawnMarkCommand(session.document.value.character, group, drawing, anchors, bin)?.let(session::send)
        } finally {
            live.clear()
        }
    }

    override fun OverlayBuilder.drawn(sight: SurfaceView) {
        live.mapNotNull { sight.toPixel(it) }.zipWithNext().forEach { (from, to) -> line(from, to, ink, LIVE_WIDTH * density) }
    }
}

/** Edit curves on a mark: the picked role's places show as handles in the version shown, and a drag moves one over the surface as one undo step. */
class EditMarkTool(state: AppState, accent: Color, density: Float) : FaceTool(state, accent, density) {
    private val reach get() = REACH * density

    override suspend fun AwaitPointerEventScope.pressedOwn(down: PointerInputChange, sight: SurfaceView): Boolean {
        val session = state.session.value ?: return false
        val group = picked ?: return false
        val editing = role ?: return false
        val bin = binThrough(sight) ?: return false
        val index = sight.pixelsOf(placesIn(bin, sight)).withIndex()
            .mapNotNull { (i, pixel) -> pixel?.let { i to (it - down.position).getDistance() } }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first ?: return false
        down.consume()
        val gesture = session.history.begin()
        val released = followToRelease(down.id, {}) { pixel ->
            val hit = hitAt(pixel, sight)?.takeIf { it.part == group.part } ?: return@followToRelease
            movedPlaceCommand(session.document.value.character, group, editing, bin, index, hit.anchor)?.let(gesture::update)
        }
        if (released) gesture.commit() else gesture.cancel()
        return true
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit

    override fun OverlayBuilder.drawn(sight: SurfaceView) {
        val bin = binThrough(sight) ?: return
        val half = HANDLE_SIZE * density
        sight.pixelsOf(placesIn(bin, sight)).filterNotNull().forEach { at ->
            line(at + Offset(-half, 0f), at + Offset(half, 0f), accent, half)
        }
    }
}

/** Erase on a mark: a press on what the picked role draws takes it from the version shown. */
class EraseMarkTool(state: AppState, accent: Color, density: Float) : FaceTool(state, accent, density) {
    private val reach get() = REACH * density

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val group = picked ?: return
        val erasing = role ?: return
        val bin = binThrough(sight) ?: return
        val near = sight.pixelsOf(placesIn(bin, sight)).filterNotNull().any { (it - down.position).getDistance() <= reach }
        if (near) erasedMarkCommand(session.document.value.character, group, erasing, bin)?.let(session::send)
    }
}

@Composable
fun rememberDrawMarkTool(state: AppState): DrawMarkTool = rememberFaceTool(state) { accent, density -> DrawMarkTool(state, PART_INK, accent, density) }

@Composable
fun rememberEditMarkTool(state: AppState): EditMarkTool = rememberFaceTool(state) { accent, density -> EditMarkTool(state, accent, density) }

@Composable
fun rememberEraseMarkTool(state: AppState): EraseMarkTool = rememberFaceTool(state) { accent, density -> EraseMarkTool(state, accent, density) }

@Composable
private fun <T : FaceTool> rememberFaceTool(state: AppState, make: (Color, Float) -> T): T {
    val density = LocalDensity.current.density
    val accent = Stretch.colors.accent
    val selected = Stretch.colors.ringSelected
    val paper = Stretch.colors.surfaceFloat
    return remember(state, density) { make(accent, density) }.also {
        it.accent = accent
        it.selected = selected
        it.paper = paper
    }
}
