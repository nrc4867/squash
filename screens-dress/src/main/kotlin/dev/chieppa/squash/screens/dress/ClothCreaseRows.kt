package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.ClothCrease
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.MOST_CREASE_STRENGTH
import dev.chieppa.squash.document.SetGarmentCrease
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_creases
import dev.chieppa.squash.uidesign.generated.dress_drape
import dev.chieppa.squash.uidesign.generated.dress_wrinkles
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/**
 * Creases, Wrinkles and Drape: how strongly the garment creases at the joints it covers, wrinkles where a pose bunches it and gathers
 * in short folds above its hems, each 0 to twice its default, times the character's cloth creases.
 */
@Composable
internal fun ClothCreaseRows(session: CharacterSession, garment: Garment) {
    val gesture = remember(session) { SliderGesture(session) }
    CreaseRow(garment, ClothCrease.CREASES, Res.string.dress_creases, garment.creases, DressTags.CREASES, gesture)
    CreaseRow(garment, ClothCrease.WRINKLES, Res.string.dress_wrinkles, garment.wrinkles, DressTags.WRINKLES, gesture)
    CreaseRow(garment, ClothCrease.DRAPE, Res.string.dress_drape, garment.drape, DressTags.DRAPE, gesture)
}

@Composable
private fun CreaseRow(
    garment: Garment,
    which: ClothCrease,
    name: StringResource,
    value: Float,
    tag: String,
    gesture: SliderGesture,
) {
    val label = stringResource(name)
    SettingRow(label) {
        Slider(value, { gesture.change(SetGarmentCrease(garment.id, which, it)) }, label, Modifier.weight(1f).testTag(tag), 0f..MOST_CREASE_STRENGTH, gesture::done)
        Readout(stringResource(Res.string.edit_percent, (value * PERCENT).roundToInt()))
    }
}
