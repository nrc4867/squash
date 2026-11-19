package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.PosedSurface
import kotlinx.coroutines.flow.MutableStateFlow
import dev.chieppa.squash.character.build.PartColor
import dev.chieppa.squash.character.surface.isFieldRun
import dev.chieppa.squash.character.surface.separatePieces
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
import dev.chieppa.squash.render.MeshInstance
import dev.chieppa.squash.character.copies.PartCopies
import dev.chieppa.stretch.gpu.Mat4
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

/** How strongly a garment shown ghosted fills. */
private const val SEE_THROUGH_OPACITY = 0.18f

private val EVEN_WIDTH = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f)))

/**
 * What the panes read to show a character's color, all of it derived: its [body] color, its [palette] and its [shading], with its
 * and its one [surface].
 */
class PaneColor(val body: BodyColor, val palette: Palette, val shading: ObjectShading, val surface: PaneSurface)

/**
 * [part] as a pane draws it. With [color], its base and layers show their slots at the shading the pane reads, darkened by side seen with the
 * camera at [toCamera]; a part nothing has been placed on, or a character with no color, draws as the look's own. With [posed], the
 * part as a pose shows it, it draws on that part's mesh with its layers laid on its patches, since a layer keeps its place on the patch. A part seen
 * through draws faint in the look's own fill with its outline. A part [color]'s one surface holds draws only its pieces that are not
 * runs, null with none; the surface draws the rest. A part of its own whose base slot has an opacity draws at that share of [opacity].
 */
fun SheetLook.colored(part: SheetPart, color: PaneColor?, toCamera: Vec3, outline: Float, opacity: Float = 1f, layer: DepthLayer = DepthLayer.SCENE, posed: PlacedPart? = null): MeshDraw? {
    val copies = (posed ?: part.placedPart).copies
    if (copies != null) return copiesDraw(part, copies, color, toCamera, outline, opacity, layer)
    val mesh = posed?.surface?.mesh ?: part.mesh
    if (part.seeThrough) return draw(part, ghostOutline, opacity * SEE_THROUGH_OPACITY, layer).copy(mesh = mesh)
    val id = part.id
    if (color == null || id == null) return draw(part, outline, opacity, layer).copy(mesh = mesh)
    val fallback = this.part.copy(ink = color.palette.colorOf(INK_SLOT) ?: this.part.ink)
    val cuts: (PatchCut) -> RenderMesh = if (posed == null) ({ it.mesh }) else ({ part.posedCut(it, posed.surface) })
    if (color.holds(part)) {
        val placed = posed ?: part.placedPart
        val pieces = separatePieces(placed) ?: return null
        val shown = placed.surface.patches.filterNot { isFieldRun(placed, it) }.mapTo(HashSet()) { it.name }
        val own = color.body.parts[id]?.let { PartColor(it.baseSlot, it.layers.filter { held -> held.cut.patch in shown }) }
        return coloredPartDraw(pieces, own, color.palette, fallback, color.shading, part.side, toCamera, part.model(), outline, cutMesh = cuts).copy(opacity = opacity, layer = layer)
    }
    val draw = coloredPartDraw(mesh, color.body.parts[id], color.palette, fallback, color.shading, part.side, toCamera, part.model(), outline, cutMesh = cuts)
    return draw.copy(opacity = opacity * draw.opacity, layer = layer)
}

/** [part], shown as [copies], as a pane draws it: its own mesh at rest once for each copy, colored as [colored] colors a part. */
private fun SheetLook.copiesDraw(part: SheetPart, copies: PartCopies, color: PaneColor?, toCamera: Vec3, outline: Float, opacity: Float, layer: DepthLayer): MeshDraw {
    val mesh = copies.rule.surface.mesh
    val id = part.id
    if (part.seeThrough || color == null || id == null) {
        val plain = draw(part, if (part.seeThrough) ghostOutline else outline, if (part.seeThrough) opacity * SEE_THROUGH_OPACITY else opacity, layer)
        return plain.copy(mesh = mesh, instances = copies.places.map { MeshInstance(copyModel(it)) })
    }
    val fallback = this.part.copy(ink = color.palette.colorOf(INK_SLOT) ?: this.part.ink)
    val draw = coloredPartDraw(mesh, color.body.parts[id], color.palette, fallback, color.shading, part.side, toCamera, Mat4.Identity, outline, copies = copies)
    return draw.copy(opacity = opacity * draw.opacity, layer = layer)
}

/** The ribbon along each border this surface work inks, closed on itself. */
fun ResolvedSurfaceWork.inkedBorders(): List<StrokeRibbon> =
    regions.filter { it.ink }.mapNotNull { StrokeRibbon.along(closedCurve(it.border), markProfile(EVEN_WIDTH)) }

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

/** What the open character's color reads now, or null with none open or until its surface is built. */
@Composable
fun rememberPaneColor(state: AppState): PaneColor? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val document by remember(open) { open.document }.collectAsState()
    val body by remember(open) { open.bodyColor.output }.collectAsState()
    val shading = rememberObjectShading(state)
    val surface = rememberPaneSurface(open) ?: return null
    val shown = body ?: return null
    return PaneColor(shown, document.character.palette, shading, surface)
}

/** The open character's one surface with its paint and dress, at rest and in the shown pose, or null until its graph has built it. */
@Composable
fun rememberPaneSurface(open: CharacterSession): PaneSurface? {
    val surface by remember(open) { open.characterSurface.output }.collectAsState()
    val paint by remember(open) { open.surfacePaint.output }.collectAsState()
    val dress by remember(open) { open.sessionSurface.dress.output }.collectAsState()
    val posedNode by remember(open) { open.sessionSurface.posed }.collectAsState()
    val posed by remember(posedNode) { posedNode?.output ?: MutableStateFlow(null) }.collectAsState()
    val rest = surface?.surface ?: return null
    val last = remember(open) { LastPaneSurface() }
    return remember(rest, paint, dress, posed) { last.next(rest, paint, dress?.dress, posed?.posed) }
}
