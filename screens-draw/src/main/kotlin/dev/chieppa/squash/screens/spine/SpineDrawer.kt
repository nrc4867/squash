package dev.chieppa.squash.screens.spine

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.OutlineRowsSection
import dev.chieppa.squash.screens.draw.HintSection
import dev.chieppa.squash.screens.draw.SmoothRow
import dev.chieppa.squash.screens.draw.SnapGuidesRow
import dev.chieppa.squash.screens.draw.StabilizerRow
import dev.chieppa.squash.screens.edit.Readout
import dev.chieppa.squash.screens.sheet.pixelsLabel
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.draw_stroke
import dev.chieppa.squash.uidesign.generated.edit_none
import dev.chieppa.squash.uidesign.generated.spine_px
import dev.chieppa.squash.uidesign.generated.spine_to_write
import dev.chieppa.squash.uidesign.generated.spine_width
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.NumberField
import org.jetbrains.compose.resources.stringResource

/** The Spine and width drawer's test tags. */
object SpineDrawerTags {
    const val WIDTH = "spine.width"

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
    val writing = state.spine.preview != null
    val toWrite = stringResource(Res.string.spine_to_write)
    OutlineRowsSection(state, outlines, SpineDrawerTags::outline, divider = false) { view -> toWrite.takeIf { writing && view != View.FRONT } }
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

