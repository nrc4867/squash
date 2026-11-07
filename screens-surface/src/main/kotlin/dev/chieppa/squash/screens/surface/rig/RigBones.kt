package dev.chieppa.squash.screens.surface.rig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.rig.Bone
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_bones
import dev.chieppa.squash.uidesign.generated.rig_reach
import dev.chieppa.squash.uidesign.generated.rig_reach_less
import dev.chieppa.squash.uidesign.generated.rig_reach_more
import dev.chieppa.squash.uidesign.generated.rig_stretches
import dev.chieppa.squash.uidesign.generated.rig_wiggle_hint
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.NumberSteppers
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

private val DOT = 8.dp
private val CHEVRON = 14.dp
private val JOINT_INDENT = 24.dp
private val ROW_GAP = 6.dp

/** Test tags for the Bones list. */
object RigBonesTags {
    fun part(id: PartId) = "rig.bones.part.${id.value}"
    fun joint(id: PartId, joint: String) = "rig.bones.joint.${id.value}.$joint"
    fun reach(id: PartId, joint: String) = "rig.bones.reach.${id.value}.$joint"
}

/** A reach as the Bones list shows it, to a tenth. */
fun reachLabel(value: Float): String = "%.1f".format(Locale.ROOT, value)

/**
 * The Bones section: each rigged part with its color, and under it its joints, each with its Reach; a part on the right side
 * starts closed with its joint count. A joint's row picks its bone, which lights it and shows its weight, and wiggles it.
 */
@Composable
fun BonesSection(state: AppState, session: CharacterSession, rig: Rig, names: RigNames) {
    val document by session.document.collectAsState()
    val character = document.character
    val colors = remember(character.parts) { rigPartColors(character) }
    var closed by remember(session) { mutableStateOf(character.parts.filter { it.side == Side.RIGHT }.map { it.id }.toSet()) }
    val byPart = rig.bones.groupBy { it.name.part }
    DrawerSection(stringResource(Res.string.rig_bones)) {
        for (part in rig.parts) {
            val bones = byPart[part.id].orEmpty()
            if (bones.isEmpty()) continue
            val open = part.id !in closed
            val color = colors[part.id] ?: Stretch.colors.inkMuted
            ListRow(false, { closed = if (open) closed + part.id else closed - part.id }, Modifier.testTag(RigBonesTags.part(part.id))) {
                Icon(if (open) StretchIcons.ChevronDown else StretchIcons.ChevronRight, Stretch.colors.inkMuted, size = CHEVRON)
                Dot(color)
                RowText(names.of(part.id), Modifier.weight(1f))
                if (!open) RowText(bones.size.toString(), subtle = true)
            }
            if (open) bones.forEach { JointRow(state, session, character, it, color, names) }
        }
    }
}

@Composable
private fun JointRow(state: AppState, session: CharacterSession, character: Character, bone: Bone, color: Color, names: RigNames) {
    val name = bone.name
    val label = names.of(name)
    val reach = stringResource(Res.string.rig_reach)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        Box(Modifier.weight(1f)) {
            Tooltip(stringResource(Res.string.rig_wiggle_hint, label)) {
                ListRow(state.rig.picked == name, {
                    state.rig.picked = name
                    state.rig.wiggle(name)
                }, Modifier.testTag(RigBonesTags.joint(name.part, name.joint)), indent = JOINT_INDENT) {
                    Dot(color)
                    RowText(name.joint, Modifier.weight(1f))
                    if (bone.stretch) Icon(SquashIcons.Stretch, Stretch.colors.inkMuted, size = CHEVRON, contentDescription = stringResource(Res.string.rig_stretches))
                }
            }
        }
        NumberField(
            reachOf(character, name),
            { value -> reachCommand(session.document.value.character, name, value, state.thisSide)?.let(session::send) },
            reach,
            Modifier.testTag(RigBonesTags.reach(name.part, name.joint)),
            step = RigParams.REACH_STEP,
            range = RigParams.MIN_REACH..RigParams.MAX_REACH,
            format = ::reachLabel,
            width = REACH_WIDTH,
            steppers = NumberSteppers(stringResource(Res.string.rig_reach_less, label), stringResource(Res.string.rig_reach_more, label)),
        )
    }
}

private val REACH_WIDTH = 36.dp

@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(DOT).background(color, CircleShape))
}
