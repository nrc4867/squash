package dev.chieppa.squash.screens.surface.paint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.MOST_CREASE_STRENGTH
import dev.chieppa.squash.document.SetCharacterCreases
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_cloth_creases
import dev.chieppa.squash.uidesign.generated.color_skin_creases
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/**
 * Skin creases: how strongly the body creases at its bending joints; and Cloth creases: how strongly every garment creases, wrinkles and
 * hangs in folds, times its own strengths in Dress. Each 0, off, to twice its default, one undo step a drag.
 */
@Composable
internal fun CreaseRows(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by remember(open) { open.document }.collectAsState()
    val creases = document.character.creases
    val gesture = remember(open) { SliderGesture(open) }
    CreaseRow(stringResource(Res.string.color_skin_creases), creases.skin, PaintTags.SKIN_CREASES, gesture) { creases.copy(skin = it) }
    CreaseRow(stringResource(Res.string.color_cloth_creases), creases.cloth, PaintTags.CLOTH_CREASES, gesture) { creases.copy(cloth = it) }
}

@Composable
private fun CreaseRow(label: String, value: Float, tag: String, gesture: SliderGesture, changed: (Float) -> CharacterCreases) {
    SettingRow(label) {
        Slider(value, { gesture.change(SetCharacterCreases(changed(it.coerceIn(0f, MOST_CREASE_STRENGTH)))) }, label, Modifier.weight(1f).testTag(tag), 0f..MOST_CREASE_STRENGTH, gesture::done)
        Readout(stringResource(Res.string.edit_percent, (value * PERCENT).roundToInt()))
    }
}
