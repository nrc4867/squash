package dev.chieppa.squash.screens.edit

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.smooth_brush
import dev.chieppa.squash.uidesign.generated.smooth_size
import dev.chieppa.squash.uidesign.generated.smooth_size_value
import dev.chieppa.squash.uidesign.generated.smooth_strength
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f
private val READOUT_WIDTH = 44.dp

/** Test tags for the drawer's controls. */
object SmoothDrawerTags {
    const val SIZE = "smooth.size"
    const val SIZE_VALUE = "smooth.size.value"
    const val STRENGTH = "smooth.strength"
    const val STRENGTH_VALUE = "smooth.strength.value"
}

/** The Smooth drawer: the brush's Size and Strength. */
@Composable
fun SmoothSections(state: AppState) {
    val curves = state.curves
    val size = stringResource(Res.string.smooth_size)
    val strength = stringResource(Res.string.smooth_strength)
    DrawerSection(stringResource(Res.string.smooth_brush)) {
        SettingRow(size) {
            Slider(curves.brushSize, { curves.brushSize = it }, size, Modifier.weight(1f).testTag(SmoothDrawerTags.SIZE), MIN_BRUSH_SIZE..MAX_BRUSH_SIZE)
            Readout(
                stringResource(Res.string.smooth_size_value, curves.brushSize.roundToInt()),
                Modifier.width(READOUT_WIDTH).testTag(SmoothDrawerTags.SIZE_VALUE),
            )
        }
        SettingRow(strength) {
            Slider(curves.brushStrength, { curves.brushStrength = it }, strength, Modifier.weight(1f).testTag(SmoothDrawerTags.STRENGTH))
            Readout(
                stringResource(Res.string.edit_percent, (curves.brushStrength * PERCENT).roundToInt()),
                Modifier.width(READOUT_WIDTH).testTag(SmoothDrawerTags.STRENGTH_VALUE),
            )
        }
    }
}
