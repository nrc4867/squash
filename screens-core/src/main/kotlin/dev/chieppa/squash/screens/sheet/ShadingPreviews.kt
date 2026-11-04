package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_shading_flat
import dev.chieppa.squash.uidesign.generated.color_shading_two_tone
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.theme.Stretch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val TILE_WIDTH = 128.dp
private val TILE_HEIGHT = 92.dp
private val TILE_GAP = 10.dp
private val TILE_RING = 2.dp

/** The test tag of the preview tile of [mode]. */
fun shadingPreviewTag(mode: Shading) = "sheet.preview.${mode.name}"

/** The test tag of the label over the preview tile of [mode]. */
fun shadingPreviewLabelTag(mode: Shading) = "sheet.previewLabel.${mode.name}"

/** One preview of the whole character: where it shows in the frame and what shading it shows. */
class ShadingPreview(val rect: PixelRect, val shading: ObjectShading)

/** Where the tiles and the surface under them lie in the window, so the frame can draw into the tiles' holes. */
@Stable
class PreviewPlaces {
    var surface by mutableStateOf(Offset.Zero)
    val tiles = mutableStateMapOf<Shading, PixelRect>()

    /** The previews to draw, each [shading] by its tile's mode; none while [shown] is false. */
    fun previews(shown: Boolean, shading: ObjectShading): List<ShadingPreview> {
        if (!shown) return emptyList()
        return tiles.map { (mode, rect) ->
            ShadingPreview(PixelRect(rect.x - surface.x.roundToInt(), rect.y - surface.y.roundToInt(), rect.width, rect.height), shading.copy(mode = mode))
        }
    }
}

/** The views of [previews], each the whole of [selection] through [camera] in its own shading. */
fun previewViews(previews: List<ShadingPreview>, selection: SheetSelection?, camera: CameraView?, look: SheetLook, color: PaneColor?): List<SceneView> {
    if (selection == null || camera == null) return emptyList()
    val eye = Vec3(camera.eye.x, camera.eye.y, camera.eye.z)
    val parts = (selection.others + selection.part).filter { !it.planned }
    return previews.map { preview ->
        val shaded = color?.let { PaneColor(it.body, it.palette, preview.shading, it.borders) }
        val draws = parts.map { look.colored(it, shaded, eye - selection.box.center, look.ghostOutline) }
        SceneView(preview.rect, camera.viewProjection(preview.rect.width, preview.rect.height), draws, look.borderRibbons(shaded))
    }
}

/** The Flat and Two-tone tiles, each a hole the frame draws its preview into; pressing one chooses that shading. */
@Composable
fun ShadingPreviewTiles(state: AppState, places: PreviewPlaces, modifier: Modifier = Modifier) {
    val shading = rememberObjectShading(state)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        Tile(state, places, Shading.FLAT, stringResource(Res.string.color_shading_flat), shading)
        Tile(state, places, Shading.TWO_TONE, stringResource(Res.string.color_shading_two_tone), shading)
    }
}

@Composable
private fun Tile(state: AppState, places: PreviewPlaces, mode: Shading, label: String, shading: ObjectShading) {
    val c = Stretch.colors
    val shape = Stretch.shapes.ctlShape
    Column(Modifier.width(TILE_WIDTH)) {
        BasicText(
            label,
            Modifier.testTag(shadingPreviewLabelTag(mode)).fillMaxWidth().background(c.surfaceFloat).padding(horizontal = 6.dp, vertical = 2.dp),
            style = Stretch.type.value,
            maxLines = 1,
        )
        Box(
            Modifier
                .testTag(shadingPreviewTag(mode))
                .size(TILE_WIDTH, TILE_HEIGHT)
                .border(TILE_RING, if (shading.mode == mode) c.accent else c.lineStrong, shape)
                .onGloballyPositioned {
                    val at = it.positionInRoot()
                    places.tiles[mode] = PixelRect(at.x.roundToInt(), at.y.roundToInt(), it.size.width, it.size.height)
                }
                .clickable { state.chooseShading(mode) },
        )
    }
}
