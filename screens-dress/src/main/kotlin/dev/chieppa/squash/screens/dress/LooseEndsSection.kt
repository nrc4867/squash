package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.SetLooseEnd
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_collide_body
import dev.chieppa.squash.uidesign.generated.dress_follow_through
import dev.chieppa.squash.uidesign.generated.dress_loose_bones
import dev.chieppa.squash.uidesign.generated.dress_loose_end
import dev.chieppa.squash.uidesign.generated.dress_loose_end_of
import dev.chieppa.squash.uidesign.generated.dress_loose_end_on
import dev.chieppa.squash.uidesign.generated.dress_spring
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** A section for each of [garment]'s loose ends: the part its branch is on and its bones, Follow-through, Spring and Collide body. */
@Composable
internal fun LooseEndsSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    for (end in garment.looseEnds) {
        DrawerSection(stringResource(Res.string.dress_loose_end_of, garment.name)) {
            LooseEndRows(state, session, character, garment, end)
        }
    }
}

@Composable
private fun LooseEndRows(state: AppState, session: CharacterSession, character: Character, garment: Garment, end: LooseEnd) {
    val part = character.part(end.branch)?.let { dressPartName(state, it) } ?: end.branch.value
    SettingRow(stringResource(Res.string.dress_loose_end)) {
        Spacer(Modifier.weight(1f))
        BasicText(
            stringResource(Res.string.dress_loose_end_on, part, stringResource(Res.string.dress_loose_bones, end.bones)),
            Modifier.testTag(DressTags.looseEnd(end.id)),
            style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)),
            maxLines = 1,
        )
    }
    fun set(changed: LooseEnd) = session.send(SetLooseEnd(garment.id, changed))
    SwitchRow(stringResource(Res.string.dress_follow_through), end.followThrough, Modifier.testTag(DressTags.follow(end.id))) { set(end.copy(followThrough = it)) }
    val label = stringResource(Res.string.dress_spring)
    val gesture = remember(session) { SliderGesture(session) }
    SettingRow(label) {
        Slider(
            end.spring,
            { gesture.change(SetLooseEnd(garment.id, end.copy(spring = it))) },
            label,
            Modifier.weight(1f).testTag(DressTags.spring(end.id)),
            onValueChangeFinished = gesture::done,
        )
        Readout(stringResource(Res.string.edit_percent, (end.spring * PERCENT).roundToInt()))
    }
    SwitchRow(stringResource(Res.string.dress_collide_body), end.collideBody, Modifier.testTag(DressTags.collide(end.id))) { set(end.copy(collideBody = it)) }
}
