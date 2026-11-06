package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.EyeFrame
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.SurfaceGaze
import dev.chieppa.squash.character.build.solveSurfaceGaze
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How finely a view's direction is read for what shows of a face, which holds its fills still while a camera creeps. */
private const val DIRECTION_STEP = 0.02f

/** How many views' faces are kept, for panes and what an orbit passes through. */
private const val KEPT_FACES = 64

/** How far the pupil travels with the eye looking square along the surface, as a share of the lid's reach. */
private const val GAZE_TRAVEL = 1.1f

/** How fine a face's fills are cut, in body units. */
private val FACE_TOLERANCE = ColorDetail.tolerance(ColorDetail.COARSEST)

/** What a face adds to a view of its part: fills over the part and the lines over everything. */
class FaceDraws(val layers: List<ColorLayerDraw>, val ribbons: List<RibbonDraw>) {
    companion object {
        val NONE = FaceDraws(emptyList(), emptyList())
    }
}

/** What a view reads of the character to show its faces: its placed [parts], its surface [work], the [palette], the [shown] expression, the gaze [target] and [cheats]. */
class FaceInput(
    val parts: Map<PartId, PlacedPart>,
    val work: BodySurfaceWork,
    val lines: BodyLines?,
    val palette: Palette,
    val toLight: Vec3,
    val shown: ExpressionId? = null,
    val target: Vec3? = null,
    val cheats: GazeCheats = GazeCheats(),
)

/**
 * The faces of one state of the character as views ask for them: each part's eyes and mouths, in the version [binOf] says the view shows, drawn with [rule] in [ink]. Eyes
 * look at the input's target, if it has one, through the gaze solve.
 */
class FaceWork(private val input: FaceInput, private val rule: LineRule, private val ink: Color, private val taper: Float, private val binOf: (PlacedPart, SurfaceView) -> CameraBin) {
    private data class Key(val part: PartId, val bin: CameraBin, val x: Int, val y: Int, val z: Int)

    private val kept = object : LinkedHashMap<Key, FaceDraws>(KEPT_FACES, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, FaceDraws>) = size > KEPT_FACES
    }

    /** What [part]'s face adds through [view]. */
    fun draws(part: PartId, view: SurfaceView): FaceDraws {
        val placed = input.parts[part] ?: return FaceDraws.NONE
        val work = input.work.parts[part] ?: return FaceDraws.NONE
        if (work.groups.none { it.isEye || it.isMouth }) return FaceDraws.NONE
        val bin = binOf(placed, view)
        val toCamera = view.toViewer(placed.box?.center ?: placed.origin).normalized()
        fun step(value: Float) = kotlin.math.round(value / DIRECTION_STEP).toInt()
        return kept.getOrPut(Key(part, bin, step(toCamera.x), step(toCamera.y), step(toCamera.z))) { drawn(part, placed, work, bin, toCamera) }
    }

    private fun drawn(part: PartId, placed: PlacedPart, work: ResolvedSurfaceWork, bin: CameraBin, toCamera: Vec3): FaceDraws {
        val marks = work.marks.associateBy { it.id }
        val expression = work.expressions.firstOrNull { it.id == input.shown }
        val eyes = work.groups.filter { it.isEye }
        val gazes = gazeOf(placed, work, eyes, bin, toCamera)
        val layers = ArrayList<ColorLayerDraw>()
        val ribbons = ArrayList<RibbonDraw>()
        for (group in eyes) {
            val gaze = gazes[group.id]
            val show = EyeShow(expression?.marks?.get(group.id), gaze?.offset ?: Vec2(0f, 0f), gaze?.scale ?: 1f)
            val eye = eyeDraws(group, marks, placed.surface, bin, input.palette, input.toLight, toCamera, FACE_TOLERANCE, rule, ink, show, placed.origin)
            layers += eye.layers
            ribbons += eye.ribbons
        }
        for (group in work.groups.filter { it.isMouth }) {
            val mouth = mouthDraws(group, marks, expression?.marks?.get(group.id), placed.surface, bin, input.palette, FACE_TOLERANCE, placed.origin)
            layers += mouth.layers
            mouth.lips.mapNotNullTo(ribbons) { it.lineDraw(input.lines?.parts?.get(part), rule, ink, taper, DepthLayer.SCENE) }
        }
        return FaceDraws(layers, ribbons)
    }

    private fun gazeOf(placed: PlacedPart, work: ResolvedSurfaceWork, eyes: List<MarkGroup>, bin: CameraBin, toCamera: Vec3): Map<MarkGroupId, SurfaceGaze> {
        val target = input.target ?: return emptyMap()
        val marks = work.marks.associateBy { it.id }
        val held = eyes.mapNotNull { group -> eyeFrame(group, marks, placed, bin)?.let { group.id to it } }
        val solved = solveSurfaceGaze(held.map { it.second }, target, toCamera, input.cheats)
        return held.indices.associate { held[it].first to solved[it] }
    }

    private fun eyeFrame(group: MarkGroup, marks: Map<dev.chieppa.squash.document.MarkId, ResolvedMark>, placed: PlacedPart, bin: CameraBin): EyeFrame? {
        val iris = roleShapes(group, MarkRole.IRIS, marks, null, bin).firstOrNull() ?: return null
        val lid = roleShapes(group, MarkRole.LID, marks, null, bin).firstOrNull() ?: return null
        val patch = placed.surface.patches.firstOrNull { it.name == iris.patch } ?: return null
        val middle = iris.places.middle()
        val at = patch.surfaceAt(middle.x, middle.y)
        val surface = placed.surface
        val travel = Vec2(
            (lid.places.maxOf { it.x } - lid.places.minOf { it.x }) / 2f * GAZE_TRAVEL,
            (lid.places.maxOf { it.y } - lid.places.minOf { it.y }) / 2f * GAZE_TRAVEL,
        )
        return EyeFrame(placed.origin + surface.shown(at.position), surface.shown(at.normal), surface.shown(at.alongU).normalized(), surface.shown(at.aroundV).normalized(), travel)
    }
}
