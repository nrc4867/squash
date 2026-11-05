package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.FurCamera
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.PartFurView
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.DefaultToneRule
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.FurLayerDraw
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.RibbonPoint
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.Mat4
import kotlin.math.roundToInt

/** What a wedge's ink edge weighs: thin at its base, so a notch between two tufts ends in a point, full up its rise, thinning to the tip. */
private const val EDGE_AT_BASE = 0.2f
private const val EDGE_FULL_FROM = 0.35f
private const val EDGE_FULL_TO = 0.85f
private const val EDGE_AT_TIP = 0.7f

/** How many weights of ink a wedge's size is rounded to, each a profile made once. */
private const val EDGE_LEVELS = 8

private val EDGE_PROFILES = List(EDGE_LEVELS + 1) { level ->
    val scale = level.toFloat() / EDGE_LEVELS
    markProfile(MarkWidth(listOf(WidthStop(0f, scale * EDGE_AT_BASE), WidthStop(EDGE_FULL_FROM, scale), WidthStop(EDGE_FULL_TO, scale), WidthStop(1f, scale * EDGE_AT_TIP))))
}

private const val MATRIX_FLOATS = 16

/** A part's fills cover the inner half of its ink, so its ink draws this much wider to show the width its rule says. */
private const val INK_WIDENS = 2f

/** What a view's fur adds to its scene, part by part, furthest first. */
class FurDraws(val layers: List<FurLayerDraw>) {
    companion object {
        val NONE = FurDraws(emptyList())
    }
}

/** A view's fur ready to draw: what it shows, inked by [edge], with each part's ink edges made once as one ribbon. */
class ShownFur(val view: FurView, val edge: LineRule) {
    val edges: Map<PartId, StrokeRibbon> = view.parts.mapNotNull { (id, part) -> StrokeRibbon.joined(part.edgeRibbons())?.let { id to it } }.toMap()

    companion object {
        val NONE = ShownFur(FurView.EMPTY, LineRule(0f))
    }
}

/** The camera a free view's fur reads. */
fun furCamera(camera: CameraView, width: Int, height: Int): FurCamera {
    val eye = Vec3(camera.eye.x, camera.eye.y, camera.eye.z)
    return FurCamera(camera.viewProjection(width, height).floats(), width, height, eye, (eye - Vec3(camera.target.x, camera.target.y, camera.target.z)).normalized())
}

/** The camera a locked pane's fur reads. */
fun furCamera(camera: LockedCamera): FurCamera =
    FurCamera(camera.viewProjection().floats(), camera.width, camera.height, null, camera.basis.back)

private fun Mat4.floats(): List<Float> = FloatArray(MATRIX_FLOATS).also { copyInto(it, 0) }.toList()

/** [fur] as draws: each part's ink edges, and its fills over them in what [material] gives for its slot, in [layer]. [parts] says which parts it shows, every one when null. */
fun furDraws(fur: ShownFur, ink: Color, layer: DepthLayer = DepthLayer.SCENE, parts: Set<PartId>? = null, material: (PartId, SlotName?) -> PartMaterial): FurDraws {
    val shown = fur.view.parts.filterKeys { parts == null || it in parts }
    if (shown.isEmpty()) return FurDraws.NONE
    val rule = fur.edge.copy(interiorPixels = fur.edge.interiorPixels * INK_WIDENS, silhouettePixels = fur.edge.silhouettePixels * INK_WIDENS)
    return FurDraws(
        shown.map { (part, held) ->
            FurLayerDraw(fur.edges[part]?.let { RibbonDraw(it, rule, ink, layer = layer) }, held.fills.map { MeshDraw(it.mesh, material(part, it.slot), layer = layer) })
        },
    )
}

/** What fur over [slot] fills with: its palette color and shadow as a part's surface takes them, on [side], with the camera at [toCamera]. A wedge's mesh carries its root's normal, so two-tone reads the surface there. */
fun furMaterial(palette: Palette, slot: SlotName?, fallback: Color, ink: Color, shading: ObjectShading, side: BodySide?, toCamera: Vec3): PartMaterial {
    val tones = DefaultToneRule.tones(palette.colorOf(slot) ?: fallback, palette.shadowOf(slot), shading, side, toCamera)
    return PartMaterial(tones.lit, tones.shadow, ink, shading.mode, shading.toLight)
}

private fun PartFurView.edgeRibbons(): List<StrokeRibbon> = wedges.flatMap { wedge ->
    val profile = EDGE_PROFILES[(wedge.size * EDGE_LEVELS).roundToInt().coerceIn(0, EDGE_LEVELS)]
    listOf(wedge.left, wedge.right).mapNotNull { line -> StrokeRibbon.along(line.map { RibbonPoint(it, wedge.normal) }, profile) }
}

/** What the fur node for the view named [key] holds, once it has told that node the [camera] it looks through, inked as the figure's outline is. */
@Composable
fun rememberFurView(session: CharacterSession?, key: String, camera: FurCamera?): ShownFur {
    if (session == null) return ShownFur.NONE
    SideEffect { camera?.let { session.showFurFrom(key, it) } }
    val shown by remember(session, key) { session.furView(key).output }.collectAsState()
    val document by remember(session) { session.document }.collectAsState()
    val rules = document.character.lineRules
    val density = LocalDensity.current.density
    return remember(shown, rules, density) { ShownFur(shown ?: FurView.EMPTY, LineRule(rules.interior * density, rules.silhouette * density)) }
}

/** The view names the panes' fur nodes go by. */
object FurViews {
    const val FULL = "full"
    const val SINGLE = "single"

    fun locked(view: View) = "locked-${view.name}"
}

/**
 * [fur] as a pane draws it, with the camera at [toCamera]: only [parts], when that is given. With [color] a wedge fills with the palette
 * under it and takes the pane's shading; with none, the look's own part fill, far side in its shade.
 */
fun SheetLook.furDraws(fur: ShownFur, color: PaneColor?, toCamera: Vec3, selection: SheetSelection, parts: Set<PartId>? = null, layer: DepthLayer = DepthLayer.SCENE): FurDraws {
    val placed = (selection.others + selection.part).mapNotNull { part -> part.id?.let { it to part } }.toMap()
    val ink = color?.palette?.colorOf(INK_SLOT) ?: part.ink
    return furDraws(fur, ink, layer, parts) { id, slot ->
        val held = placed[id]
        if (color == null) {
            if (held?.mirrored == true) farPart else part
        } else {
            furMaterial(color.palette, slot, part.base, ink, color.shading, held?.side, toCamera)
        }
    }
}
