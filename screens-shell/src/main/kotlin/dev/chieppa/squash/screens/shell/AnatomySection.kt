package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.deleteJoint
import dev.chieppa.squash.screens.deleteLandmark
import dev.chieppa.squash.screens.pairCandidates
import dev.chieppa.squash.screens.pairWith
import dev.chieppa.squash.screens.pickJoint
import dev.chieppa.squash.screens.unpair
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.anatomy_delete
import dev.chieppa.squash.uidesign.generated.anatomy_joint
import dev.chieppa.squash.uidesign.generated.anatomy_joints
import dev.chieppa.squash.uidesign.generated.anatomy_landmark
import dev.chieppa.squash.uidesign.generated.anatomy_landmarks
import dev.chieppa.squash.uidesign.generated.anatomy_pair
import dev.chieppa.squash.uidesign.generated.anatomy_save
import dev.chieppa.squash.uidesign.generated.anatomy_title
import dev.chieppa.squash.uidesign.generated.anatomy_unpair
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuDivider
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

private const val PLACES = 100f
private const val FIGURE = "%.2f"
private const val PLACE_JOIN = " � "

/** A place along or round a part as the board writes it, 0.35. */
private fun place(value: Float): String = FIGURE.format(kotlin.math.round(value * PLACES) / PLACES)

/**
 * The selected part's anatomy: its joints, the template's and those placed by hand, by name and u, and its landmarks by name, u and v,
 * each placed by hand with a small delete, picking a joint's row picking the joint; then Pair with… and Save as template.
 */
@Composable
internal fun AnatomySection(state: AppState, session: CharacterSession, part: PartInstance) {
    session.document.collectAsState().value
    val picked by state.move.pickedJoint.collectAsState()
    val template = session.builtTemplate(part)
    val joints = template.blocks.flatMap { it.joints() }.sortedBy { it.u }
    val landmarks = template.blocks.flatMap { it.landmarks() }
    var saving by remember(part.id) { mutableStateOf(false) }
    DrawerSection(stringResource(Res.string.anatomy_title)) {
        Count(stringResource(Res.string.anatomy_joints), joints.size, ShellTags.ANATOMY_JOINTS)
        for (joint in joints) {
            val id = HandJoints.idOf(joint.name)
            val name = id?.let { stringResource(Res.string.anatomy_joint, it) } ?: displayName(joint.name)
            AnatomyRow(joint.name, SquashIcons.Joint, name, place(joint.u), picked?.part == part.id && picked?.joint == joint.name, id?.let { { state.deleteJoint(part.id, it) } }) {
                state.pickJoint(part.id, joint.name)
            }
        }
        Count(stringResource(Res.string.anatomy_landmarks), landmarks.size, ShellTags.ANATOMY_LANDMARKS)
        for (landmark in landmarks) {
            val id = HandLandmarks.idOf(landmark.name)
            val name = id?.let { stringResource(Res.string.anatomy_landmark, it) } ?: displayName(landmark.name)
            AnatomyRow(landmark.name, SquashIcons.Landmark, name, place(landmark.u) + " · " + place(landmark.v), false, id?.let { { state.deleteLandmark(part.id, it) } }) {}
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PairWith(state, part)
            Button(stringResource(Res.string.anatomy_save), { saving = true }, Modifier.testTag(ShellTags.SAVE_TEMPLATE), kind = ButtonKind.Quiet, icon = SquashIcons.SaveTemplate)
        }
    }
    if (saving) SaveTemplateDialog(state, part.id) { saving = false }
}

@Composable
private fun Count(label: String, count: Int, tag: String) {
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        RowText(formatNumber(count.toFloat()), Modifier.testTag(tag), subtle = true)
    }
}

/** A joint's or a landmark's row: its name and place, and a small delete when [delete] is given. */
@Composable
private fun AnatomyRow(key: String, icon: ImageVector, name: String, at: String, picked: Boolean, delete: (() -> Unit)?, onClick: () -> Unit) {
    ListRow(picked, onClick, Modifier.testTag(ShellTags.anatomyRow(key))) {
        RowIcon(icon)
        RowText(name, Modifier.weight(1f))
        RowText(at, subtle = true)
        if (delete != null) {
            IconButton(SquashIcons.DeleteSocket, stringResource(Res.string.anatomy_delete, name), delete, Modifier.testTag(ShellTags.anatomyDelete(key)))
        }
    }
}

/**
 * Pair with…: a menu of the parts [part] can pair with, choosing one making the two a pair in one step, and Unpair as its last item
 * while [part] is paired. Faint while it offers nothing.
 */
@Composable
private fun PairWith(state: AppState, part: PartInstance) {
    var open by remember(part.id) { mutableStateOf(false) }
    val candidates = state.pairCandidates(part.id)
    val paired = part.pair != null
    val offers = paired || candidates.isNotEmpty()
    Box {
        Button(
            stringResource(Res.string.anatomy_pair),
            { if (offers) open = true },
            Modifier.enabledOrFaint(offers).testTag(ShellTags.PAIR_WITH),
            kind = ButtonKind.Quiet,
            icon = SquashIcons.PairWith,
        )
        Menu(open, { open = false }) {
            for (other in candidates) {
                Box(Modifier.testTag(ShellTags.pairOption(other.id.value))) {
                    MenuItem(partName(other, state.templates), {
                        open = false
                        state.pairWith(part.id, other.id)
                    })
                }
            }
            if (paired) {
                if (candidates.isNotEmpty()) MenuDivider()
                MenuItem(stringResource(Res.string.anatomy_unpair), {
                    open = false
                    state.unpair(part.id)
                })
            }
        }
    }
}
