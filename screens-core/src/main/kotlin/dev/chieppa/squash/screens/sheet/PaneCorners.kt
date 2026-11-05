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
import dev.chieppa.squash.uidesign.generated.sheet_chip_surface_only
import dev.chieppa.squash.uidesign.generated.sheet_pane_back
import dev.chieppa.squash.uidesign.generated.sheet_pane_bottom
import dev.chieppa.squash.uidesign.generated.sheet_pane_far_side
import dev.chieppa.squash.uidesign.generated.sheet_pane_flip
import dev.chieppa.squash.uidesign.generated.sheet_pane_front
import dev.chieppa.squash.uidesign.generated.sheet_pane_full
import dev.chieppa.squash.uidesign.generated.sheet_pane_side
import dev.chieppa.squash.uidesign.generated.sheet_pane_top
import dev.chieppa.squash.uidesign.generated.shell_view_back
import dev.chieppa.squash.uidesign.generated.shell_view_bottom
import dev.chieppa.squash.uidesign.generated.shell_view_far_side
import dev.chieppa.squash.uidesign.generated.shell_view_front
import dev.chieppa.squash.uidesign.generated.shell_view_side
import dev.chieppa.squash.uidesign.generated.shell_view_top
import dev.chieppa.stretch.frame.Hud
import dev.chieppa.stretch.frame.Viewport
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.ProgressBar
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/**
 * A pane over the surface, which shows through it: its label names the [body] direction it looks from, null for Full, its
 * chip floats in the other corner, and [modifier] takes its pointer. A [short] label drops what the pane's camera follows. A locked pane's label
 * turns it to look from the other side through [onFlip], and names the side while [flipped].
 */
@Composable
fun PaneViewport(
    body: BodyView?,
    chip: PaneChip?,
    modifier: Modifier = Modifier,
    fitError: Float? = null,
    progress: Float = 0f,
    bottomStart: (@Composable () -> Unit)? = null,
    zoom: Float? = null,
    onFit: () -> Unit = {},
    short: Boolean = false,
    flipped: Boolean = false,
    onFlip: () -> Unit = {},
) {
    Viewport(
        modifier,
        nativeContent = true,
        topStart = { Box(Modifier.testTag(PANE_CORNER_TAG)) { PaneLabel(body, short, flipped, onFlip) } },
        topEnd = chip?.let { { Box(Modifier.testTag(PANE_CORNER_TAG)) { PaneChipView(it, fitError, progress) } } },
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
private fun PaneLabel(body: BodyView?, short: Boolean, flipped: Boolean, onFlip: () -> Unit) {
    val label = stringResource(paneLabel(body, short, flipped))
    if (body == null) {
        Hud(label)
        return
    }
    val flip = stringResource(Res.string.sheet_pane_flip)
    Tooltip(flip) {
        Box(Modifier.testTag(paneFlipTag(body)).clickable(onClickLabel = flip, onClick = onFlip)) { Hud(label) }
    }
}

private fun paneLabel(body: BodyView?, short: Boolean, flipped: Boolean) = when (body) {
    null -> Res.string.sheet_pane_full
    BodyView.TOP -> when {
        flipped -> if (short) Res.string.shell_view_bottom else Res.string.sheet_pane_bottom
        else -> if (short) Res.string.shell_view_top else Res.string.sheet_pane_top
    }
    BodyView.SIDE -> when {
        flipped -> if (short) Res.string.shell_view_far_side else Res.string.sheet_pane_far_side
        else -> if (short) Res.string.shell_view_side else Res.string.sheet_pane_side
    }
    BodyView.FRONT -> when {
        flipped -> if (short) Res.string.shell_view_back else Res.string.sheet_pane_back
        else -> if (short) Res.string.shell_view_front else Res.string.sheet_pane_front
    }
}

@Composable
private fun PaneChipView(chip: PaneChip, fitError: Float?, progress: Float) {
    when (chip) {
        PaneChip.DRAWING -> Chip(stringResource(Res.string.sheet_chip_drawing), kind = ChipKind.On)
        PaneChip.FIT_ERROR -> Chip(stringResource(Res.string.edit_chip_pixels, pixelsLabel(fitError ?: 0f)), mono = true)
        PaneChip.REFIT -> {
            val refit = stringResource(Res.string.edit_refit)
            Hud(refit, leading = { ProgressBar(progress, refit, Modifier.width(REFIT_BAR_WIDTH).testTag(REFIT_BAR_TAG)) })
        }
        PaneChip.THIS_SIDE -> Chip(stringResource(Res.string.edit_chip_this_side), icon = SquashIcons.PairOverride)
        PaneChip.HIDES_PAIR -> Chip(stringResource(Res.string.edit_chip_this_side), kind = ChipKind.Warn)
        PaneChip.SURFACE_REFUSED -> Box(Modifier.testTag(SURFACE_REFUSED_TAG)) { Chip(stringResource(Res.string.sheet_chip_surface_only), kind = ChipKind.Warn) }
    }
}

/** The test tag of every pane's label and chip. */
const val PANE_CORNER_TAG = "sheet.pane.corner"

/** The test tag of Full first's chip while a tool is refused in Full. */
const val SURFACE_REFUSED_TAG = "sheet.pane.surfaceRefused"

/** The test tag of the label that flips the pane looking from [body]. */
fun paneFlipTag(body: BodyView) = "sheet.pane.flip.${body.name}"

/** The test tag of a pane's zoom readout. */
const val PANE_ZOOM_TAG = "sheet.pane.zoom"

private const val PERCENT = 100

/** The test tag of the Refit readout's bar. */
const val REFIT_BAR_TAG = "sheet.refit.bar"

private val REFIT_BAR_WIDTH = 72.dp

/** A pixel error as the chip reads it, to one decimal. */
fun pixelsLabel(pixels: Float): String = "%.1f".format(Locale.ROOT, pixels)
