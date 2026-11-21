package dev.chieppa.squash.render

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.ChartShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4

/** Vectors in the `Layer` uniform before its sides' tones. */
private const val SIDES_AFTER_VECTORS = 7

/** Vectors in the `Layer` uniform before a shape's detail: its opening's flatness and corner, its cut and its lid mass. */
private const val DETAIL_AFTER_VECTORS = 11

/** The `Layer` uniform of [COLOR_LAYER_WGSL], packed into one array reused for every layer. */
internal class ColorLayerUniform {
    val data = FloatArray(COLOR_LAYER_FLOATS)

    /** [data] for [layer] drawn over [over] in [view]. */
    fun write(layer: ColorLayerDraw, over: MeshDraw, view: SceneView): FloatArray {
        view.viewProjection.copyInto(data, 0)
        over.model.copyInto(data, MATRIX_FLOATS)
        var at = 2 * MATRIX_FLOATS
        at = put(at, layer.tones.lit)
        at = put(at, layer.tones.shadow)
        val light = over.material.toLight
        at = put(at, light.x, light.y, light.z, if (over.material.shading == Shading.TWO_TONE) 1f else 0f)
        at = put(at, view.viewport.width.toFloat(), view.viewport.height.toFloat(), over.layer.scale, over.layer.offset)
        val shape = layer.shape
        if (shape != null) {
            val o = shape.opening
            at = put(at, o.width, o.height, o.lowerLid, o.frontNarrowing)
            val oval = shape.oval
            at = put(at, oval?.u ?: 0f, oval?.v ?: 0f, oval?.halfU ?: 1f, oval?.halfV ?: 1f)
            val angle = shape.turn * PI.toFloat() / 180f
            put(at, cos(angle), sin(angle), if (oval != null) 1f else 0f, shape.ovalTurn * PI.toFloat() / 180f)
            writeDetail(shape)
        }
        val sides = layer.sides
        if (sides != null) {
            at = 2 * MATRIX_FLOATS + SIDES_AFTER_VECTORS * VECTOR_FLOATS
            at = put(at, sides.positive.lit.red, sides.positive.lit.green, sides.positive.lit.blue, if (sides.positiveShares != null) 1f else 0f)
            at = put(at, sides.positive.shadow)
            at = put(at, sides.negative.lit.red, sides.negative.lit.green, sides.negative.lit.blue, if (sides.negativeShares != null) 1f else 0f)
            put(at, sides.negative.shadow)
        }
        put(COLOR_LAYER_FLOATS - VECTOR_FLOATS, over.cloth?.lift ?: 0f, 0f, 0f, 0f)
        return data
    }

    /** [shape]'s opening's flatness and corner, its cut, its lid mass, its lids' pose and its lid paint, after the sides' tones. */
    private fun writeDetail(shape: ChartShape) {
        val cut = shape.cut
        val lid = shape.lid
        val paint = shape.paint
        val mark = paint?.mark
        val lids = shape.opening.lids
        var at = 2 * MATRIX_FLOATS + DETAIL_AFTER_VECTORS * VECTOR_FLOATS
        at = put(at, shape.opening.topFlat, shape.opening.outerCorner, if (lid != null) 1f else 0f, cut?.soft ?: 0f)
        val oval = cut?.oval
        at = if (oval != null) put(at, oval.u, oval.v, oval.halfU, oval.halfV) else put(at, cut?.du ?: 0f, cut?.dv ?: 0f, cut?.widen ?: 1f, 1f)
        val mode = when {
            cut == null -> 0f
            oval != null -> 1f
            else -> 2f
        }
        at = put(at, mode, if (cut?.outside == true) -1f else 1f, 0f, 0f)
        at = put(at, lid?.weight ?: 0f, lid?.inner ?: 0f, lid?.flick ?: 0f, lid?.flickLift ?: 0f)
        at = put(at, lid?.lower ?: 0f, lid?.lowerExtent ?: 0f, lid?.clumps?.toFloat() ?: 0f, lid?.clumpLength ?: 0f)
        at = put(at, lid?.clumpSpread ?: 0f, lid?.aspect ?: paint?.aspect ?: 1f, lid?.shoulder ?: -1f, lid?.lowerShoulder ?: -1f)
        at = put(at, lids.close, lids.rise, lids.closedAt, lids.closedBend)
        val paintMode = when {
            paint == null -> 0f
            mark == null -> 1f
            else -> 2f
        }
        at = put(at, lids.closedSlope, paintMode, paint?.above ?: 0f, paint?.fade ?: 0f)
        at = put(at, mark?.along ?: 0f, mark?.above ?: 0f, mark?.reachX ?: 0f, mark?.reachY ?: 0f)
        put(at, mark?.halfWidth ?: 0f, mark?.bend ?: 0f, lid?.clumpWidth ?: 0f, 0f)
    }

    private fun put(at: Int, color: Color) = put(at, color.red, color.green, color.blue, color.alpha)

    private fun put(at: Int, a: Float, b: Float, c: Float, d: Float): Int {
        data[at] = a
        data[at + 1] = b
        data[at + 2] = c
        data[at + 3] = d
        return at + VECTOR_FLOATS
    }
}
