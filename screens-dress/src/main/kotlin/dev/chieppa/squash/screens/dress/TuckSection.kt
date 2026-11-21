package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.shownTucks
import dev.chieppa.squash.character.dress.tuckCommand
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.TuckKind
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_shaped
import dev.chieppa.squash.uidesign.generated.dress_tuck
import dev.chieppa.squash.uidesign.generated.dress_tuck_claws
import dev.chieppa.squash.uidesign.generated.dress_tuck_ears
import dev.chieppa.squash.uidesign.generated.dress_tuck_fingers
import dev.chieppa.squash.uidesign.generated.dress_tuck_fins
import dev.chieppa.squash.uidesign.generated.dress_tuck_horns
import dev.chieppa.squash.uidesign.generated.dress_tuck_spikes
import dev.chieppa.squash.uidesign.generated.dress_tuck_tail
import dev.chieppa.squash.uidesign.generated.dress_tuck_toes
import dev.chieppa.squash.uidesign.generated.dress_tucked
import dev.chieppa.squash.uidesign.generated.shell_tree_pair
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val PAIR = 2

/**
 * Tuck: a row for each small part, or part's digits, inside the garment as drawn and in the selection (see [shownTucks]), Tucked or
 * Shaped; one shaped that pokes through the cloth in lumps is washed in the warn colour. Fingers tuck into a mitten on a hand that can fuse
 * them and are only shaped on any other. Absent while it holds none.
 */
@Composable
internal fun TuckSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val body by remember(session) { session.assembly.output }.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val held = body ?: return
    val items = remember(character, held, garment, selected) { shownTucks(character, held, garment, selected) }
    if (items.isEmpty()) return
    val tucked = stringResource(Res.string.dress_tucked)
    val shaped = stringResource(Res.string.dress_shaped)
    DrawerSection(stringResource(Res.string.dress_tuck)) {
        for (item in items) {
            val paired = character.part(item.part)?.pair != null
            val label = stringResource(kindName(item.kind)).let { if (paired) "$it ${stringResource(Res.string.shell_tree_pair, PAIR)}" else it }
            val wash = if (item.pokes) Modifier.background(Stretch.colors.warnWash, Stretch.shapes.ctlShape) else Modifier
            Column(Modifier.fillMaxWidth().then(wash).testTag(DressTags.tuck(item.part, item.kind))) {
                SettingRow(label) {
                    Segmented(
                        listOf(true, false),
                        item.tucked,
                        { if (it != item.tucked) session.send(tuckCommand(character, held, garment, item, it)) },
                        { if (it) tucked else shaped },
                        Modifier.weight(1f).testTag(DressTags.tuckChoice(item.part, item.kind)),
                        fill = true,
                        compact = true,
                        enabled = { !it || item.choosable },
                    )
                }
            }
        }
    }
}

private fun kindName(kind: TuckKind): StringResource = when (kind) {
    TuckKind.TOES -> Res.string.dress_tuck_toes
    TuckKind.CLAWS -> Res.string.dress_tuck_claws
    TuckKind.FINGERS -> Res.string.dress_tuck_fingers
    TuckKind.EARS -> Res.string.dress_tuck_ears
    TuckKind.TAIL -> Res.string.dress_tuck_tail
    TuckKind.HORNS -> Res.string.dress_tuck_horns
    TuckKind.SPIKES -> Res.string.dress_tuck_spikes
    TuckKind.FINS -> Res.string.dress_tuck_fins
}
