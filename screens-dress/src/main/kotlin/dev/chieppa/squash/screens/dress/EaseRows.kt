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
import dev.chieppa.squash.document.EaseKey
import dev.chieppa.squash.document.EaseProfile
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetPartEase
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_ease_add_key
import dev.chieppa.squash.uidesign.generated.dress_ease_back
import dev.chieppa.squash.uidesign.generated.dress_ease_key
import dev.chieppa.squash.uidesign.generated.dress_ease_of
import dev.chieppa.squash.uidesign.generated.dress_ease_part
import dev.chieppa.squash.uidesign.generated.dress_ease_remove
import dev.chieppa.squash.uidesign.generated.dress_ease_remove_key
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The loosest ease a key's sliders reach, in body units. */
private const val MOST_EASE = 0.1f

/** Where along a part a new profile's keys sit, in u. */
private val NEW_KEYS = listOf(0f, 0.5f, 1f)

private const val PERCENT = 100f

/** Thousandths of a body unit, as an ease reads out. */
private const val THOUSANDTHS = 1000f

/**
 * Ease along each part the garment shapes its stand-off over: the part's name with its cross, then a row a key, its place along the part,
 * its front ease and, where the back is set apart, its back ease, with a toggle setting the back apart and a cross; Add key between the
 * two keys furthest apart; and Ease a part, which starts a profile on a covered part at its fit.
 */
@Composable
internal fun EaseRows(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val gesture = remember(session) { SliderGesture(session) }
    for ((part, profile) in garment.eases) {
        val name = character.part(part)?.let { dressPartName(state, it) } ?: part.value
        SettingRow(stringResource(Res.string.dress_ease_of, name)) {
            IconButton(SquashIcons.AddKey, stringResource(Res.string.dress_ease_add_key, name), { session.send(SetPartEase(garment.id, part, withKeyAdded(profile))) }, Modifier.testTag(DressTags.addEaseKey(part)))
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_ease_remove, name), { session.send(SetPartEase(garment.id, part, null)) }, Modifier.testTag(DressTags.removeEase(part)))
        }
        profile.keys.forEachIndexed { i, key ->
            val label = stringResource(Res.string.dress_ease_key, (key.u * PERCENT).roundToInt())
            SettingRow(label) {
                Slider(key.ease, { gesture.change(SetPartEase(garment.id, part, profile.withKey(i, key.copy(ease = it)))) }, label, Modifier.weight(1f).testTag(DressTags.easeKey(part, i)), 0f..MOST_EASE, gesture::done)
                key.back?.let { back ->
                    Slider(back, { gesture.change(SetPartEase(garment.id, part, profile.withKey(i, key.copy(back = it)))) }, label, Modifier.weight(1f).testTag(DressTags.easeBack(part, i)), 0f..MOST_EASE, gesture::done)
                }
                Readout((key.ease * THOUSANDTHS).roundToInt().toString())
                IconButton(
                    SquashIcons.EaseBack, stringResource(Res.string.dress_ease_back),
                    { session.send(SetPartEase(garment.id, part, profile.withKey(i, key.copy(back = if (key.back == null) key.ease else null)))) },
                    Modifier.testTag(DressTags.easeBackToggle(part, i)), on = key.back != null,
                )
                if (profile.keys.size > 1) {
                    IconButton(StretchIcons.Close, stringResource(Res.string.dress_ease_remove_key), { session.send(SetPartEase(garment.id, part, EaseProfile(profile.keys - key))) }, Modifier.testTag(DressTags.removeEaseKey(part, i)))
                }
            }
        }
    }
    val open = garment.branches.map { it.part }.filter { it !in garment.eases }
    if (open.isNotEmpty()) EaseAPart(state, session, character, garment, open)
}

@Composable
private fun EaseAPart(state: AppState, session: CharacterSession, character: Character, garment: Garment, parts: List<PartId>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(stringResource(Res.string.dress_ease_part), { open = true }, Modifier.testTag(DressTags.EASE_PART), kind = ButtonKind.Quiet, icon = StretchIcons.Plus)
        Menu(open, { open = false }) {
            for (part in parts) {
                val name = character.part(part)?.let { dressPartName(state, it) } ?: part.value
                MenuItem(name, {
                    open = false
                    session.send(SetPartEase(garment.id, part, startingProfile(garment, part)))
                })
            }
        }
    }
}

/** The profile Ease a part starts [part] on: keys along it at the stand-off [garment] has there now. */
internal fun startingProfile(garment: Garment, part: PartId): EaseProfile {
    val ease = garment.branch(part)?.let(garment::easeOf) ?: garment.fit ?: 0f
    return EaseProfile(NEW_KEYS.map { EaseKey(it, ease) })
}

/** This profile with a key added halfway between the two keys furthest apart, at the ease the profile has there; past the last key with one key. */
internal fun withKeyAdded(profile: EaseProfile): EaseProfile {
    val keys = profile.keys
    if (keys.size == 1) {
        val only = keys.single()
        val u = if (only.u < 1f) 1f else 0f
        return EaseProfile((keys + EaseKey(u, only.ease, only.back)).sortedBy { it.u })
    }
    val gap = keys.zipWithNext().maxBy { (a, b) -> b.u - a.u }
    val u = (gap.first.u + gap.second.u) / 2f
    val back = if (gap.first.back != null || gap.second.back != null) profile.at(u, 1f) else null
    return EaseProfile((keys + EaseKey(u, profile.at(u, 0f), back)).sortedBy { it.u })
}

private fun EaseProfile.withKey(i: Int, key: EaseKey): EaseProfile = EaseProfile(keys.mapIndexed { j, held -> if (j == i) key else held })
