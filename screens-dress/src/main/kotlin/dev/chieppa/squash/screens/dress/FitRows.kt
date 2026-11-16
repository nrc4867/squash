package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetGarmentFit
import dev.chieppa.squash.document.SetPartFit
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_fit
import dev.chieppa.squash.uidesign.generated.dress_fit_loose
import dev.chieppa.squash.uidesign.generated.dress_fit_of
import dev.chieppa.squash.uidesign.generated.dress_fit_part
import dev.chieppa.squash.uidesign.generated.dress_fit_remove
import dev.chieppa.squash.uidesign.generated.dress_fit_snug
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource

/** The loosest fit the sliders reach, in body units. */
private const val MOST_FIT = 0.2f

/** The fit up to which a garment reads as snug, in body units; looser reads as loose. */
private const val SNUG_UP_TO = 0.03f

/** How far [garment] stands off the body as its Fit row shows it: its fit, else what most of its branches stand off by. */
fun shownFit(garment: Garment): Float =
    garment.fit ?: garment.branches.groupingBy { it.ease }.eachCount().maxByOrNull { it.value }?.key ?: DEFAULT_EASE

/**
 * Fit: how far the garment stands off the body, then a row for each part or pair fitted apart from it, with its cross, and Fit a part,
 * which lists the parts the garment covers that are not fitted apart yet.
 */
@Composable
internal fun FitRows(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val gesture = remember(session) { SliderGesture(session) }
    val fit = shownFit(garment)
    FitRow(stringResource(Res.string.dress_fit), fit, DressTags.FIT, { gesture.change(SetGarmentFit(garment.id, it)) }, gesture::done)
    for ((part, partFit) in garment.partFits) {
        val name = character.part(part)?.let { dressPartName(state, it) } ?: part.value
        FitRow(stringResource(Res.string.dress_fit_of, name), partFit, DressTags.partFit(part), { gesture.change(SetPartFit(garment.id, part, it)) }, gesture::done) {
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_fit_remove, name), { session.send(SetPartFit(garment.id, part, null)) }, Modifier.testTag(DressTags.removeFit(part)))
        }
    }
    val open = garment.branches.map { it.part }.filter { it !in garment.partFits }
    if (open.isNotEmpty()) FitAPart(state, session, character, garment, open, fit)
}

@Composable
private fun FitRow(label: String, value: Float, tag: String, onChange: (Float) -> Unit, onDone: () -> Unit, end: @Composable () -> Unit = {}) {
    SettingRow(label) {
        Slider(value, onChange, label, Modifier.weight(1f).testTag(tag), 0f..MOST_FIT, onDone)
        Readout(stringResource(if (value <= SNUG_UP_TO) Res.string.dress_fit_snug else Res.string.dress_fit_loose))
        end()
    }
}

@Composable
private fun FitAPart(state: AppState, session: CharacterSession, character: Character, garment: Garment, parts: List<PartId>, fit: Float) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(stringResource(Res.string.dress_fit_part), { open = true }, Modifier.testTag(DressTags.FIT_PART), kind = ButtonKind.Quiet, icon = StretchIcons.Plus)
        Menu(open, { open = false }) {
            for (part in parts) {
                val name = character.part(part)?.let { dressPartName(state, it) } ?: part.value
                MenuItem(name, {
                    open = false
                    session.send(SetPartFit(garment.id, part, fit))
                })
            }
        }
    }
}
