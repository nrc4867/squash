package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.gpu.Mat4

/** A mesh's colors. [ink] is its outline. */
@Immutable
data class PartMaterial(
    val base: Color,
    val shade: Color = base,
    val ink: Color = base,
    val shading: Shading = Shading.FLAT,
    val toLight: Vec3 = DEFAULT_TO_LIGHT,
) {
    companion object {
        val DEFAULT_TO_LIGHT = Vec3(0.45f, 0.8f, -0.5f).normalized()
    }
}

/** A rectangle of the surface in pixels, from its top-left corner. */
@Immutable
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** The part of this rectangle inside a [surfaceWidth] by [surfaceHeight] surface, which a pass may draw to. */
    fun clampedTo(surfaceWidth: Int, surfaceHeight: Int): PixelRect {
        val left = x.coerceIn(0, surfaceWidth)
        val top = y.coerceIn(0, surfaceHeight)
        return PixelRect(left, top, right.coerceIn(left, surfaceWidth) - left, bottom.coerceIn(top, surfaceHeight) - top)
    }
}

/** Which slice of the depth buffer a draw uses, so a whole layer stays behind or in front of another wherever its parts lie. */
enum class DepthLayer(val scale: Float, val offset: Float) {
    FRONT(0.1f, 0f),

    /** Behind all of [FRONT] and before all of [SCENE], so what a layer outlines clears it and shows over the rest. */
    BEHIND_FRONT(0f, 0.1f),
    SCENE(0.8f, 0.1f),
    BACK(0.1f, 0.9f),
}

/**
 * One mesh to draw in a view, placed by [model]. With an [outlinePixels] above 0 it has an outline that wide. Below an
 * [opacity] of 1 it blends over what a view drew before it, showing only its own nearest surface, so a view draws such
 * meshes after opaque ones and furthest first. [layers] draw over a mesh at full opacity straight after it, each over the last.
 */
@Immutable
data class MeshDraw(
    val mesh: RenderMesh,
    val material: PartMaterial,
    val model: Mat4 = Mat4.Identity,
    val outlinePixels: Float = 0f,
    val opacity: Float = 1f,
    val layer: DepthLayer = DepthLayer.SCENE,
    val layers: List<ColorLayerDraw> = emptyList(),
)

/** One part's fur: its [ink] and then its [fills] over it, so strokes that overlap read as one shape with one outline. */
@Immutable
data class FurLayerDraw(val ink: RibbonDraw?, val fills: List<MeshDraw>)

/**
 * One camera's view: what it sees, drawn into [viewport] with nothing outside it. [behind] draws first, then [draws], then [fur] in its order,
 * furthest part first, [ribbons] after that and [over] last.
 */
@Immutable
data class SceneView(
    val viewport: PixelRect,
    val viewProjection: Mat4,
    val draws: List<MeshDraw>,
    val ribbons: List<RibbonDraw> = emptyList(),
    val fur: List<FurLayerDraw> = emptyList(),
    val behind: List<ImageDraw> = emptyList(),
    val over: List<ImageDraw> = emptyList(),
)

/** One frame of [ViewsScene]: an immutable snapshot, safe to hand to the render thread. */
@Immutable
data class SceneFrame(
    val background: Color,
    val views: List<SceneView> = emptyList(),
    val overlay: ViewportOverlay = ViewportOverlay.Empty,
)
