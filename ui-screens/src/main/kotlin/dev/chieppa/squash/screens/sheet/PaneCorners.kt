package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_chip_pixels
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
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/** A pane over the surface, which shows through it: its label and chip float in its corners, and [modifier] takes its pointer. */
@Composable
internal fun PaneViewport(pane: SheetPane, chip: PaneChip?, modifier: Modifier = Modifier, fitError: Float? = null) {
    Viewport(
        modifier,
        nativeContent = true,
        topStart = { PaneLabel(pane) },
        topEnd = chip?.let { { PaneChipView(it, fitError) } },
    ) {}
}

@Composable
private fun PaneLabel(pane: SheetPane) {
    Hud(
        stringResource(
            when (pane) {
                SheetPane.TOP -> Res.string.sheet_pane_top
                SheetPane.FULL -> Res.string.sheet_pane_full
                SheetPane.SIDE -> Res.string.sheet_pane_side
                SheetPane.FRONT -> Res.string.sheet_pane_front
            },
        ),
    )
}

@Composable
private fun PaneChipView(chip: PaneChip, fitError: Float?) {
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
        PaneChip.REFIT -> Hud(stringResource(Res.string.edit_refit))
    }
}

/** A pixel error as the chip reads it, to one decimal. */
fun pixelsLabel(pixels: Float): String = "%.1f".format(Locale.ROOT, pixels)
