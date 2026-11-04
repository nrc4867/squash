package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.uidesign.generated.sheet_pane_fit
import dev.chieppa.squash.uidesign.generated.sheet_pane_zoom
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import dev.chieppa.stretch.ui.Tooltip
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_chip_pixels
import dev.chieppa.squash.uidesign.generated.edit_chip_this_side
import dev.chieppa.squash.uidesign.generated.edit_refit
import dev.chieppa.squash.uidesign.generated.sheet_chip_drawing
import dev.chieppa.squash.uidesign.generated.sheet_chip_drawn
import dev.chieppa.squash.uidesign.generated.sheet_chip_from_template
import dev.chieppa.squash.uidesign.generated.sheet_chip_live
import dev.chieppa.squash.uidesign.generated.sheet_pane_front
import dev.chieppa.squash.uidesign.generated.sheet_pane_full
import dev.chieppa.squash.uidesign.generated.sheet_pane_side
import dev.chieppa.squash.uidesign.generated.sheet_pane_top
import dev.chieppa.stretch.frame.Hud
import dev.chieppa.stretch.frame.Viewport
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.ProgressBar
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/** A pane over the surface, which shows through it: its label names the [body] direction it looks from, null for Full, its chip floats in the other corner, and [modifier] takes its pointer. */
@Composable
internal fun PaneViewport(
    body: BodyView?,
    chip: PaneChip?,
    modifier: Modifier = Modifier,
    fitError: Float? = null,
    progress: Float = 0f,
    bottomStart: (@Composable () -> Unit)? = null,
    zoom: Float? = null,
    onFit: () -> Unit = {},
) {
    Viewport(
        modifier,
        nativeContent = true,
        topStart = { PaneLabel(body) },
        topEnd = chip?.let { { PaneChipView(it, fitError, progress) } },
        bottomStart = bottomStart,
        bottomEnd = zoom?.let { { PaneZoom(it, onFit) } },
    ) {}
}

/** The pane's zoom while it is away from the fit; pressing it goes back. */
@Composable
private fun PaneZoom(zoom: Float, onFit: () -> Unit) {
    val label = stringResource(Res.string.sheet_pane_fit)
    Tooltip(label, listOf("F")) {
        Box(Modifier.testTag(PANE_ZOOM_TAG).clickable(onClick = onFit)) {
            Hud(stringResource(Res.string.sheet_pane_zoom, (zoom * PERCENT).roundToInt()))
        }
    }
}

@Composable
private fun PaneLabel(body: BodyView?) {
    Hud(
        stringResource(
            when (body) {
                BodyView.TOP -> Res.string.sheet_pane_top
                null -> Res.string.sheet_pane_full
                BodyView.SIDE -> Res.string.sheet_pane_side
                BodyView.FRONT -> Res.string.sheet_pane_front
            },
        ),
    )
}

@Composable
private fun PaneChipView(chip: PaneChip, fitError: Float?, progress: Float) {
    when (chip) {
        PaneChip.DRAWING -> Chip(stringResource(Res.string.sheet_chip_drawing), kind = ChipKind.On)
        PaneChip.DRAWN ->
            if (fitError != null) {
                Chip(stringResource(Res.string.edit_chip_pixels, pixelsLabel(fitError)), mono = true)
            } else {
                Chip(stringResource(Res.string.sheet_chip_drawn), icon = StretchIcons.Check)
            }
        PaneChip.FROM_TEMPLATE -> Chip(stringResource(Res.string.sheet_chip_from_template))
        PaneChip.LIVE -> Chip(stringResource(Res.string.sheet_chip_live))
        PaneChip.REFIT -> {
            val refit = stringResource(Res.string.edit_refit)
            Hud(refit, leading = { ProgressBar(progress, refit, Modifier.width(REFIT_BAR_WIDTH).testTag(REFIT_BAR_TAG)) })
        }
        PaneChip.THIS_SIDE -> Chip(stringResource(Res.string.edit_chip_this_side), icon = SquashIcons.PairOverride)
        PaneChip.HIDES_PAIR -> Chip(stringResource(Res.string.edit_chip_this_side), kind = ChipKind.Warn)
    }
}

/** The test tag of a pane's zoom readout. */
const val PANE_ZOOM_TAG = "sheet.pane.zoom"

private const val PERCENT = 100

/** The test tag of the Refit readout's bar. */
const val REFIT_BAR_TAG = "sheet.refit.bar"

private val REFIT_BAR_WIDTH = 72.dp

/** A pixel error as the chip reads it, to one decimal. */
fun pixelsLabel(pixels: Float): String = "%.1f".format(Locale.ROOT, pixels)
