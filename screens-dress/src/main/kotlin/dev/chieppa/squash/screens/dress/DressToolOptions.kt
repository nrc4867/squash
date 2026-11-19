package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.growCommand
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.PaintShape
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_area
import dev.chieppa.squash.uidesign.generated.dress_clean_edges
import dev.chieppa.squash.uidesign.generated.dress_cuff_on_second_press
import dev.chieppa.squash.uidesign.generated.dress_grow
import dev.chieppa.squash.uidesign.generated.dress_handles
import dev.chieppa.squash.uidesign.generated.dress_paint
import dev.chieppa.squash.uidesign.generated.dress_paint_mode
import dev.chieppa.squash.uidesign.generated.dress_shape
import dev.chieppa.squash.uidesign.generated.dress_shape_box
import dev.chieppa.squash.uidesign.generated.dress_shape_brush
import dev.chieppa.squash.uidesign.generated.dress_shape_ellipse
import dev.chieppa.squash.uidesign.generated.dress_shape_lasso
import dev.chieppa.squash.uidesign.generated.dress_shape_line
import dev.chieppa.squash.uidesign.generated.dress_shrink
import dev.chieppa.squash.uidesign.generated.dress_size
import dev.chieppa.squash.uidesign.generated.dress_straighten
import dev.chieppa.squash.uidesign.generated.dress_unpaint
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The brush sizes Paint offers, in body units. */
private val SIZES = 0.02f..0.5f

/** How many hundredths a size reads in. */
private const val HUNDREDTHS = 100f

/**
 * Paint's options: Paint or Unpaint, the shape a drag lays, the brush's Size and Clean edges, then Shrink and Grow, which move the
 * picked area's whole edge in or out a step.
 */
@Composable
internal fun PaintOptions(state: AppState) {
    val unpaint by state.dress.unpaint.collectAsState()
    val size by state.dress.brushSize.collectAsState()
    val clean by state.dress.clean.collectAsState()
    val shape by state.dress.shape.collectAsState()
    val paint = stringResource(Res.string.dress_paint)
    val erase = stringResource(Res.string.dress_unpaint)
    SettingRow(stringResource(Res.string.dress_paint_mode)) {
        Segmented(listOf(false, true), unpaint, { state.dress.unpaint.value = it }, { if (it) erase else paint }, Modifier.weight(1f).testTag(DressTags.PAINT_MODE), fill = true, compact = true)
    }
    SettingRow(stringResource(Res.string.dress_shape)) {
        PaintShape.entries.forEach { each ->
            IconButton(shapeIcon(each), stringResource(shapeName(each)), { state.dress.shape.value = each }, Modifier.testTag(DressTags.shape(each)), on = each == shape)
        }
    }
    val sizeLabel = stringResource(Res.string.dress_size)
    SettingRow(sizeLabel) {
        Slider(size, { state.dress.brushSize.value = it }, sizeLabel, Modifier.weight(1f).testTag(DressTags.SIZE), SIZES)
        Readout(((size * HUNDREDTHS).roundToInt() / HUNDREDTHS).toString())
    }
    PercentRow(stringResource(Res.string.dress_clean_edges), clean, DressTags.CLEAN) { state.dress.clean.value = it }
    SettingRow(stringResource(Res.string.dress_area)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            Button(stringResource(Res.string.dress_shrink), { growPicked(state, shrink = true) }, Modifier.testTag(DressTags.SHRINK), icon = SquashIcons.Shrink)
            Button(stringResource(Res.string.dress_grow), { growPicked(state, shrink = false) }, Modifier.testTag(DressTags.GROW), icon = SquashIcons.Grow)
        }
    }
}

/** Sends Shrink or Grow for the picked garment's picked area. */
internal fun growPicked(state: AppState, shrink: Boolean) {
    val picked = state.pickedGarment() ?: return
    picked.character.growCommand(picked.body, picked.garment, state.dress.area.value, shrink)?.let { state.session.value?.send(it) }
}

/** Edit curves' options: Handles, every edge's points shown, and Straighten, which makes the picked edge a level ring round its part. */
@Composable
internal fun EditCurvesOptions(state: AppState) {
    val handles by state.dress.handles.collectAsState()
    val picked by state.dress.edge.collectAsState()
    SwitchRow(stringResource(Res.string.dress_handles), handles, Modifier.testTag(DressTags.HANDLES)) { state.dress.handles.value = it }
    Row {
        Button(stringResource(Res.string.dress_straighten), { straightenPicked(state) }, Modifier.testTag(DressTags.STRAIGHTEN), icon = SquashIcons.Straighten, enabled = picked != null)
        Spacer(Modifier.weight(1f))
    }
}

/** Sends Straighten for the picked garment's picked edge. */
internal fun straightenPicked(state: AppState) {
    val picked = state.pickedGarment() ?: return
    val edge = state.dress.edge.value ?: return
    picked.character.straightenCommand(picked.body, picked.garment, edge)?.let { state.session.value?.send(it) }
}

/** Hole's options: whether a second press adds a cuff. */
@Composable
internal fun HoleOptions(state: AppState) {
    val cuff by state.dress.cuffOnSecondPress.collectAsState()
    SwitchRow(stringResource(Res.string.dress_cuff_on_second_press), cuff, Modifier.testTag(DressTags.CUFF_ON_SECOND)) { state.dress.cuffOnSecondPress.value = it }
}

private fun shapeIcon(shape: PaintShape) = when (shape) {
    PaintShape.BRUSH -> SquashIcons.Brush
    PaintShape.LINE -> SquashIcons.ShapeLine
    PaintShape.ELLIPSE -> SquashIcons.ShapeEllipse
    PaintShape.BOX -> SquashIcons.ShapeBox
    PaintShape.LASSO -> SquashIcons.ShapeLasso
}

private fun shapeName(shape: PaintShape): StringResource = when (shape) {
    PaintShape.BRUSH -> Res.string.dress_shape_brush
    PaintShape.LINE -> Res.string.dress_shape_line
    PaintShape.ELLIPSE -> Res.string.dress_shape_ellipse
    PaintShape.BOX -> Res.string.dress_shape_box
    PaintShape.LASSO -> Res.string.dress_shape_lasso
}
