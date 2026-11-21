package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartBind
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetPartBind
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_bind
import dev.chieppa.squash.uidesign.generated.dress_bind_auto
import dev.chieppa.squash.uidesign.generated.dress_bind_follow
import dev.chieppa.squash.uidesign.generated.dress_bind_single
import dev.chieppa.squash.uidesign.generated.dress_owned_part
import dev.chieppa.squash.uidesign.generated.dress_stiff
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** The binding choices in the order the Bind row shows them: the rule's, then each binding by hand. */
private val BINDS: List<PartBind?> = listOf(null, PartBind.SINGLE, PartBind.FOLLOW)

/**
 * A section for each part [garment] owns, a pair once: Bind, which bones it moves with (Auto for the rule's choice, Single, Follow), and
 * Stiff, how rigid a part that follows the body keeps each stretch, one undo step a drag.
 */
@Composable
internal fun OwnedPartsSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    for (part in character.ownedBy(garment.id).filter { it.pair == null || it.side == Side.LEFT }) {
        DrawerSection(stringResource(Res.string.dress_owned_part, dressPartName(state, part))) {
            OwnedPartRows(session, part)
        }
    }
}

@Composable
private fun OwnedPartRows(session: CharacterSession, part: PartInstance) {
    val auto = stringResource(Res.string.dress_bind_auto)
    val single = stringResource(Res.string.dress_bind_single)
    val follow = stringResource(Res.string.dress_bind_follow)
    SettingRow(stringResource(Res.string.dress_bind)) {
        Segmented(
            BINDS,
            part.bind,
            { if (it != part.bind) session.send(SetPartBind(part.id, it, part.stiff)) },
            {
                when (it) {
                    null -> auto
                    PartBind.SINGLE -> single
                    PartBind.FOLLOW -> follow
                }
            },
            Modifier.weight(1f).testTag(DressTags.bind(part.id)),
            fill = true,
            compact = true,
        )
    }
    val label = stringResource(Res.string.dress_stiff)
    val gesture = remember(session) { SliderGesture(session) }
    val stiff = part.stiff ?: 0f
    SettingRow(label) {
        Slider(
            stiff,
            { gesture.change(SetPartBind(part.id, part.bind, it)) },
            label,
            Modifier.weight(1f).testTag(DressTags.stiff(part.id)),
            onValueChangeFinished = gesture::done,
        )
        Readout(stringResource(Res.string.edit_percent, (stiff * PERCENT).roundToInt()))
    }
}
