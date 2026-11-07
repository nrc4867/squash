package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.RibbonPoint
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.squash.screens.AppState
import dev.chieppa.stretch.gpu.CameraView
import kotlin.math.max
import kotlin.math.tan

/** The palette slot a character's lines show when it holds one. */
val INK_SLOT = SlotName("ink")

/** How many pieces a border's ribbon has between two of its anchors. */
private const val BORDER_PIECES = 6

private val EVEN_WIDTH = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f)))

/** What the panes read to show a character's color, all of it derived: its [body] color, its [palette] and its [shading], with its [borders]' ink. */
class PaneColor(val body: BodyColor, val palette: Palette, val shading: ObjectShading, val borders: BodySurfaceWork? = null)

/**
 * [part] as a pane draws it. With [color], its base and layers show their slots at the shading the pane reads, darkened by side seen with the
 * camera at [toCamera]; a part nothing has been placed on, or a character with no color, draws as the look's own. With [posed], the
 * part's mesh in a pose, it draws on that mesh with its layers laid on it, since a layer keeps its place on the patch.
 */
fun SheetLook.colored(part: SheetPart, color: PaneColor?, toCamera: Vec3, outline: Float, opacity: Float = 1f, layer: DepthLayer = DepthLayer.SCENE, posed: RenderMesh? = null): MeshDraw {
    val id = part.id
    if (color == null || id == null) return draw(part, outline, opacity, layer).let { if (posed != null) it.copy(mesh = posed) else it }
    val fallback = this.part.copy(ink = color.palette.colorOf(INK_SLOT) ?: this.part.ink)
    val mesh = posed ?: part.mesh
    val cuts: (PatchCut) -> RenderMesh = if (posed == null) ({ it.mesh }) else ({ part.posedCut(it, posed) })
    return coloredPartDraw(mesh, color.body.parts[id], color.palette, fallback, color.shading, part.side, toCamera, part.model(), outline, cutMesh = cuts).copy(opacity = opacity, layer = layer)
}

/** The ribbon along each border this surface work inks, closed on itself. */
fun ResolvedSurfaceWork.inkedBorders(): List<StrokeRibbon> =
    regions.filter { it.ink }.mapNotNull { StrokeRibbon.along(closedCurve(it.border), markProfile(EVEN_WIDTH)) }

/** Every inked border as a ribbon in the character's ink, or nothing with no [PaneColor]. */
fun SheetLook.borderRibbons(color: PaneColor?): List<RibbonDraw> {
    val work = color?.borders ?: return emptyList()
    val rule = LineRule(ghostOutline)
    val ink = color.palette.colorOf(INK_SLOT) ?: part.ink
    return work.parts.values.flatMap { it.inkedBorders() }.map { RibbonDraw(it, rule, ink) }
}

/** A smooth closed curve through [points], which meets the mesh's own curve through them closely enough for a line drawn on it. */
private fun closedCurve(points: List<ResolvedPoint>): List<RibbonPoint> {
    val count = points.size
    return buildList {
        for (i in 0 until count) {
            val before = points[(i + count - 1) % count].position
            val from = points[i]
            val to = points[(i + 1) % count]
            val after = points[(i + 2) % count].position
            for (piece in 0 until BORDER_PIECES) {
                val t = piece.toFloat() / BORDER_PIECES
                add(RibbonPoint(catmullRom(before, from.position, to.position, after, t), (from.normal * (1f - t) + to.normal * t).normalized()))
            }
        }
        add(first())
    }
}

private fun catmullRom(a: Vec3, b: Vec3, c: Vec3, d: Vec3, t: Float): Vec3 {
    val t2 = t * t
    val t3 = t2 * t
    return (b * 2f + (c - a) * t + (a * 2f - b * 5f + c * 4f - d) * t2 + (b * 3f - a - c * 3f + d) * t3) * 0.5f
}

/** How many pixels a body unit spans at what [camera] looks at, in a view [height] pixels tall. */
fun CameraView.pixelsPerUnit(height: Int): Float = height / (2f * (eye - target).length * tan(fovY / 2f))

/** The most pixels a body unit spans in any of [cameras] and through [full], a view [fullHeight] pixels tall, which is what color flattens for. */
fun largestColorScale(cameras: Collection<LockedCamera>, full: CameraView?, fullHeight: Int): Float =
    max(cameras.maxOfOrNull { it.fit.pixelsPerUnit } ?: 0f, full?.pixelsPerUnit(fullHeight) ?: 0f)

/** What the open character's color reads now, or null with none open. */
@Composable
fun rememberPaneColor(state: AppState): PaneColor? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val document by remember(open) { open.document }.collectAsState()
    val body by remember(open) { open.bodyColor.output }.collectAsState()
    val work by remember(open) { open.bodySurfaceWork.output }.collectAsState()
    val shading = rememberObjectShading(state)
    val shown = body ?: return null
    return PaneColor(shown, document.character.palette, shading, work)
}
