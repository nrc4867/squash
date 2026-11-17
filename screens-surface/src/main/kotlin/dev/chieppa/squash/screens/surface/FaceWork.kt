package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How many views' faces are kept, for panes and what an orbit passes through. */
private const val KEPT_FACES = 64

/** How fine a face's fills are cut, in body units. */
private val FACE_TOLERANCE = ColorDetail.tolerance(ColorDetail.COARSEST)

/** What a face adds to a view of its part: fills over the part and the lines over everything. */
class FaceDraws(val layers: List<ColorLayerDraw>, val ribbons: List<RibbonDraw>) {
    companion object {
        val NONE = FaceDraws(emptyList(), emptyList())
    }
}

/** What a view reads of the character to show its faces: its placed [parts], its surface [work], the [palette] and the [shown] expression. */
class FaceInput(
    val parts: Map<PartId, PlacedPart>,
    val work: BodySurfaceWork,
    val lines: BodyLines?,
    val palette: Palette,
    val shown: ExpressionId? = null,
)

/** The faces of one state of the character as views ask for them: each part's mouth, in the version [binOf] says the view shows, drawn with [rule] in [ink]. */
class FaceWork(
    private val input: FaceInput,
    private val rule: LineRule,
    private val ink: Color,
    private val taper: Float,
    private val binOf: (PlacedPart, SurfaceView) -> CameraBin,
) {
    private data class Key(val part: PartId, val bin: CameraBin)

    private val kept = object : LinkedHashMap<Key, FaceDraws>(KEPT_FACES, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, FaceDraws>) = size > KEPT_FACES
    }

    /** What [part]'s face adds through [view]. */
    fun draws(part: PartId, view: SurfaceView): FaceDraws {
        val placed = input.parts[part] ?: return FaceDraws.NONE
        val work = input.work.parts[part] ?: return FaceDraws.NONE
        if (work.groups.none { it.isMouth }) return FaceDraws.NONE
        val bin = binOf(placed, view)
        return kept.getOrPut(Key(part, bin)) { drawn(part, placed, work, bin) }
    }

    private fun drawn(part: PartId, placed: PlacedPart, work: ResolvedSurfaceWork, bin: CameraBin): FaceDraws {
        val marks = work.marks.associateBy { it.id }
        val expression = work.expressions.firstOrNull { it.id == input.shown }
        val layers = ArrayList<ColorLayerDraw>()
        val ribbons = ArrayList<RibbonDraw>()
        for (group in work.groups.filter { it.isMouth }) {
            val mouth = mouthDraws(group, marks, expression?.marks?.get(group.id), placed.surface, bin, input.palette, FACE_TOLERANCE, placed.origin)
            layers += mouth.layers
            mouth.lips.mapNotNullTo(ribbons) { it.lineDraw(input.lines?.parts?.get(part), rule, ink, taper, DepthLayer.SCENE) }
        }
        return FaceDraws(layers, ribbons)
    }
}
