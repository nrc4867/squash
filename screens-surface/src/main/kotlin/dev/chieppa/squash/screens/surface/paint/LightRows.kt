package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.document.MOST_FAR_SIDE_DEPTH
import dev.chieppa.squash.document.SetCharacterShading
import dev.chieppa.squash.render.LightFrame
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.screens.sheet.chooseFarSideDarker
import dev.chieppa.squash.screens.sheet.chooseViewLight
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_far_amount
import dev.chieppa.squash.uidesign.generated.color_far_parts
import dev.chieppa.squash.uidesign.generated.color_view_light
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/**
 * Far parts darker: how much darker the side of a pair away from the camera is, 0 to [MOST_FAR_SIDE_DEPTH] of its color, one undo step a
 * drag, and whether it darkens at all; and Light from the view: whether the light turns with the view or stays fixed to the body.
 */
@Composable
internal fun LightRows(state: AppState, shading: ObjectShading) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by remember(open) { open.document }.collectAsState()
    val stored = document.character.shading
    val gesture = remember(open) { SliderGesture(open) }
    val far = stringResource(Res.string.color_far_parts)
    SettingRow(far) {
        Slider(
            stored.farSide,
            { gesture.change(SetCharacterShading(stored.copy(farSide = it.coerceIn(0f, MOST_FAR_SIDE_DEPTH)))) },
            far,
            Modifier.weight(1f).testTag(PaintTags.FAR_DEPTH),
            0f..MOST_FAR_SIDE_DEPTH,
            gesture::done,
        )
        Readout(stringResource(Res.string.color_far_amount, (stored.farSide * PERCENT).roundToInt()))
        Toggle(shading.farSide.enabled, state::chooseFarSideDarker, far, Modifier.testTag(PaintTags.FAR_PARTS))
    }
    val view = stringResource(Res.string.color_view_light)
    SettingRow(view) {
        Spacer(Modifier.weight(1f))
        Toggle(shading.light == LightFrame.VIEW, state::chooseViewLight, view, Modifier.testTag(PaintTags.VIEW_LIGHT))
    }
}
