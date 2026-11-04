package dev.chieppa.squash.screens.spine

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.HintSection
import dev.chieppa.squash.screens.draw.SmoothRow
import dev.chieppa.squash.screens.draw.SnapGuidesRow
import dev.chieppa.squash.screens.draw.StabilizerRow
import dev.chieppa.squash.screens.edit.Readout
import dev.chieppa.squash.screens.sheet.pixelsLabel
import dev.chieppa.squash.screens.shell.VIEW_ORDER
import dev.chieppa.squash.screens.shell.viewLabel
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.draw_pen
import dev.chieppa.squash.uidesign.generated.draw_pen_pressure
import dev.chieppa.squash.uidesign.generated.draw_pen_width
import dev.chieppa.squash.uidesign.generated.draw_stroke
import dev.chieppa.squash.uidesign.generated.edit_none
import dev.chieppa.squash.uidesign.generated.edit_outlines
import dev.chieppa.squash.uidesign.generated.edit_points
import dev.chieppa.squash.uidesign.generated.spine_pen_pressure
import dev.chieppa.squash.uidesign.generated.spine_pen_width
import dev.chieppa.squash.uidesign.generated.spine_px
import dev.chieppa.squash.uidesign.generated.spine_to_write
import dev.chieppa.squash.uidesign.generated.spine_width
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.NumberField
import org.jetbrains.compose.resources.stringResource

/** The Spine and width drawer's test tags. */
object SpineDrawerTags {
    const val WIDTH = "spine.width"
    const val PRESSURE = "spine.pressure"
    const val PEN_WIDTH = "spine.penWidth"

    fun outline(view: View) = "spine.outline.${view.name}"
}

/** The Spine and width drawer's sections: Stroke, Width, Hint, Pen and the outlines it writes. */
@Composable
fun SpineSections(state: AppState, outlines: Map<View, OutlineCurve>) {
    DrawerSection(stringResource(Res.string.draw_stroke)) {
        SmoothRow(state)
        StabilizerRow(state)
        SnapGuidesRow(state)
    }
    WidthSection(state)
    HintSection(state)
    PenSection()
    OutlinesSection(state, outlines)
}

@Composable
private fun WidthSection(state: AppState) {
    val label = stringResource(Res.string.spine_width)
    val pixels = state.spine.widthPixels
    DrawerSection(label) {
        SettingRow(label) {
            Spacer(Modifier.weight(1f))
            if (pixels != null) {
                NumberField(
                    pixels,
                    state.spine::setWidthPixels,
                    label,
                    Modifier.testTag(SpineDrawerTags.WIDTH),
                    unit = stringResource(Res.string.spine_px),
                    format = ::pixelsLabel,
                )
            } else {
                Readout(stringResource(Res.string.edit_none), Modifier.testTag(SpineDrawerTags.WIDTH))
            }
        }
    }
}

@Composable
private fun PenSection() {
    DrawerSection(stringResource(Res.string.draw_pen)) {
        SettingRow(stringResource(Res.string.draw_pen_pressure)) {
            Spacer(Modifier.weight(1f))
            Readout(stringResource(Res.string.spine_pen_pressure), Modifier.testTag(SpineDrawerTags.PRESSURE))
        }
        SettingRow(stringResource(Res.string.draw_pen_width)) {
            Spacer(Modifier.weight(1f))
            Readout(stringResource(Res.string.spine_pen_width), Modifier.testTag(SpineDrawerTags.PEN_WIDTH))
        }
    }
}

@Composable
private fun OutlinesSection(state: AppState, outlines: Map<View, OutlineCurve>) {
    val active by state.activeView.collectAsState()
    val writing = state.spine.preview != null
    DrawerSection(stringResource(Res.string.edit_outlines), divider = false) {
        VIEW_ORDER.forEach { view ->
            val anchors = outlines[view]?.anchors?.size
            ListRow(view == active, { state.activeView.value = view }, Modifier.testTag(SpineDrawerTags.outline(view))) {
                RowIcon(SquashIcons.EditCurves)
                RowText(viewLabel(view), Modifier.weight(1f))
                RowText(
                    when {
                        writing && view != View.FRONT -> stringResource(Res.string.spine_to_write)
                        anchors != null -> stringResource(Res.string.edit_points, anchors)
                        else -> stringResource(Res.string.edit_none)
                    },
                    subtle = true,
                )
            }
        }
    }
}
