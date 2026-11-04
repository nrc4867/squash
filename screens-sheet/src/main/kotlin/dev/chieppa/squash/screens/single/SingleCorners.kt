package dev.chieppa.squash.screens.single

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.pixelsLabel
import dev.chieppa.squash.character.catalog.FitStages
import dev.chieppa.squash.character.catalog.DIRECTION_ORDER
import dev.chieppa.squash.screens.shell.directionLabel
import dev.chieppa.squash.character.catalog.count
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.uidesign.THUMB_HEIGHT
import dev.chieppa.squash.uidesign.THUMB_WIDTH
import dev.chieppa.squash.uidesign.ViewThumb
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.shell_part_fit_surface
import dev.chieppa.squash.uidesign.generated.single_hud
import dev.chieppa.squash.uidesign.generated.single_hud_sided
import dev.chieppa.squash.uidesign.generated.single_side_left
import dev.chieppa.squash.uidesign.generated.single_side_right
import dev.chieppa.squash.uidesign.generated.single_snap_front
import dev.chieppa.squash.uidesign.generated.single_snap_side
import dev.chieppa.squash.uidesign.generated.single_snap_top
import dev.chieppa.squash.uidesign.generated.single_thumb_pixels
import dev.chieppa.squash.uidesign.generated.single_view_free
import dev.chieppa.squash.uidesign.generated.single_view_front
import dev.chieppa.squash.uidesign.generated.single_view_side
import dev.chieppa.squash.uidesign.generated.single_view_top
import dev.chieppa.stretch.frame.Hud
import dev.chieppa.stretch.frame.Toolbar
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.ProgressBar
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Tooltip
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private val THUMB_GAP = 10.dp
private val THUMBS_INSET = 6.dp
private val FIT_BAR_WIDTH = 160.dp
private val FIT_TEXT_INSET = 8.dp
private const val PERCENT = 100f

/** How far the graph is through the part's surface, as its nodes count it, while it is working. */
@Composable
internal fun rememberFitting(state: AppState): Float? {
    val session by state.session.collectAsState()
    val progress = session?.graph?.progress?.collectAsState()?.value ?: return null
    return progress.count(FitStages.SURFACE).takeUnless { progress.total == 0 }?.fraction
}

/** The fit's stage, bar and percent, as board 4 draws them. */
@Composable
internal fun FitProgressBar(fraction: Float) {
    val surface = stringResource(Res.string.shell_part_fit_surface)
    Toolbar(Modifier.testTag(SingleTags.FIT)) {
        BasicText(surface, Modifier.padding(horizontal = FIT_TEXT_INSET), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)))
        ProgressBar(fraction, surface, Modifier.width(FIT_BAR_WIDTH))
        BasicText(
            stringResource(Res.string.edit_percent, (fraction * PERCENT).roundToInt()),
            Modifier.padding(horizontal = FIT_TEXT_INSET),
            style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
        )
    }
}

/** Snap view: which direction the camera looks from, none while free; picking one turns the camera there. */
@Composable
internal fun SnapViewControl(snapped: BodyView?, onSnap: (BodyView) -> Unit) {
    val labels = DIRECTION_ORDER.associateWith { directionLabel(it) }
    Segmented<BodyView?>(DIRECTION_ORDER, snapped, { it?.let(onSnap) }, { labels[it].orEmpty() })
}

/** The part, its side and the direction the camera looks from, as the board's HUD reads them. */
@Composable
internal fun SingleHud(state: AppState, snapped: BodyView?) {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val document = session?.document?.collectAsState()?.value ?: return
    val part = selected?.let(document.character::part) ?: return
    val name = partName(part, state.templates)
    val view = stringResource(
        when (snapped) {
            null -> Res.string.single_view_free
            BodyView.SIDE -> Res.string.single_view_side
            BodyView.TOP -> Res.string.single_view_top
            BodyView.FRONT -> Res.string.single_view_front
        },
    )
    val text = when (part.side) {
        null -> stringResource(Res.string.single_hud, name, view)
        Side.LEFT -> stringResource(Res.string.single_hud_sided, name, stringResource(Res.string.single_side_left), view)
        Side.RIGHT -> stringResource(Res.string.single_hud_sided, name, stringResource(Res.string.single_side_right), view)
    }
    Hud(text, Modifier.testTag(SingleTags.HUD))
}

/** A thumbnail per view, named for the direction it is looked at from, with its fit error once a stroke has one; one not on the view on screen turns the camera to its own. */
@Composable
internal fun ThumbnailStrip(
    selection: SheetSelection,
    current: View?,
    live: LiveStroke?,
    templateHint: Boolean,
    fitError: (View) -> Float?,
    onSnap: (View) -> Unit,
    alongside: List<LiveStroke> = emptyList(),
) {
    val density = LocalDensity.current
    val width = with(density) { THUMB_WIDTH.roundToPx() }
    val height = with(density) { THUMB_HEIGHT.roundToPx() }
    Toolbar(Modifier.testTag(SingleTags.THUMBNAILS)) {
        Row(Modifier.padding(THUMBS_INSET), horizontalArrangement = Arrangement.spacedBy(THUMB_GAP)) {
            for (thumbnail in viewThumbnails(selection, current, live, templateHint, width, height, alongside)) {
                val tagged = Modifier.testTag(SingleTags.thumbnail(thumbnail.view))
                val name = directionLabel(thumbnail.body)
                val label = fitError(thumbnail.view)?.let { stringResource(Res.string.single_thumb_pixels, name, pixelsLabel(it)) } ?: name
                if (thumbnail.snaps) {
                    val snapTo = stringResource(snapLabel(thumbnail.body))
                    Tooltip(snapTo) {
                        ViewThumb(label, thumbnail.edge, thumbnail.lines, thumbnail.hint, tagged.clickable(onClickLabel = snapTo) { onSnap(thumbnail.view) }, thumbnail.preview)
                    }
                } else {
                    ViewThumb(label, thumbnail.edge, thumbnail.lines, thumbnail.hint, tagged, thumbnail.preview)
                }
            }
        }
    }
}

private fun snapLabel(body: BodyView) = when (body) {
    BodyView.SIDE -> Res.string.single_snap_side
    BodyView.TOP -> Res.string.single_snap_top
    BodyView.FRONT -> Res.string.single_snap_front
}
