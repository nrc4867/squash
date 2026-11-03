package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.Readout
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.draw_close_end
import dev.chieppa.squash.uidesign.generated.draw_pen
import dev.chieppa.squash.uidesign.generated.draw_pen_pressure
import dev.chieppa.squash.uidesign.generated.draw_pen_pressure_not_used
import dev.chieppa.squash.uidesign.generated.draw_pen_width
import dev.chieppa.squash.uidesign.generated.draw_pen_width_shape_only
import dev.chieppa.squash.uidesign.generated.draw_smooth
import dev.chieppa.squash.uidesign.generated.draw_snap_line
import dev.chieppa.squash.uidesign.generated.draw_stroke
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f
private val PERCENT_WIDTH = 36.dp

/** Test tags for the drawer's controls. */
object OutlineDrawerTags {
    const val SMOOTH = "outline.smooth"
    const val SMOOTH_VALUE = "outline.smooth.value"
    const val SNAP_LINE = "outline.snapLine"
    const val CLOSE_END = "outline.closeEnd"
    const val PRESSURE = "outline.pressure"
    const val WIDTH = "outline.width"
}

/** The Outline drawer's sections, with [hint], the board's Hint section, between Stroke and Pen. */
@Composable
fun OutlineSections(state: AppState, hint: @Composable () -> Unit = {}) {
    StrokeSection(state)
    hint()
    PenSection()
}

@Composable
private fun StrokeSection(state: AppState) {
    val curves = state.curves
    val smooth = stringResource(Res.string.draw_smooth)
    val snap = stringResource(Res.string.draw_snap_line)
    val close = stringResource(Res.string.draw_close_end)
    DrawerSection(stringResource(Res.string.draw_stroke)) {
        SettingRow(smooth) {
            Slider(curves.smooth, { curves.smooth = it }, smooth, Modifier.weight(1f).testTag(OutlineDrawerTags.SMOOTH))
            Readout(
                stringResource(Res.string.edit_percent, (curves.smooth * PERCENT).roundToInt()),
                Modifier.width(PERCENT_WIDTH).testTag(OutlineDrawerTags.SMOOTH_VALUE),
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
