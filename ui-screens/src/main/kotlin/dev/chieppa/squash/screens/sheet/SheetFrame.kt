package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RenderMesh
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.Mat4
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Above this, a pane's up direction counts as the world's, and the ground lies level in it. */
private const val LEVEL_DOT = 0.99f
private const val RING_SEGMENTS = 64
private const val RING_MARGIN = 1.1f

/** The ground's ellipse under a part, as multiples of how far the part reaches across and up the pane. */
private const val SIDE_GROUND_ACROSS = 2.8f
private const val SIDE_GROUND_UP = 0.47f
private const val FRONT_GROUND_ACROSS = 0.86f
private const val FRONT_GROUND_UP = 0.1f
private const val FULL_GROUND_RADIUS = 1.6f

/** What one frame of the panes shows; [sheetFrame] turns it into draws. */
class SheetInput(
    val rects: List<PixelRect>,
    val width: Int,
    val height: Int,
    val look: SheetLook,
    val selection: SheetSelection? = null,
    val cameras: Map<View, LockedCamera> = emptyMap(),
    val full: CameraView? = null,
    val active: View? = null,
    val toolOverlays: Map<View, ViewportOverlay> = emptyMap(),
)

/**
 * One immutable frame of the panes for the render thread. Full always shows the part; a locked pane shows it filled once
 * its view is drawn and as the mesh's dashed outline until then. A pane with no part stays empty.
 */
fun sheetFrame(input: SheetInput): SceneFrame {
    val views = SheetPane.entries.mapNotNull { sceneView(it, input) }
    val dividers = buildOverlay { sheetDividers(input.rects, input.width, input.height).forEach { rect(it.toRect(), input.look.divider) } }
    val panes = SheetPane.entries.map { paneOverlay(it, input).clippedTo(input.rects[it.ordinal].toRect()) }
    return SceneFrame(input.look.well, views, panes.fold(dividers, ViewportOverlay::plus))
}

private fun sceneView(pane: SheetPane, input: SheetInput): SceneView? {
    val selection = input.selection ?: return null
    val rect = input.rects[pane.ordinal]
    val part = selection.part
    val look = input.look
    val view = pane.view
    if (view == null) {
        val camera = input.full ?: return null
        val draws = listOf(fullGround(part, look), MeshDraw(part.mesh, look.part, outlinePixels = look.fullOutline))
        return SceneView(rect, camera.viewProjection(rect.width, rect.height), draws)
    }
    val camera = input.cameras[view] ?: return null
    val draws = buildList {
        lockedGround(camera, part, look)?.let(::add)
        if (view in selection.outlines) add(MeshDraw(part.mesh, look.part, outlinePixels = look.lockedOutline))
    }
    return SceneView(rect, camera.viewProjection(), draws)
}

/** The ground as a flat ellipse behind the part, in a pane whose up is the world's and which so looks along the ground. */
private fun lockedGround(camera: LockedCamera, part: SheetPart, look: SheetLook): MeshDraw? {
    val basis = camera.basis
    if ((basis.screenUp dot Vec3.Y) < LEVEL_DOT) return null
    val (x, y) = part.extents.spansIn(camera.view)
    val (across, tall) = when (camera.view) {
        View.SIDE -> SIDE_GROUND_ACROSS to SIDE_GROUND_UP
        View.FRONT -> FRONT_GROUND_ACROSS to FRONT_GROUND_UP
        View.TOP -> return null
    }
    val behind = (camera.fit.center dot basis.back) - camera.eyeDistance / 2f
    val middle = basis.xAxis * x.center + basis.yAxis * y.min + basis.back * behind
    val model = placedDisc(middle, basis.screenRight * (x.length * across), basis.screenUp * (y.length * tall), basis.back)
    return MeshDraw(RenderMesh.UnitDisc, PartMaterial(look.ground), model)
}

private fun fullGround(part: SheetPart, look: SheetLook): MeshDraw {
    val radius = part.radius * FULL_GROUND_RADIUS
    val model = placedDisc(part.floor, Vec3.X * radius, Vec3(0f, 0f, -radius), Vec3.Y)
    return MeshDraw(RenderMesh.UnitDisc, PartMaterial(look.ground), model)
}

/** The matrix taking the unit disc's x, y and facing z to [x], [y] and [facing], at [at]. */
private fun placedDisc(at: Vec3, x: Vec3, y: Vec3, facing: Vec3): Mat4 = Mat4.of(
    x.x, x.y, x.z, 0f,
    y.x, y.y, y.z, 0f,
    facing.x, facing.y, facing.z, 0f,
    at.x, at.y, at.z, 1f,
)

private fun paneOverlay(pane: SheetPane, input: SheetInput): ViewportOverlay {
    val rect = input.rects[pane.ordinal]
    val selection = input.selection ?: return ViewportOverlay.Empty
    val inPane = when (val view = pane.view) {
        null -> fullRing(selection.part, input, rect)
        else -> {
            val camera = input.cameras[view] ?: return ViewportOverlay.Empty
            lockedOverlay(view, camera, selection, input) + (input.toolOverlays[view] ?: ViewportOverlay.Empty)
        }
    }
    return inPane.translated(Offset(rect.x.toFloat(), rect.y.toFloat()))
}

private fun lockedOverlay(view: View, camera: LockedCamera, selection: SheetSelection, input: SheetInput): ViewportOverlay = buildOverlay {
    val look = input.look
    if (view !in selection.outlines) {
        selection.part.silhouettes.getValue(view).forEach { path ->
            dashed(path.map(camera::toPixel), look.template, look.templateWidth, look.templateDash, look.templateGap)
        }
    }
    guideLines(paneGuides(camera, sideExtents(selection.outlines[View.SIDE], selection.part.extents)), camera, look)
    if (view == input.active) {
        val inset = look.activeWidth / 2f
        outline(Rect(inset, inset, camera.width - inset, camera.height - inset), look.active, look.activeWidth)
    }
}

private fun OverlayBuilder.guideLines(guides: PaneGuides, camera: LockedCamera, look: SheetLook) {
    val right = camera.width.toFloat()
    val bottom = camera.height.toFloat()
    guides.verticals.forEach { dashed(listOf(Offset(it, 0f), Offset(it, bottom)), look.guide, look.guideWidth, look.guideDash, look.guideDash) }
    guides.horizontals.forEach { dashed(listOf(Offset(0f, it), Offset(right, it)), look.guide, look.guideWidth, look.guideDash, look.guideDash) }
}

/** The dashed ring around the selected part in Full, in pane pixels. */
private fun fullRing(part: SheetPart, input: SheetInput, rect: PixelRect): ViewportOverlay {
    val camera = input.full ?: return ViewportOverlay.Empty
    val look = input.look
    val target = part.center.toGpu()
    val center = camera.project(target, rect.width, rect.height) ?: return ViewportOverlay.Empty
    val across = (camera.up cross (camera.eye - target)).normalized()
    val edge = camera.project(target + across * part.radius, rect.width, rect.height) ?: return ViewportOverlay.Empty
    val radius = (edge - center).getDistance() * RING_MARGIN
    return buildOverlay {
        val path = (0..RING_SEGMENTS).map {
            val angle = 2.0 * PI * it / RING_SEGMENTS
            center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
        }
        dashed(path, look.ring, look.ringWidth, look.ringDash, look.ringGap)
    }
}

internal fun PixelRect.toRect() = Rect(x.toFloat(), y.toFloat(), right.toFloat(), bottom.toFloat())
