package dev.chieppa.squash.screens.single

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.shell.VIEW_ORDER
import dev.chieppa.squash.screens.shell.partName
import dev.chieppa.squash.screens.shell.viewLabel
import dev.chieppa.squash.uidesign.THUMB_HEIGHT
import dev.chieppa.squash.uidesign.THUMB_WIDTH
import dev.chieppa.squash.uidesign.ViewThumb
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.single_hud
import dev.chieppa.squash.uidesign.generated.single_hud_sided
import dev.chieppa.squash.uidesign.generated.single_side_left
import dev.chieppa.squash.uidesign.generated.single_side_right
import dev.chieppa.squash.uidesign.generated.single_snap_front
import dev.chieppa.squash.uidesign.generated.single_snap_side
import dev.chieppa.squash.uidesign.generated.single_snap_top
import dev.chieppa.squash.uidesign.generated.single_view_free
import dev.chieppa.squash.uidesign.generated.single_view_front
import dev.chieppa.squash.uidesign.generated.single_view_side
import dev.chieppa.squash.uidesign.generated.single_view_top
import dev.chieppa.stretch.frame.Hud
import dev.chieppa.stretch.frame.Toolbar
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val THUMB_GAP = 10.dp
private val THUMBS_INSET = 6.dp

/** Snap view: which view the camera is on, none while free; picking one turns the camera there. */
@Composable
internal fun SnapViewControl(snapped: View?, onSnap: (View) -> Unit) {
    val labels = VIEW_ORDER.associateWith { viewLabel(it) }
    Segmented<View?>(VIEW_ORDER, snapped, { it?.let(onSnap) }, { labels[it].orEmpty() })
}

/** The part, its side and the view the camera is on, as the board's HUD reads them. */
@Composable
internal fun SingleHud(state: AppState, snapped: View?) {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val document = session?.document?.collectAsState()?.value ?: return
    val part = selected?.let(document.character::part) ?: return
    val name = partName(part, state.templates)
    val view = stringResource(
        when (snapped) {
            null -> Res.string.single_view_free
            View.SIDE -> Res.string.single_view_side
            View.TOP -> Res.string.single_view_top
            View.FRONT -> Res.string.single_view_front
        },
    )
    val text = when (part.side) {
        null -> stringResource(Res.string.single_hud, name, view)
        Side.LEFT -> stringResource(Res.string.single_hud_sided, name, stringResource(Res.string.single_side_left), view)
        Side.RIGHT -> stringResource(Res.string.single_hud_sided, name, stringResource(Res.string.single_side_right), view)
    }
    Hud(text, Modifier.testTag(SingleTags.HUD))
}

/** A thumbnail per view; one not on the view on screen turns the camera to its own. */
@Composable
internal fun ThumbnailStrip(selection: SheetSelection, current: View?, live: LiveStroke?, templateHint: Boolean, onSnap: (View) -> Unit) {
    val density = LocalDensity.current
    val width = with(density) { THUMB_WIDTH.roundToPx() }
    val height = with(density) { THUMB_HEIGHT.roundToPx() }
    Toolbar(Modifier.testTag(SingleTags.THUMBNAILS)) {
        Row(Modifier.padding(THUMBS_INSET), horizontalArrangement = Arrangement.spacedBy(THUMB_GAP)) {
            for (thumbnail in viewThumbnails(selection, current, live, templateHint, width, height)) {
                val tagged = Modifier.testTag(SingleTags.thumbnail(thumbnail.view))
                val label = viewLabel(thumbnail.view)
                if (thumbnail.snaps) {
                    val snapTo = stringResource(snapLabel(thumbnail.view))
                    Tooltip(snapTo) {
                        ViewThumb(label, thumbnail.edge, thumbnail.lines, thumbnail.hint, tagged.clickable(onClickLabel = snapTo) { onSnap(thumbnail.view) })
                    }
                } else {
                    ViewThumb(label, thumbnail.edge, thumbnail.lines, thumbnail.hint, tagged)
                }
            }
        }
    }
}

private fun snapLabel(view: View) = when (view) {
    View.SIDE -> Res.string.single_snap_side
    View.TOP -> Res.string.single_snap_top
    View.FRONT -> Res.string.single_snap_front
}
