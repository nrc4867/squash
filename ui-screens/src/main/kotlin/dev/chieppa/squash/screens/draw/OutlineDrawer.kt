package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.Readout
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.draw_close_end
import dev.chieppa.squash.uidesign.generated.draw_hold_snap
import dev.chieppa.squash.uidesign.generated.draw_half_outline
import dev.chieppa.squash.uidesign.generated.draw_hint
import dev.chieppa.squash.uidesign.generated.draw_hint_ghost
import dev.chieppa.squash.uidesign.generated.draw_hint_template
import dev.chieppa.squash.uidesign.generated.draw_pen
import dev.chieppa.squash.uidesign.generated.draw_pen_pressure
import dev.chieppa.squash.uidesign.generated.draw_pen_pressure_not_used
import dev.chieppa.squash.uidesign.generated.draw_pen_width
import dev.chieppa.squash.uidesign.generated.draw_pen_width_shape_only
import dev.chieppa.squash.uidesign.generated.draw_smooth
import dev.chieppa.squash.uidesign.generated.draw_snap_line
import dev.chieppa.squash.uidesign.generated.draw_snap_guides
import dev.chieppa.squash.uidesign.generated.draw_stabilizer
import dev.chieppa.squash.uidesign.generated.draw_stroke
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f
private val PERCENT_WIDTH = 36.dp
private val ROW_GAP = 10.dp

/** Test tags for the drawer's controls. */
object OutlineDrawerTags {
    const val SMOOTH = "outline.smooth"
    const val SMOOTH_VALUE = "outline.smooth.value"
    const val STABILIZER = "outline.stabilizer"
    const val STABILIZER_VALUE = "outline.stabilizer.value"
    const val SNAP_LINE = "outline.snapLine"
    const val SNAP_GUIDES = "outline.snapGuides"
    const val HALF_OUTLINE = "outline.halfOutline"
    const val CLOSE_END = "outline.closeEnd"
    const val HOLD_SNAP = "outline.holdSnap"
    const val TEMPLATE = "outline.template"
    const val GHOST = "outline.ghost"
    const val GHOST_VALUE = "outline.ghost.value"
    const val PRESSURE = "outline.pressure"
    const val WIDTH = "outline.width"
}

/** The Outline drawer's sections: Stroke, Hint and Pen. */
@Composable
fun OutlineSections(state: AppState) {
    StrokeSection(state)
    HintSection(state)
    PenSection()
}

/** How strongly the panes hint at what is around the part, which holds whatever the armed tool. */
@Composable
fun HintSection(state: AppState) {
    val curves = state.curves
    val template = stringResource(Res.string.draw_hint_template)
    val ghost = stringResource(Res.string.draw_hint_ghost)
    DrawerSection(stringResource(Res.string.draw_hint)) {
        SettingRow(template) {
            Spacer(Modifier.weight(1f))
            Toggle(curves.templateHint, { curves.templateHint = it }, template, Modifier.testTag(OutlineDrawerTags.TEMPLATE))
        }
        SettingRow(ghost) {
            Slider(curves.ghost, { curves.ghost = it }, ghost, Modifier.weight(1f).testTag(OutlineDrawerTags.GHOST))
            Readout(
                stringResource(Res.string.edit_percent, (curves.ghost * PERCENT).roundToInt()),
                Modifier.width(PERCENT_WIDTH).testTag(OutlineDrawerTags.GHOST_VALUE),
            )
        }
    }
}

/** How a stroke is cleaned up, which holds whichever view is next and however much of the part is drawn. */
@Composable
fun StrokeSection(state: AppState) {
    val curves = state.curves
    val stabilizer = stringResource(Res.string.draw_stabilizer)
    val guides = stringResource(Res.string.draw_snap_guides)
    val half = stringResource(Res.string.draw_half_outline)
    val snap = stringResource(Res.string.draw_snap_line)
    val close = stringResource(Res.string.draw_close_end)
    val hold = stringResource(Res.string.draw_hold_snap)
    DrawerSection(stringResource(Res.string.draw_stroke)) {
        SmoothRow(state)
        SettingRow(stabilizer) {
            Slider(curves.stabilizer, { curves.stabilizer = it }, stabilizer, Modifier.weight(1f).testTag(OutlineDrawerTags.STABILIZER))
            Readout(
                stringResource(Res.string.edit_percent, (curves.stabilizer * PERCENT).roundToInt()),
                Modifier.width(PERCENT_WIDTH).testTag(OutlineDrawerTags.STABILIZER_VALUE),
            )
        }
        SettingRow(snap) {
            Spacer(Modifier.weight(1f))
            Toggle(curves.snapLine, { curves.snapLine = it }, snap, Modifier.testTag(OutlineDrawerTags.SNAP_LINE))
        }
        SettingRow(close) {
            Spacer(Modifier.weight(1f))
            Toggle(curves.closeEnd, { curves.closeEnd = it }, close, Modifier.testTag(OutlineDrawerTags.CLOSE_END))
        }
        LongLabelRow(guides) {
            Toggle(curves.snapGuides, { curves.snapGuides = it }, guides, Modifier.testTag(OutlineDrawerTags.SNAP_GUIDES))
        }
        SettingRow(half) {
            Spacer(Modifier.weight(1f))
            Toggle(curves.halfOutline, { curves.halfOutline = it }, half, Modifier.testTag(OutlineDrawerTags.HALF_OUTLINE))
        }
        LongLabelRow(hold) {
            Toggle(curves.holdSnap, { curves.holdSnap = it }, hold, Modifier.testTag(OutlineDrawerTags.HOLD_SNAP))
        }
    }
}

/** How much a stroke is smoothed, which every stroke tool shares. */
@Composable
internal fun SmoothRow(state: AppState) {
    val curves = state.curves
    val smooth = stringResource(Res.string.draw_smooth)
    SettingRow(smooth) {
        Slider(curves.smooth, { curves.smooth = it }, smooth, Modifier.weight(1f).testTag(OutlineDrawerTags.SMOOTH))
        Readout(
            stringResource(Res.string.edit_percent, (curves.smooth * PERCENT).roundToInt()),
            Modifier.width(PERCENT_WIDTH).testTag(OutlineDrawerTags.SMOOTH_VALUE),
        )
    }
}

/** A setting's row for a [label] longer than a [SettingRow] holds, at the size the board gives it. */
@Composable
private fun LongLabelRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = StretchSizes.ctl),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.inkControl)), maxLines = 1)
        Spacer(Modifier.weight(1f))
        control()
    }
}

@Composable
private fun PenSection() {
    DrawerSection(stringResource(Res.string.draw_pen)) {
        SettingRow(stringResource(Res.string.draw_pen_pressure)) {
            Spacer(Modifier.weight(1f))
            Readout(stringResource(Res.string.draw_pen_pressure_not_used), Modifier.testTag(OutlineDrawerTags.PRESSURE))
        }
        SettingRow(stringResource(Res.string.draw_pen_width)) {
            Spacer(Modifier.weight(1f))
            Readout(stringResource(Res.string.draw_pen_width_shape_only), Modifier.testTag(OutlineDrawerTags.WIDTH))
        }
    }
}
